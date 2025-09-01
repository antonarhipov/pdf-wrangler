package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.PdfOperationException
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

/**
 * Service for converting images to PDF format.
 * Supports multiple input image formats and flexible page layout options.
 */
@Service
class ImageToPdfService(
    private val tempFileManagerService: TempFileManagerService,
    private val operationContextLogger: OperationContextLogger
) {

    private val logger = LoggerFactory.getLogger(ImageToPdfService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<Resource>>>()
    private val operationProgress = ConcurrentHashMap<String, ConversionProgressResponse>()

    // Standard page dimensions in points (72 points = 1 inch)
    companion object {
        const val POINTS_PER_INCH = 72f
        val PAGE_SIZES = mapOf(
            "A4" to Pair(595f, 842f),      // 8.27 x 11.69 inches
            "LETTER" to Pair(612f, 792f),   // 8.5 x 11 inches
            "LEGAL" to Pair(612f, 1008f),   // 8.5 x 14 inches
            "A3" to Pair(842f, 1191f),     // 11.69 x 16.54 inches
            "A5" to Pair(420f, 595f)       // 5.83 x 8.27 inches
        )
    }

    /**
     * Convert images to PDF synchronously.
     */
    fun convertImagesToPdf(request: ImageToPdfRequest): ResponseEntity<Resource> {
        logger.info("Converting ${request.files.size} images to PDF with page size ${request.pageSize}")
        val startTime = System.currentTimeMillis()
        val operationId = operationContextLogger.logOperationStart("IMAGE_TO_PDF", request.files.size)
        
        return try {
            validateRequest(request)
            
            // Process each image and create PDF
            val processedImages = processImages(request)
            val pdfFile = createPdfFromImages(processedImages, request)
            val resource = ByteArrayResource(Files.readAllBytes(pdfFile))
            
            // Cleanup temporary files
            cleanupTempFiles(processedImages + listOf(pdfFile))
            
            val endTime = System.currentTimeMillis()
            operationContextLogger.logOperationSuccess("IMAGE_TO_PDF", endTime - startTime, operationId)
            
            ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"${request.outputFileName ?: "converted_document.pdf"}\"")
                .body(resource)
                
        } catch (ex: Exception) {
            logger.error("Error converting images to PDF: ${ex.message}", ex)
            operationContextLogger.logOperationError(ex, operationId)
            throw PdfOperationException("Image to PDF conversion failed: ${ex.message}")
        }
    }

    /**
     * Get conversion status and metadata.
     */
    fun getConversionStatus(request: ImageToPdfRequest): ImageToPdfResponse {
        logger.info("Getting conversion status for ${request.files.size} image files")
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // Analyze images without converting
            val imageAnalysis = analyzeImages(request)
            val estimatedSize = estimateOutputSize(imageAnalysis, request.pageSize, request.quality)
            
            ImageToPdfResponse(
                success = true,
                message = "Image analysis completed successfully",
                outputFileName = request.outputFileName ?: "converted_document.pdf",
                totalPages = imageAnalysis.validImageCount,
                processedImages = 0,
                processingTimeMs = System.currentTimeMillis() - startTime,
                fileSize = estimatedSize
            )
            
        } catch (ex: Exception) {
            logger.error("Error getting conversion status: ${ex.message}", ex)
            ImageToPdfResponse(
                success = false,
                message = ex.message ?: "Status analysis failed",
                outputFileName = null,
                totalPages = 0,
                processedImages = 0,
                processingTimeMs = 0,
                fileSize = null
            )
        }
    }

    /**
     * Start asynchronous image to PDF conversion.
     */
    fun startAsyncConversion(request: ImageToPdfRequest): String {
        val operationId = UUID.randomUUID().toString()
        logger.info("Starting async conversion with operation ID: $operationId")
        
        val future = CompletableFuture.supplyAsync {
            updateProgress(operationId, "PROCESSING", 0, "Starting conversion", null, 0, request.files.size)
            
            try {
                val result = convertImagesToPdf(request)
                updateProgress(operationId, "COMPLETED", 100, "Conversion completed", 0, request.files.size, request.files.size)
                result
            } catch (ex: Exception) {
                updateProgress(operationId, "FAILED", 0, "Conversion failed: ${ex.message}", null, 0, request.files.size)
                throw ex
            }
        }
        
        asyncOperations[operationId] = future
        updateProgress(operationId, "STARTED", 0, "Conversion queued", null, 0, request.files.size)
        
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
     * Convert multiple image sets to PDFs in batch.
     */
    fun batchConvertImages(request: BatchConversionRequest): BatchConversionResponse {
        logger.info("Starting batch conversion of ${request.conversionJobs.size} image sets")
        val startTime = System.currentTimeMillis()
        
        val results = mutableListOf<ConversionJobResult>()
        var completedJobs = 0
        var failedJobs = 0
        
        request.conversionJobs.forEach { job ->
            try {
                if (job.conversionType == "IMAGE_TO_PDF") {
                    val imageRequest = createImageToPdfRequestFromJob(job)
                    val jobStartTime = System.currentTimeMillis()
                    
                    convertImagesToPdf(imageRequest)
                    
                    results.add(ConversionJobResult(
                        success = true,
                        message = "Conversion completed successfully",
                        outputFileName = job.outputFileName ?: "converted_document.pdf",
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
    fun validateConversionRequest(request: ImageToPdfRequest): Map<String, Any> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Validate files
            if (request.files.isEmpty()) {
                errors.add("At least one image file is required")
            }
            
            // Validate image formats
            val supportedFormats = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff")
            request.files.forEach { file ->
                val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
                if (extension !in supportedFormats) {
                    errors.add("Unsupported image format: $extension")
                }
            }
            
            // Validate page size
            if (request.pageSize != "CUSTOM" && !PAGE_SIZES.containsKey(request.pageSize)) {
                errors.add("Unsupported page size: ${request.pageSize}")
            }
            
            if (request.pageSize == "CUSTOM") {
                if (request.customWidth == null || request.customHeight == null) {
                    errors.add("Custom page size requires both width and height")
                }
            }
            
            // Validate quality
            if (request.quality < 0.1 || request.quality > 1.0) {
                errors.add("Quality must be between 0.1 and 1.0")
            }
            
            // Warnings for performance
            if (request.files.size > 100) {
                warnings.add("Large number of images may result in longer processing time")
            }
            
            return mapOf(
                "valid" to errors.isEmpty(),
                "errors" to errors,
                "warnings" to warnings,
                "estimatedProcessingTime" to estimateProcessingTime(request),
                "estimatedOutputSize" to "Depends on image sizes and quality settings"
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
    fun previewConversion(request: ImageToPdfRequest): Map<String, Any> {
        return try {
            val imageAnalysis = analyzeImages(request)
            val estimatedSize = estimateOutputSize(imageAnalysis, request.pageSize, request.quality)
            val estimatedTime = estimateProcessingTime(request)
            
            mapOf(
                "success" to true,
                "message" to "Preview generated successfully",
                "totalImages" to request.files.size,
                "validImages" to imageAnalysis.validImageCount,
                "invalidImages" to imageAnalysis.invalidImageCount,
                "pageSize" to request.pageSize,
                "orientation" to request.orientation,
                "quality" to request.quality,
                "maintainAspectRatio" to request.maintainAspectRatio,
                "estimatedPages" to imageAnalysis.validImageCount,
                "estimatedOutputSize" to estimatedSize,
                "estimatedProcessingTime" to estimatedTime,
                "pageDimensions" to getPageDimensions(request.pageSize, request.customWidth, request.customHeight)
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

    private fun validateRequest(request: ImageToPdfRequest) {
        if (request.files.isEmpty()) {
            throw PdfOperationException("At least one image file is required")
        }
        
        val supportedFormats = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff")
        request.files.forEach { file ->
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            if (extension !in supportedFormats) {
                throw PdfOperationException("Unsupported image format: $extension")
            }
        }
    }

    private fun processImages(request: ImageToPdfRequest): List<Path> {
        val processedImages = mutableListOf<Path>()
        
        request.files.forEachIndexed { index, file ->
            try {
                // Save uploaded image to temporary file
                val tempImageFile = tempFileManagerService.createTempFile("image_$index", ".${file.originalFilename?.substringAfterLast(".") ?: "png"}", 60)
                file.transferTo(tempImageFile.toFile())
                
                // Load and process the image
                val image = ImageIO.read(tempImageFile.toFile())
                if (image != null) {
                    val processedImage = processImageForPdf(image, request)
                    
                    // Save processed image
                    val processedImageFile = tempFileManagerService.createTempFile("processed_$index", ".png", 60)
                    ImageIO.write(processedImage, "PNG", processedImageFile.toFile())
                    processedImages.add(processedImageFile)
                }
                
                // Clean up original temp file
                tempFileManagerService.removeTempFile(tempImageFile)
                
            } catch (ex: Exception) {
                logger.warn("Failed to process image ${file.originalFilename}: ${ex.message}")
            }
        }
        
        return processedImages
    }

    private fun processImageForPdf(image: BufferedImage, request: ImageToPdfRequest): BufferedImage {
        val pageDimensions = getPageDimensions(request.pageSize, request.customWidth, request.customHeight)
        val pageWidth = if (request.orientation == "LANDSCAPE") pageDimensions.second.toInt() else pageDimensions.first.toInt()
        val pageHeight = if (request.orientation == "LANDSCAPE") pageDimensions.first.toInt() else pageDimensions.second.toInt()
        
        // Calculate scaling to fit image on page
        val scaleX = pageWidth.toFloat() / image.width
        val scaleY = pageHeight.toFloat() / image.height
        
        val scale = if (request.maintainAspectRatio) {
            min(scaleX, scaleY)
        } else {
            max(scaleX, scaleY)
        }
        
        val scaledWidth = (image.width * scale).toInt()
        val scaledHeight = (image.height * scale).toInt()
        
        // Create processed image
        val processedImage = BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = processedImage.createGraphics()
        
        // Configure high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Fill background with white
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, pageWidth, pageHeight)
        
        // Center the image on the page
        val x = (pageWidth - scaledWidth) / 2
        val y = (pageHeight - scaledHeight) / 2
        
        g2d.drawImage(image, x, y, scaledWidth, scaledHeight, null)
        g2d.dispose()
        
        return processedImage
    }

    private fun createPdfFromImages(imageFiles: List<Path>, request: ImageToPdfRequest): Path {
        // Placeholder implementation - would use PDF library like iText or PDFBox
        val pdfFile = tempFileManagerService.createTempFile("converted_document", ".pdf", 60)
        
        // In a real implementation, this would:
        // 1. Create a new PDF document
        // 2. Add each image as a separate page
        // 3. Apply quality and compression settings
        // 4. Save the PDF to the temporary file
        
        // For now, create a simple placeholder PDF content
        val pdfContent = createPlaceholderPdfContent(imageFiles.size, request)
        Files.write(pdfFile, pdfContent.toByteArray())
        
        return pdfFile
    }

    private fun createPlaceholderPdfContent(pageCount: Int, request: ImageToPdfRequest): String {
        // Simple PDF structure placeholder
        return """
            %PDF-1.4
            1 0 obj
            <<
            /Type /Catalog
            /Pages 2 0 R
            >>
            endobj
            
            2 0 obj
            <<
            /Type /Pages
            /Kids [3 0 R]
            /Count $pageCount
            >>
            endobj
            
            3 0 obj
            <<
            /Type /Page
            /Parent 2 0 R
            /MediaBox [0 0 ${getPageDimensions(request.pageSize, request.customWidth, request.customHeight).first} ${getPageDimensions(request.pageSize, request.customWidth, request.customHeight).second}]
            >>
            endobj
            
            xref
            0 4
            0000000000 65535 f 
            0000000009 00000 n 
            0000000058 00000 n 
            0000000115 00000 n 
            trailer
            <<
            /Size 4
            /Root 1 0 R
            >>
            startxref
            300
            %%EOF
        """.trimIndent()
    }

    private fun analyzeImages(request: ImageToPdfRequest): ImageAnalysis {
        var validImageCount = 0
        var invalidImageCount = 0
        val supportedFormats = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff")
        
        request.files.forEach { file ->
            val extension = file.originalFilename?.substringAfterLast(".")?.lowercase()
            if (extension in supportedFormats && !file.isEmpty) {
                validImageCount++
            } else {
                invalidImageCount++
            }
        }
        
        return ImageAnalysis(validImageCount, invalidImageCount)
    }

    private fun estimateOutputSize(analysis: ImageAnalysis, pageSize: String, quality: Float): Long {
        // Rough estimation based on page size and quality
        val baseSizePerPage = when (pageSize) {
            "A3" -> 2_000_000L
            "A4", "LETTER" -> 1_500_000L
            "LEGAL" -> 1_800_000L
            "A5" -> 1_000_000L
            else -> 1_500_000L
        }
        
        val qualityMultiplier = quality
        return (baseSizePerPage * analysis.validImageCount * qualityMultiplier).toLong()
    }

    private fun estimateProcessingTime(request: ImageToPdfRequest): Long {
        // Rough estimation in milliseconds
        val baseTimePerImage = 1000L // 1 second per image
        return baseTimePerImage * request.files.size
    }

    private fun getPageDimensions(pageSize: String, customWidth: Float?, customHeight: Float?): Pair<Float, Float> {
        return when (pageSize) {
            "CUSTOM" -> {
                if (customWidth != null && customHeight != null) {
                    Pair(customWidth * POINTS_PER_INCH, customHeight * POINTS_PER_INCH)
                } else {
                    PAGE_SIZES["A4"]!!
                }
            }
            else -> PAGE_SIZES[pageSize] ?: PAGE_SIZES["A4"]!!
        }
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

    private fun createImageToPdfRequestFromJob(job: ConversionJobRequest): ImageToPdfRequest {
        val params = job.parameters
        return ImageToPdfRequest(
            files = listOf(job.file),
            pageSize = params["pageSize"] as? String ?: "A4",
            orientation = params["orientation"] as? String ?: "PORTRAIT",
            quality = params["quality"] as? Float ?: 0.95f,
            maintainAspectRatio = params["maintainAspectRatio"] as? Boolean ?: true,
            outputFileName = job.outputFileName,
            customWidth = params["customWidth"] as? Float,
            customHeight = params["customHeight"] as? Float
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
            conversionType = "IMAGE_TO_PDF"
        )
    }

    // Data classes for internal use
    private data class ImageAnalysis(
        val validImageCount: Int,
        val invalidImageCount: Int
    )
}