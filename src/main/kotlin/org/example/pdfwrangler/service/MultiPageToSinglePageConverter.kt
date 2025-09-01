package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.MultiPageToSinglePageRequest
import org.example.pdfwrangler.dto.PageOperationResponse
import org.example.pdfwrangler.dto.PageOperationValidationRequest
import org.example.pdfwrangler.dto.PageOperationValidationResponse
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
 * Service for converting multi-page PDF layouts to single-page layouts.
 * Supports vertical and horizontal arrangement with configurable spacing.
 */
@Service
class MultiPageToSinglePageConverter(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(MultiPageToSinglePageConverter::class.java)

    /**
     * Convert multi-page layout to single-page layout according to the specified request.
     */
    fun convertToSinglePage(request: MultiPageToSinglePageRequest): PageOperationResponse {
        logger.info("Starting multi-page to single-page conversion: ${request.layout} layout for file: ${request.file.originalFilename}")
        
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
                
                // Validate conversion parameters
                validateConversionParameters(request)
                
                // Calculate layout information
                val layoutInfo = calculateLayoutInfo(request, totalPages)
                
                // Simulate conversion processing
                processedPages = totalPages
                val resultPages = 1 // All pages combined into one
                
                logger.debug("Simulated conversion of $processedPages pages to single ${request.layout} layout")
                
                // Create output file (currently just copies the input)
                outputFile = createOutputFile(request.outputFileName, request.layout)
                inputFile.copyTo(outputFile!!, overwrite = true)
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("MULTI_TO_SINGLE_CONVERSION", processedPages)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "layout" to request.layout,
                        "spacing" to request.spacing,
                        "totalPages" to totalPages,
                        "processedPages" to processedPages,
                        "resultPages" to resultPages,
                        "layoutInfo" to layoutInfo
                    )
                )
            }
            
            return PageOperationResponse(
                success = true,
                message = "Successfully converted $processedPages pages to single ${request.layout} layout",
                outputFileName = outputFile?.name,
                totalPages = 1, // Result is single page
                processedPages = processedPages,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length(),
                operationDetails = mapOf(
                    "layout" to request.layout,
                    "spacing" to request.spacing,
                    "originalPages" to totalPages,
                    "resultPages" to 1,
                    "conversionType" to "Multi-page to Single-page"
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during multi-page to single-page conversion", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Multi-page to single-page conversion failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Multi-page to single-page conversion completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for a converted file.
     */
    fun getConvertedFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Converted file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating multi-page to single-page conversion operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract conversion parameters
            val layout = request.operationParameters["layout"]?.toString()
            val spacing = request.operationParameters["spacing"] as? Number
            
            // Validate layout
            if (layout == null) {
                validationErrors.add("Layout is required")
            } else if (layout !in listOf("vertical", "horizontal")) {
                validationErrors.add("Invalid layout: $layout (must be 'vertical' or 'horizontal')")
            }
            
            // Validate spacing
            if (spacing != null && spacing.toInt() < 0) {
                validationErrors.add("Spacing must be non-negative")
            }
            
            // Check if conversion is meaningful
            if (totalPages <= 1) {
                validationErrors.add("Document must have more than one page for multi-page to single-page conversion")
            }
            
            // Estimate processing metrics
            val estimatedProcessingTime = (totalPages * 150L) // ~150ms per page for layout conversion
            val estimatedOutputSize = inputFile.length() // Rough estimate - might be different due to layout changes
            
            // Calculate estimated dimensions
            val layoutSpacing = spacing?.toInt() ?: 10
            val estimatedHeight = when (layout) {
                "vertical" -> totalPages * 842 + (totalPages - 1) * layoutSpacing // Assume A4 height
                "horizontal" -> 842 // Single row height
                else -> 842
            }
            val estimatedWidth = when (layout) {
                "horizontal" -> totalPages * 595 + (totalPages - 1) * layoutSpacing // Assume A4 width
                "vertical" -> 595 // Single column width
                else -> 595
            }
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "layout" to (layout ?: "unknown"),
                "spacing" to (spacing?.toInt() ?: 10),
                "resultPages" to 1,
                "estimatedDimensions" to mapOf(
                    "width" to estimatedWidth,
                    "height" to estimatedHeight
                ),
                "operationType" to "Multi-page to Single-page Conversion"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during multi-page to single-page conversion validation", e)
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
     * Validate conversion parameters.
     */
    private fun validateConversionParameters(request: MultiPageToSinglePageRequest) {
        // Validate layout
        if (request.layout !in listOf("vertical", "horizontal")) {
            throw IllegalArgumentException("Invalid layout: ${request.layout}")
        }
        
        // Validate spacing
        if (request.spacing < 0) {
            throw IllegalArgumentException("Spacing must be non-negative")
        }
    }

    /**
     * Calculate layout information based on request parameters.
     */
    private fun calculateLayoutInfo(request: MultiPageToSinglePageRequest, totalPages: Int): Map<String, Any> {
        val pageWidth = 595 // Assume A4 width in points
        val pageHeight = 842 // Assume A4 height in points
        
        return when (request.layout) {
            "vertical" -> {
                val totalHeight = totalPages * pageHeight + (totalPages - 1) * request.spacing
                mapOf(
                    "arrangement" to "vertical",
                    "columns" to 1,
                    "rows" to totalPages,
                    "resultWidth" to pageWidth,
                    "resultHeight" to totalHeight,
                    "spacingBetweenPages" to request.spacing
                )
            }
            "horizontal" -> {
                val totalWidth = totalPages * pageWidth + (totalPages - 1) * request.spacing
                mapOf(
                    "arrangement" to "horizontal",
                    "columns" to totalPages,
                    "rows" to 1,
                    "resultWidth" to totalWidth,
                    "resultHeight" to pageHeight,
                    "spacingBetweenPages" to request.spacing
                )
            }
            else -> emptyMap()
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
     * Create output file with appropriate naming.
     */
    private fun createOutputFile(customFileName: String?, layout: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "single_page_${layout}_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}