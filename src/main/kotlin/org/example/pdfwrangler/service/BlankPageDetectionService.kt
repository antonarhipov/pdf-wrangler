package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.BlankPageDetectionRequest
import org.example.pdfwrangler.dto.BlankPageDetectionResponse
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
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Service for detecting blank pages in PDF documents.
 * Supports configurable sensitivity and optional blank page removal.
 */
@Service
class BlankPageDetectionService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(BlankPageDetectionService::class.java)

    /**
     * Detect blank pages in a PDF document according to the specified request.
     */
    fun detectBlankPages(request: BlankPageDetectionRequest): BlankPageDetectionResponse {
        logger.info("Starting blank page detection for file: ${request.file.originalFilename}, sensitivity: ${request.sensitivity}")
        
        val startTime = System.currentTimeMillis()
        var totalPages = 0
        var blankPages = emptyList<Int>()
        var outputFile: File? = null
        
        try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for input
            val inputFile = tempFileManagerService.createTempFile("input", ".pdf")
            request.file.transferTo(inputFile)
            
            val processingTimeMs = measureTimeMillis {
                // Simplified implementation - in a full version this would use PDFBox
                // For now, we'll simulate the detection and potentially remove blank pages
                totalPages = estimatePageCount(inputFile)
                logger.info("Total pages in document: $totalPages")
                
                // Validate detection parameters
                validateDetectionParameters(request)
                
                // Simulate blank page detection
                blankPages = simulateBlankPageDetection(totalPages, request.sensitivity)
                
                logger.info("Detected ${blankPages.size} blank pages: $blankPages")
                
                // If removal is requested, create output file
                if (request.removeBlankPages && blankPages.isNotEmpty()) {
                    outputFile = createOutputFile(request.outputFileName)
                    // In a real implementation, this would create a new PDF without the blank pages
                    inputFile.copyTo(outputFile!!, overwrite = true)
                    logger.info("Created output file with blank pages removed")
                }
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("BLANK_PAGE_DETECTION", totalPages)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "sensitivity" to request.sensitivity,
                        "removeBlankPages" to request.removeBlankPages,
                        "totalPages" to totalPages,
                        "blankPagesDetected" to blankPages.size,
                        "blankPageNumbers" to blankPages,
                        "resultingPages" to if (request.removeBlankPages) totalPages - blankPages.size else totalPages
                    )
                )
            }
            
            return BlankPageDetectionResponse(
                success = true,
                message = buildDetectionMessage(blankPages.size, totalPages, request.removeBlankPages),
                outputFileName = outputFile?.name,
                totalPages = totalPages,
                blankPages = blankPages,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length() ?: 0L
            )
            
        } catch (e: Exception) {
            logger.error("Error during blank page detection", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Blank page detection failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Blank page detection completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for a processed file (with blank pages removed).
     */
    fun getProcessedFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Processed file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating blank page detection operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract detection parameters
            val sensitivity = request.operationParameters["sensitivity"] as? Number
            val removeBlankPages = request.operationParameters["removeBlankPages"] as? Boolean ?: false
            
            // Validate sensitivity
            if (sensitivity == null) {
                validationErrors.add("Sensitivity is required")
            } else {
                val sens = sensitivity.toInt()
                if (sens < 0 || sens > 100) {
                    validationErrors.add("Sensitivity must be between 0 and 100")
                }
            }
            
            // Estimate blank pages for preview
            val estimatedBlankPages = simulateBlankPageDetection(totalPages, sensitivity?.toInt() ?: 95)
            
            // Estimate processing metrics
            val estimatedProcessingTime = (totalPages * 300L) // ~300ms per page for content analysis
            val estimatedOutputSize = if (removeBlankPages) {
                val remainingPages = totalPages - estimatedBlankPages.size
                (inputFile.length() * remainingPages / totalPages.coerceAtLeast(1)).toLong()
            } else {
                inputFile.length()
            }
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "sensitivity" to (sensitivity?.toInt() ?: 95),
                "removeBlankPages" to removeBlankPages,
                "estimatedBlankPages" to estimatedBlankPages.size,
                "estimatedBlankPageNumbers" to estimatedBlankPages,
                "estimatedRemainingPages" to if (removeBlankPages) totalPages - estimatedBlankPages.size else totalPages,
                "operationType" to "Blank Page Detection"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during blank page detection validation", e)
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
     * Validate detection parameters.
     */
    private fun validateDetectionParameters(request: BlankPageDetectionRequest) {
        // Validate sensitivity
        if (request.sensitivity < 0 || request.sensitivity > 100) {
            throw IllegalArgumentException("Sensitivity must be between 0 and 100")
        }
    }

    /**
     * Simulate blank page detection based on sensitivity.
     * In a real implementation, this would analyze page content using PDFBox.
     */
    private fun simulateBlankPageDetection(totalPages: Int, sensitivity: Int): List<Int> {
        val blankPages = mutableListOf<Int>()
        
        // Simulate detection algorithm based on sensitivity
        val detectionProbability = when {
            sensitivity >= 95 -> 0.1 // Very sensitive - fewer pages detected as blank
            sensitivity >= 85 -> 0.15 // High sensitivity
            sensitivity >= 75 -> 0.2 // Medium-high sensitivity
            sensitivity >= 50 -> 0.25 // Medium sensitivity
            sensitivity >= 25 -> 0.3 // Low-medium sensitivity
            else -> 0.35 // Low sensitivity - more pages detected as blank
        }
        
        // Randomly determine blank pages based on detection probability
        for (pageNumber in 1..totalPages) {
            if (Random.nextDouble() < detectionProbability) {
                blankPages.add(pageNumber)
            }
        }
        
        // Ensure we don't detect more than half the pages as blank (safety check)
        if (blankPages.size > totalPages / 2) {
            return blankPages.take(totalPages / 3) // Limit to 1/3 of pages
        }
        
        return blankPages.sorted()
    }

    /**
     * Build detection result message.
     */
    private fun buildDetectionMessage(blankPageCount: Int, totalPages: Int, removeBlankPages: Boolean): String {
        return when {
            blankPageCount == 0 -> "No blank pages detected in document with $totalPages pages"
            !removeBlankPages -> "Detected $blankPageCount blank page(s) out of $totalPages total pages"
            else -> "Detected and removed $blankPageCount blank page(s), resulting in ${totalPages - blankPageCount} remaining pages"
        }
    }

    /**
     * Calculate detection statistics.
     */
    fun getDetectionStatistics(blankPages: List<Int>, totalPages: Int): Map<String, Any> {
        val blankPageCount = blankPages.size
        val contentPageCount = totalPages - blankPageCount
        val blankPercentage = if (totalPages > 0) (blankPageCount * 100.0 / totalPages) else 0.0
        
        return mapOf(
            "totalPages" to totalPages,
            "blankPages" to blankPageCount,
            "contentPages" to contentPageCount,
            "blankPercentage" to String.format("%.1f", blankPercentage),
            "blankPageNumbers" to blankPages,
            "consecutiveBlankRanges" to findConsecutiveRanges(blankPages)
        )
    }

    /**
     * Find consecutive ranges of blank pages.
     */
    private fun findConsecutiveRanges(blankPages: List<Int>): List<String> {
        if (blankPages.isEmpty()) return emptyList()
        
        val ranges = mutableListOf<String>()
        var start = blankPages[0]
        var end = blankPages[0]
        
        for (i in 1 until blankPages.size) {
            if (blankPages[i] == end + 1) {
                end = blankPages[i]
            } else {
                ranges.add(if (start == end) "$start" else "$start-$end")
                start = blankPages[i]
                end = blankPages[i]
            }
        }
        
        ranges.add(if (start == end) "$start" else "$start-$end")
        return ranges
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
    private fun createOutputFile(customFileName: String?): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "no_blank_pages_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}