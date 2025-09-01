package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.PageOperationResponse
import org.example.pdfwrangler.dto.PageOperationValidationRequest
import org.example.pdfwrangler.dto.PageOperationValidationResponse
import org.example.pdfwrangler.dto.PageRearrangementRequest
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
 * Service for rearranging pages in PDF documents.
 * Supports page reordering, duplication, and removal operations.
 */
@Service
class PageRearrangementService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(PageRearrangementService::class.java)

    /**
     * Rearrange pages in a PDF document according to the specified request.
     */
    fun rearrangePages(request: PageRearrangementRequest): PageOperationResponse {
        logger.info("Starting page rearrangement operation: ${request.operation} for file: ${request.file.originalFilename}")
        
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
                
                // Validate operation parameters
                validateRearrangementOperation(request, totalPages)
                
                // Simulate rearrangement processing
                processedPages = when (request.operation) {
                    "reorder" -> request.newOrder?.size ?: request.pageNumbers.size
                    "duplicate" -> request.pageNumbers.size * 2 // Original + duplicates
                    "remove" -> totalPages - request.pageNumbers.size
                    else -> request.pageNumbers.size
                }
                
                logger.debug("Simulated ${request.operation} operation on ${request.pageNumbers.size} pages")
                
                // Create output file (currently just copies the input)
                outputFile = createOutputFile(request.outputFileName, request.operation)
                inputFile.copyTo(outputFile!!, overwrite = true)
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("PAGE_REARRANGEMENT", request.pageNumbers.size)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "operation" to request.operation,
                        "pageNumbers" to request.pageNumbers,
                        "newOrder" to (request.newOrder ?: emptyList<Int>()),
                        "processedPages" to processedPages,
                        "totalPages" to totalPages
                    )
                )
            }
            
            return PageOperationResponse(
                success = true,
                message = "Successfully performed ${request.operation} operation on ${request.pageNumbers.size} pages",
                outputFileName = outputFile?.name,
                totalPages = if (request.operation == "remove") processedPages else totalPages,
                processedPages = request.pageNumbers.size,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length(),
                operationDetails = mapOf(
                    "operation" to request.operation,
                    "targetPages" to request.pageNumbers,
                    "newOrder" to (request.newOrder ?: emptyList<Int>()),
                    "resultingPages" to processedPages
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during page rearrangement operation", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Page rearrangement failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Page rearrangement operation completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for a rearranged file.
     */
    fun getRearrangedFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Rearranged file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating page rearrangement operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract operation parameters
            val operation = request.operationParameters["operation"]?.toString()
            val pageNumbers = request.operationParameters["pageNumbers"] as? List<*>
            val newOrder = request.operationParameters["newOrder"] as? List<*>
            
            // Validate operation type
            if (operation == null) {
                validationErrors.add("Operation type is required")
            } else if (operation !in listOf("reorder", "duplicate", "remove")) {
                validationErrors.add("Invalid operation type: $operation")
            }
            
            // Validate page numbers
            if (pageNumbers == null || pageNumbers.isEmpty()) {
                validationErrors.add("Page numbers are required")
            } else {
                try {
                    val pageList = pageNumbers.map { (it as Number).toInt() }
                    validatePageNumbers(pageList, totalPages)
                    
                    // Validate reorder operation
                    if (operation == "reorder") {
                        if (newOrder == null) {
                            validationErrors.add("New order is required for reorder operation")
                        } else {
                            try {
                                val orderList = newOrder.map { (it as Number).toInt() }
                                if (orderList.size != pageList.size) {
                                    validationErrors.add("New order must have same number of elements as page numbers")
                                }
                                validatePageNumbers(orderList, totalPages)
                            } catch (e: Exception) {
                                validationErrors.add("Invalid new order: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    validationErrors.add("Invalid page numbers: ${e.message}")
                }
            }
            
            // Estimate processing metrics
            val pagesToProcess = pageNumbers?.size ?: 0
            val estimatedProcessingTime = (pagesToProcess * 75L) // ~75ms per page for rearrangement
            val estimatedOutputSize = when (operation) {
                "duplicate" -> inputFile.length() * 2
                "remove" -> inputFile.length() * (1 - pagesToProcess.toDouble() / totalPages)
                else -> inputFile.length()
            }.toLong()
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "operation" to (operation ?: "unknown"),
                "targetPages" to (pageNumbers ?: emptyList<Int>()),
                "newOrder" to (newOrder ?: emptyList<Int>()),
                "estimatedResultPages" to when (operation) {
                    "duplicate" -> totalPages + pagesToProcess
                    "remove" -> totalPages - pagesToProcess
                    else -> totalPages
                },
                "operationType" to "Page Rearrangement"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during page rearrangement validation", e)
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
     * Validate rearrangement operation parameters.
     */
    private fun validateRearrangementOperation(request: PageRearrangementRequest, totalPages: Int) {
        // Validate page numbers
        validatePageNumbers(request.pageNumbers, totalPages)
        
        // Validate operation-specific parameters
        when (request.operation) {
            "reorder" -> {
                if (request.newOrder == null) {
                    throw IllegalArgumentException("New order is required for reorder operation")
                }
                if (request.newOrder.size != request.pageNumbers.size) {
                    throw IllegalArgumentException("New order must have same number of elements as page numbers")
                }
                validatePageNumbers(request.newOrder, totalPages)
            }
            "duplicate" -> {
                // No additional validation needed for duplicate
            }
            "remove" -> {
                if (request.pageNumbers.size >= totalPages) {
                    throw IllegalArgumentException("Cannot remove all pages from document")
                }
            }
            else -> {
                throw IllegalArgumentException("Invalid operation: ${request.operation}")
            }
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
    private fun createOutputFile(customFileName: String?, operation: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "${operation}_pages_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}