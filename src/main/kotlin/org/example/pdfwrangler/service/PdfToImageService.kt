package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.PdfOperationException
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service for converting PDFs to various image formats.
 * Supports multiple output formats, color modes, and DPI settings.
 */
@Service
class PdfToImageService(
    private val colorModeProcessor: ColorModeProcessor,
    private val configurableDpiService: ConfigurableDpiService,
    private val tempFileManagerService: TempFileManagerService,
    private val operationContextLogger: OperationContextLogger
) {

    private val logger = LoggerFactory.getLogger(PdfToImageService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<Resource>>>()
    private val operationProgress = ConcurrentHashMap<String, ConversionProgressResponse>()

    /**
     * Convert PDF to images synchronously.
     */
    fun convertPdfToImages(request: PdfToImageRequest): ResponseEntity<Resource> {
        logger.info("Converting PDF to ${request.outputFormat} images with DPI ${request.dpi}")
        val startTime = System.currentTimeMillis()
        val operationId = operationContextLogger.logOperationStart("PDF_TO_IMAGE", 1)
        
        return try {
            validateRequest(request)
            
            // Create temporary file for input PDF
            val tempPdfFile = tempFileManagerService.createTempFile("input", ".pdf", 60)
            request.file.transferTo(tempPdfFile.toFile())
            
            // Convert PDF pages to images
            val imageFiles = convertPdfPagesToImages(
                tempPdfFile, 
                request.outputFormat,
                request.colorMode,
                request.dpi,
                request.quality,
                request.pageRanges
            )
            
            // Create ZIP archive with all images
            val zipFile = createImageZipArchive(imageFiles, request.outputFileName)
            val resource = ByteArrayResource(Files.readAllBytes(zipFile))
            
            // Cleanup temporary files
            cleanupTempFiles(listOf(tempPdfFile) + imageFiles + listOf(zipFile))
            
            val endTime = System.currentTimeMillis()
            operationContextLogger.logOperationSuccess("PDF_TO_IMAGE", endTime - startTime, operationId)
            
            ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"${request.outputFileName ?: "converted_images"}.zip\"")
                .body(resource)
                
        } catch (ex: Exception) {
            logger.error("Error converting PDF to images: ${ex.message}", ex)
            operationContextLogger.logOperationError(ex, operationId)
            throw PdfOperationException("PDF to image conversion failed: ${ex.message}")
        }
    }

    /**
     * Get conversion status and metadata.
     */
    fun getConversionStatus(request: PdfToImageRequest): PdfToImageResponse {
        logger.info("Getting conversion status for PDF file")
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // Analyze PDF without converting
            val tempPdfFile = tempFileManagerService.createTempFile("analysis", ".pdf", 60)
            request.file.transferTo(tempPdfFile.toFile())
            
            val pageCount = analyzePdfPageCount(tempPdfFile)
            val estimatedSize = estimateOutputSize(pageCount, request.outputFormat, request.dpi)
            
            tempFileManagerService.removeTempFile(tempPdfFile)
            
            PdfToImageResponse(
                success = true,
                message = "PDF analysis completed successfully",
                outputFiles = (1..pageCount).map { "page_$it.${request.outputFormat.lowercase()}" },
                totalPages = pageCount,
                convertedPages = 0,
                processingTimeMs = System.currentTimeMillis() - startTime,
                totalFileSize = estimatedSize
            )
            
        } catch (ex: Exception) {
            logger.error("Error getting conversion status: ${ex.message}", ex)
            PdfToImageResponse(
                success = false,
                message = ex.message ?: "Status analysis failed",
                outputFiles = emptyList(),
                totalPages = 0,
                convertedPages = 0,
                processingTimeMs = 0,
                totalFileSize = 0
            )
        }
    }

    /**
     * Start asynchronous PDF to image conversion.
     */
    fun startAsyncConversion(request: PdfToImageRequest): String {
        val operationId = UUID.randomUUID().toString()
        logger.info("Starting async conversion with operation ID: $operationId")
        
        val future = CompletableFuture.supplyAsync {
            updateProgress(operationId, "PROCESSING", 0, "Starting conversion", null, 0, 1)
            
            try {
                val result = convertPdfToImages(request)
                updateProgress(operationId, "COMPLETED", 100, "Conversion completed", 0, 1, 1)
                result
            } catch (ex: Exception) {
                updateProgress(operationId, "FAILED", 0, "Conversion failed: ${ex.message}", null, 0, 1)
                throw ex
            }
        }
        
        asyncOperations[operationId] = future
        updateProgress(operationId, "STARTED", 0, "Conversion queued", null, 0, 1)
        
        return operationId
    }

    /**
     * Get progress of asynchronous conversion.
     */
    fun getConversionProgress(operationId: String): ConversionProgressResponse {
        return operationProgress[operationId] 
            ?: throw PdfOperationException("Operation not found: $operationId")
    }

    /**
     * Download result of asynchronous conversion.
     */
    fun downloadAsyncResult(operationId: String): ResponseEntity<Resource> {
        val future = asyncOperations[operationId]
            ?: throw PdfOperationException("Operation not found: $operationId")
        
        return if (future.isDone) {
            try {
                val result = future.get()
                asyncOperations.remove(operationId)
                operationProgress.remove(operationId)
                result
            } catch (ex: Exception) {
                logger.error("Error getting async result: ${ex.message}", ex)
                throw PdfOperationException("Failed to get conversion result: ${ex.message}")
            }
        } else {
            throw PdfOperationException("Conversion not yet completed")
        }
    }

    /**
     * Convert multiple PDFs to images in batch.
     */
    fun batchConvertPdfs(request: BatchConversionRequest): BatchConversionResponse {
        logger.info("Starting batch conversion of ${request.conversionJobs.size} files")
        val startTime = System.currentTimeMillis()
        
        val results = mutableListOf<ConversionJobResult>()
        var completedJobs = 0
        var failedJobs = 0
        
        request.conversionJobs.forEach { job ->
            try {
                if (job.conversionType == "PDF_TO_IMAGE") {
                    val pdfRequest = createPdfToImageRequestFromJob(job)
                    val jobStartTime = System.currentTimeMillis()
                    
                    convertPdfToImages(pdfRequest)
                    
                    results.add(ConversionJobResult(
                        success = true,
                        message = "Conversion completed successfully",
                        outputFileName = job.outputFileName ?: "converted_images.zip",
                        conversionType = job.conversionType,
                        processingTimeMs = System.currentTimeMillis() - jobStartTime,
                        fileSize = 0L // Would need actual file size
                    ))
                    completedJobs++
                } else {
                    throw PdfOperationException("Unsupported conversion type: ${job.conversionType}")
                }
            } catch (ex: Exception) {
                logger.error("Error in batch job: ${ex.message}", ex)
                results.add(ConversionJobResult(
                    success = false,
                    message = ex.message ?: "Conversion failed",
                    outputFileName = null,
                    conversionType = job.conversionType,
                    processingTimeMs = 0,
                    fileSize = null
                ))
                failedJobs++
            }
        }
        
        return BatchConversionResponse(
            success = failedJobs == 0,
            message = "Batch conversion completed: $completedJobs successful, $failedJobs failed",
            completedJobs = completedJobs,
            failedJobs = failedJobs,
            totalProcessingTimeMs = System.currentTimeMillis() - startTime,
            results = results
        )
    }

    /**
     * Validate conversion request without processing.
     */
    fun validateConversionRequest(request: PdfToImageRequest): Map<String, Any> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Validate file
            if (request.file.isEmpty) {
                errors.add("PDF file is required")
            }
            
            val filename = request.file.originalFilename?.lowercase()
            if (filename?.endsWith(".pdf") != true) {
                errors.add("File must be a PDF")
            }
            
            // Validate parameters
            if (request.dpi < 72 || request.dpi > 600) {
                errors.add("DPI must be between 72 and 600")
            }
            
            if (request.quality < 0.1 || request.quality > 1.0) {
                errors.add("Quality must be between 0.1 and 1.0")
            }
            
            // Warnings for performance
            if (request.dpi > 300) {
                warnings.add("High DPI may result in large file sizes and longer processing time")
            }
            
            return mapOf(
                "valid" to errors.isEmpty(),
                "errors" to errors,
                "warnings" to warnings,
                "estimatedProcessingTime" to estimateProcessingTime(request),
                "estimatedOutputSize" to "Unknown (requires PDF analysis)"
            )
            
        } catch (ex: Exception) {
            logger.error("Error validating request: ${ex.message}", ex)
            return mapOf(
                "valid" to false,
                "errors" to listOf(ex.message ?: "Validation failed"),
                "warnings" to emptyList<String>()
            )
        }
    }

    /**
     * Preview conversion settings without actual conversion.
     */
    fun previewConversion(request: PdfToImageRequest): Map<String, Any> {
        return try {
            val tempPdfFile = tempFileManagerService.createTempFile("preview", ".pdf", 60)
            request.file.transferTo(tempPdfFile.toFile())
            
            val pageCount = analyzePdfPageCount(tempPdfFile)
            val estimatedSize = estimateOutputSize(pageCount, request.outputFormat, request.dpi)
            val estimatedTime = estimateProcessingTime(request)
            
            tempFileManagerService.removeTempFile(tempPdfFile)
            
            mapOf(
                "success" to true,
                "message" to "Preview generated successfully",
                "totalPages" to pageCount,
                "outputFormat" to request.outputFormat,
                "colorMode" to request.colorMode,
                "dpi" to request.dpi,
                "estimatedOutputSize" to estimatedSize,
                "estimatedProcessingTime" to estimatedTime,
                "outputFiles" to (1..pageCount).map { "page_$it.${request.outputFormat.lowercase()}" }
            )
            
        } catch (ex: Exception) {
            logger.error("Error generating preview: ${ex.message}", ex)
            mapOf(
                "success" to false,
                "message" to (ex.message ?: "Preview generation failed"),
                "estimatedOutputSize" to 0,
                "estimatedProcessingTime" to 0
            )
        }
    }

    // Private helper methods

    private fun validateRequest(request: PdfToImageRequest) {
        if (request.file.isEmpty) {
            throw PdfOperationException("PDF file is required")
        }
        
        val filename = request.file.originalFilename?.lowercase()
        if (filename?.endsWith(".pdf") != true) {
            throw PdfOperationException("File must be a PDF")
        }
    }

    private fun convertPdfPagesToImages(
        pdfPath: Path,
        outputFormat: String,
        colorMode: String,
        dpi: Int,
        quality: Float,
        pageRanges: String?
    ): List<Path> {
        // Placeholder implementation - would use PDF library like PDFBox
        val imageFiles = mutableListOf<Path>()
        
        try {
            // Simulate PDF page conversion
            val pageCount = analyzePdfPageCount(pdfPath)
            val pagesToProcess = parsePageRanges(pageRanges, pageCount)
            
            pagesToProcess.forEach { pageNumber ->
                val imagePath = tempFileManagerService.createTempFile("page_$pageNumber", ".${outputFormat.lowercase()}", 60)
                
                // Create a placeholder image (in real implementation, would extract from PDF)
                val image = createPlaceholderImage(dpi)
                val processedImage = colorModeProcessor.applyColorMode(image, colorMode)
                val finalImage = configurableDpiService.applyDpiSettings(processedImage, dpi)
                
                // Save image
                ImageIO.write(finalImage, outputFormat.lowercase(), imagePath.toFile())
                imageFiles.add(imagePath)
            }
            
        } catch (ex: Exception) {
            logger.error("Error converting PDF pages to images: ${ex.message}", ex)
            throw PdfOperationException("Failed to convert PDF pages: ${ex.message}")
        }
        
        return imageFiles
    }

    private fun createImageZipArchive(imageFiles: List<Path>, outputFileName: String?): Path {
        val zipPath = tempFileManagerService.createTempFile(outputFileName ?: "images", ".zip", 60)
        
        ZipOutputStream(Files.newOutputStream(zipPath)).use { zip ->
            imageFiles.forEach { imagePath ->
                val entry = ZipEntry(imagePath.fileName.toString())
                zip.putNextEntry(entry)
                Files.copy(imagePath, zip)
                zip.closeEntry()
            }
        }
        
        return zipPath
    }

    private fun cleanupTempFiles(files: List<Path>) {
        files.forEach { file ->
            try {
                tempFileManagerService.removeTempFile(file)
            } catch (ex: Exception) {
                logger.warn("Failed to cleanup temp file: ${file.fileName}")
            }
        }
    }

    private fun analyzePdfPageCount(pdfPath: Path): Int {
        // Placeholder - would use PDF library to get actual page count
        return 5 // Simulated page count
    }

    private fun estimateOutputSize(pageCount: Int, format: String, dpi: Int): Long {
        // Rough estimation based on format and DPI
        val baseSize = when (format.uppercase()) {
            "PNG" -> 500_000L
            "JPG", "JPEG" -> 200_000L
            "GIF" -> 100_000L
            "BMP" -> 1_000_000L
            "WEBP" -> 150_000L
            "TIFF" -> 800_000L
            else -> 300_000L
        }
        
        val dpiMultiplier = (dpi / 150.0).toFloat()
        return (baseSize * pageCount * dpiMultiplier).toLong()
    }

    private fun estimateProcessingTime(request: PdfToImageRequest): Long {
        // Rough estimation in milliseconds
        val baseTime = 2000L // 2 seconds per page
        val dpiMultiplier = (request.dpi / 150.0).toFloat()
        return (baseTime * dpiMultiplier).toLong()
    }

    private fun parsePageRanges(pageRanges: String?, totalPages: Int): List<Int> {
        if (pageRanges.isNullOrBlank()) {
            return (1..totalPages).toList()
        }
        
        // Simple page range parsing (e.g., "1-3,5,7-10")
        val pages = mutableSetOf<Int>()
        pageRanges.split(",").forEach { range ->
            val trimmed = range.trim()
            if (trimmed.contains("-")) {
                val parts = trimmed.split("-")
                val start = parts[0].toInt()
                val end = parts[1].toInt()
                pages.addAll(start..end)
            } else {
                pages.add(trimmed.toInt())
            }
        }
        
        return pages.filter { it in 1..totalPages }.sorted()
    }

    private fun createPlaceholderImage(dpi: Int): BufferedImage {
        val width = (8.5 * dpi).toInt() // 8.5 inch width
        val height = (11 * dpi).toInt() // 11 inch height
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        
        val g2d = image.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        g2d.color = Color.BLACK
        g2d.drawString("Converted PDF Page", 50, 50)
        g2d.dispose()
        
        return image
    }

    private fun createPdfToImageRequestFromJob(job: ConversionJobRequest): PdfToImageRequest {
        val params = job.parameters
        return PdfToImageRequest(
            file = job.file,
            outputFormat = params["outputFormat"] as? String ?: "PNG",
            colorMode = params["colorMode"] as? String ?: "RGB",
            dpi = params["dpi"] as? Int ?: 150,
            pageRanges = params["pageRanges"] as? String,
            quality = params["quality"] as? Float ?: 0.95f,
            outputFileName = job.outputFileName
        )
    }

    private fun updateProgress(
        operationId: String,
        status: String,
        percentage: Int,
        step: String,
        remainingTime: Long?,
        processed: Int,
        total: Int
    ) {
        operationProgress[operationId] = ConversionProgressResponse(
            operationId = operationId,
            status = status,
            progressPercentage = percentage,
            currentStep = step,
            estimatedTimeRemainingMs = remainingTime,
            processedFiles = processed,
            totalFiles = total,
            conversionType = "PDF_TO_IMAGE"
        )
    }
}