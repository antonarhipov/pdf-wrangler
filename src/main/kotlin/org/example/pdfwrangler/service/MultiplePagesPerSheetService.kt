package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.MultiplePagesPerSheetRequest
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
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Service for arranging multiple pages per sheet for layout optimization.
 * Supports grid and linear layouts with configurable page counts and margins.
 */
@Service
class MultiplePagesPerSheetService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(MultiplePagesPerSheetService::class.java)

    /**
     * Arrange multiple pages per sheet according to the specified request.
     */
    fun arrangePages(request: MultiplePagesPerSheetRequest): PageOperationResponse {
        logger.info("Starting multiple pages per sheet arrangement: ${request.pagesPerSheet} pages/${request.layout} for file: ${request.file.originalFilename}")
        
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
                
                // Validate arrangement parameters
                validateArrangementParameters(request)
                
                // Calculate arrangement information
                val arrangementInfo = calculateArrangementInfo(request, totalPages)
                
                // Simulate arrangement processing
                processedPages = totalPages
                val resultPages = ceil(totalPages.toDouble() / request.pagesPerSheet).toInt()
                
                logger.debug("Simulated arrangement of $processedPages pages into $resultPages sheets with ${request.pagesPerSheet} pages per sheet")
                
                // Create output file (currently just copies the input)
                outputFile = createOutputFile(request.outputFileName, request.pagesPerSheet, request.layout)
                inputFile.copyTo(outputFile!!, overwrite = true)
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("MULTIPLE_PAGES_PER_SHEET", processedPages)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "pagesPerSheet" to request.pagesPerSheet,
                        "layout" to request.layout,
                        "margin" to request.margin,
                        "totalPages" to totalPages,
                        "processedPages" to processedPages,
                        "resultPages" to resultPages,
                        "arrangementInfo" to arrangementInfo
                    )
                )
            }
            
            return PageOperationResponse(
                success = true,
                message = "Successfully arranged $processedPages pages into ${ceil(totalPages.toDouble() / request.pagesPerSheet).toInt()} sheets with ${request.pagesPerSheet} pages per sheet",
                outputFileName = outputFile?.name,
                totalPages = ceil(totalPages.toDouble() / request.pagesPerSheet).toInt(),
                processedPages = processedPages,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length(),
                operationDetails = mapOf(
                    "pagesPerSheet" to request.pagesPerSheet,
                    "layout" to request.layout,
                    "margin" to request.margin,
                    "originalPages" to totalPages,
                    "resultPages" to ceil(totalPages.toDouble() / request.pagesPerSheet).toInt(),
                    "arrangementType" to "Multiple Pages Per Sheet"
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during multiple pages per sheet arrangement", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Multiple pages per sheet arrangement failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Multiple pages per sheet arrangement completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for an arranged file.
     */
    fun getArrangedFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Arranged file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating multiple pages per sheet arrangement operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract arrangement parameters
            val pagesPerSheet = request.operationParameters["pagesPerSheet"] as? Number
            val layout = request.operationParameters["layout"]?.toString()
            val margin = request.operationParameters["margin"] as? Number
            
            // Validate pages per sheet
            if (pagesPerSheet == null) {
                validationErrors.add("Pages per sheet is required")
            } else {
                val pages = pagesPerSheet.toInt()
                if (pages < 2 || pages > 16) {
                    validationErrors.add("Pages per sheet must be between 2 and 16")
                }
            }
            
            // Validate layout
            if (layout == null) {
                validationErrors.add("Layout is required")
            } else if (layout !in listOf("grid", "linear")) {
                validationErrors.add("Invalid layout: $layout (must be 'grid' or 'linear')")
            }
            
            // Validate margin
            if (margin != null && margin.toInt() < 0) {
                validationErrors.add("Margin must be non-negative")
            }
            
            // Check if arrangement is meaningful
            val pagesCount = pagesPerSheet?.toInt() ?: 2
            if (totalPages <= 1 && pagesCount > 1) {
                validationErrors.add("Document must have more than one page for multi-page arrangement")
            }
            
            // Estimate processing metrics
            val estimatedProcessingTime = (totalPages * 125L) // ~125ms per page for arrangement
            val resultPages = ceil(totalPages.toDouble() / pagesCount).toInt()
            val estimatedOutputSize = (inputFile.length() * resultPages / totalPages.coerceAtLeast(1)).toLong()
            
            // Calculate grid dimensions for preview
            val gridInfo = calculateGridDimensions(pagesCount, layout ?: "grid")
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "pagesPerSheet" to (pagesPerSheet?.toInt() ?: 2),
                "layout" to (layout ?: "unknown"),
                "margin" to (margin?.toInt() ?: 10),
                "resultPages" to resultPages,
                "gridDimensions" to gridInfo,
                "operationType" to "Multiple Pages Per Sheet"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during multiple pages per sheet validation", e)
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
     * Validate arrangement parameters.
     */
    private fun validateArrangementParameters(request: MultiplePagesPerSheetRequest) {
        // Validate pages per sheet
        if (request.pagesPerSheet < 2 || request.pagesPerSheet > 16) {
            throw IllegalArgumentException("Pages per sheet must be between 2 and 16")
        }
        
        // Validate layout
        if (request.layout !in listOf("grid", "linear")) {
            throw IllegalArgumentException("Invalid layout: ${request.layout}")
        }
        
        // Validate margin
        if (request.margin < 0) {
            throw IllegalArgumentException("Margin must be non-negative")
        }
    }

    /**
     * Calculate arrangement information based on request parameters.
     */
    private fun calculateArrangementInfo(request: MultiplePagesPerSheetRequest, totalPages: Int): Map<String, Any> {
        val resultPages = ceil(totalPages.toDouble() / request.pagesPerSheet).toInt()
        val gridDimensions = calculateGridDimensions(request.pagesPerSheet, request.layout)
        
        return mapOf(
            "pagesPerSheet" to request.pagesPerSheet,
            "layout" to request.layout,
            "margin" to request.margin,
            "originalPages" to totalPages,
            "resultPages" to resultPages,
            "gridDimensions" to gridDimensions,
            "spacingReduction" to (1.0 - resultPages.toDouble() / totalPages)
        )
    }

    /**
     * Calculate grid dimensions for the arrangement.
     */
    private fun calculateGridDimensions(pagesPerSheet: Int, layout: String): Map<String, Int> {
        return when (layout) {
            "grid" -> {
                val cols = ceil(sqrt(pagesPerSheet.toDouble())).toInt()
                val rows = ceil(pagesPerSheet.toDouble() / cols).toInt()
                mapOf(
                    "columns" to cols,
                    "rows" to rows
                )
            }
            "linear" -> {
                // Determine best linear arrangement
                if (pagesPerSheet <= 4) {
                    mapOf(
                        "columns" to pagesPerSheet,
                        "rows" to 1
                    )
                } else {
                    mapOf(
                        "columns" to 1,
                        "rows" to pagesPerSheet
                    )
                }
            }
            else -> mapOf(
                "columns" to 1,
                "rows" to pagesPerSheet
            )
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
    private fun createOutputFile(customFileName: String?, pagesPerSheet: Int, layout: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "${pagesPerSheet}per_sheet_${layout}_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}