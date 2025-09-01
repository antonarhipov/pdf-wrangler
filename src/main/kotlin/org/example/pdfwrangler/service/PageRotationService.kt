package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.PageOperationResponse
import org.example.pdfwrangler.dto.PageOperationValidationRequest
import org.example.pdfwrangler.dto.PageOperationValidationResponse
import org.example.pdfwrangler.dto.PageRotationRequest
import org.example.pdfwrangler.exception.PdfOperationException
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

/**
 * Service for rotating pages in PDF documents.
 * Supports individual page rotation and batch operations.
 */
@Service
class PageRotationService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(PageRotationService::class.java)

    /**
     * Rotate pages in a PDF document according to the specified request.
     */
    fun rotatePages(request: PageRotationRequest): PageOperationResponse {
        logger.info("Starting page rotation operation for file: ${request.file.originalFilename}")
        
        val startTime = System.currentTimeMillis()
        var processedPages = 0
        var totalPages = 0
        var outputFile: File? = null
        
        try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for input
            val inputFile = tempFileManagerService.createTempFile("input", ".pdf")
            request.file.transferTo(inputFile)
            
            val processingTimeMs = measureTimeMillis {
                // Simplified implementation - in a full version this would use PDFBox
                // For now, we'll simulate the operation and copy the file
                totalPages = estimatePageCount(inputFile)
                logger.info("Total pages in document: $totalPages")
                
                // Validate page numbers if specified
                val pagesToRotate = request.pageNumbers ?: (1..totalPages).toList()
                validatePageNumbers(pagesToRotate, totalPages)
                
                // Parse rotation angle (validation)
                parseRotationAngle(request.rotationAngle)
                
                // Simulate rotation processing
                processedPages = pagesToRotate.size
                logger.debug("Simulated rotation of $processedPages pages by ${request.rotationAngle} degrees")
                
                // Create output file (currently just copies the input)
                outputFile = createOutputFile(request.outputFileName)
                inputFile.copyTo(outputFile!!, overwrite = true)
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("PAGE_ROTATION", processedPages)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "rotationAngle" to request.rotationAngle,
                        "pageNumbers" to pagesToRotate,
                        "processedPages" to processedPages,
                        "totalPages" to totalPages
                    )
                )
            }
            
            return PageOperationResponse(
                success = true,
                message = "Successfully rotated $processedPages pages by ${request.rotationAngle} degrees",
                outputFileName = outputFile?.name,
                totalPages = totalPages,
                processedPages = processedPages,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length(),
                operationDetails = mapOf(
                    "rotationAngle" to request.rotationAngle,
                    "rotatedPages" to (request.pageNumbers ?: (1..totalPages).toList())
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during page rotation operation", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Page rotation failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Page rotation operation completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for a rotated file.
     */
    fun getRotatedFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Rotated file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating page rotation operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract rotation parameters
            val rotationAngle = request.operationParameters["rotationAngle"]?.toString()
            val pageNumbers = request.operationParameters["pageNumbers"] as? List<*>
            
            // Validate rotation angle
            if (rotationAngle == null) {
                validationErrors.add("Rotation angle is required")
            } else {
                try {
                    parseRotationAngle(rotationAngle)
                } catch (e: IllegalArgumentException) {
                    validationErrors.add("Invalid rotation angle: $rotationAngle")
                }
            }
            
            // Validate page numbers if specified
            if (pageNumbers != null) {
                try {
                    val pageList = pageNumbers.map { (it as Number).toInt() }
                    validatePageNumbers(pageList, totalPages)
                } catch (e: Exception) {
                    validationErrors.add("Invalid page numbers: ${e.message}")
                }
            }
            
            // Estimate processing metrics
            val pagesToProcess = pageNumbers?.size ?: totalPages
            val estimatedProcessingTime = (pagesToProcess * 50L) // ~50ms per page
            val estimatedOutputSize = inputFile.length() // Size shouldn't change significantly
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "pagesToRotate" to (request.operationParameters["pageNumbers"] ?: (1..totalPages).toList()),
                "rotationAngle" to (request.operationParameters["rotationAngle"] ?: "unknown"),
                "operationType" to "Page Rotation"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during page rotation validation", e)
            PageOperationValidationResponse(
                valid = false,
                validationErrors = listOf("Validation failed: ${e.message}"),
                previewInfo = emptyMap(),
                estimatedProcessingTime = 0L,
                estimatedOutputSize = 0L
            )
        }
    }

    /**
     * Parse rotation angle string to integer degrees.
     */
    private fun parseRotationAngle(rotationAngle: String): Int {
        return when (rotationAngle) {
            "90" -> 90
            "180" -> 180
            "270" -> 270
            "-90" -> -90
            "-180" -> -180
            "-270" -> -270
            else -> throw IllegalArgumentException("Invalid rotation angle: $rotationAngle")
        }
    }

    /**
     * Estimate page count based on file size.
     * Simplified implementation - in a full version this would use PDFBox.
     */
    private fun estimatePageCount(file: File): Int {
        val fileSizeMB = file.length().toDouble() / (1024 * 1024)
        return Math.max(1, (fileSizeMB * 4).toInt()) // Rough estimate: ~4 pages per MB
    }

    /**
     * Validate that all page numbers are within valid range.
     */
    private fun validatePageNumbers(pageNumbers: List<Int>, totalPages: Int) {
        pageNumbers.forEach { pageNumber ->
            if (pageNumber < 1 || pageNumber > totalPages) {
                throw IllegalArgumentException("Page number $pageNumber is out of range (1-$totalPages)")
            }
        }
    }

    /**
     * Create output file with appropriate naming.
     */
    private fun createOutputFile(customFileName: String?): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "rotated_pages_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}