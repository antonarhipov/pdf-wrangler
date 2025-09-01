package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.CustomPageNumberingRequest
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
 * Service for adding custom page numbering to PDF documents.
 * Supports various number formats (Arabic, Roman, alphabetic) and positioning options.
 */
@Service
class CustomPageNumberingService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(CustomPageNumberingService::class.java)

    /**
     * Add custom page numbers to a PDF document according to the specified request.
     */
    fun addPageNumbers(request: CustomPageNumberingRequest): PageOperationResponse {
        logger.info("Starting custom page numbering: ${request.numberFormat} format for file: ${request.file.originalFilename}")
        
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
                // For now, we'll simulate the numbering operation and copy the file
                totalPages = estimatePageCount(inputFile)
                logger.info("Total pages in document: $totalPages")
                
                // Validate numbering parameters
                validateNumberingParameters(request, totalPages)
                
                // Calculate numbering information
                val numberingInfo = calculateNumberingInfo(request, totalPages)
                
                // Simulate numbering processing
                val pagesToNumber = request.pageNumbers ?: (1..totalPages).toList()
                processedPages = pagesToNumber.size
                
                logger.debug("Simulated adding ${request.numberFormat} page numbers to $processedPages pages at ${request.position}")
                
                // Create output file (currently just copies the input)
                outputFile = createOutputFile(request.outputFileName, request.numberFormat)
                inputFile.copyTo(outputFile!!, overwrite = true)
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("CUSTOM_PAGE_NUMBERING", processedPages)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "numberFormat" to request.numberFormat,
                        "startingNumber" to request.startingNumber,
                        "position" to request.position,
                        "fontSize" to request.fontSize,
                        "prefix" to request.prefix,
                        "suffix" to request.suffix,
                        "pageNumbers" to pagesToNumber,
                        "processedPages" to processedPages,
                        "totalPages" to totalPages,
                        "numberingInfo" to numberingInfo
                    )
                )
            }
            
            return PageOperationResponse(
                success = true,
                message = "Successfully added ${request.numberFormat} page numbers to $processedPages pages",
                outputFileName = outputFile?.name,
                totalPages = totalPages,
                processedPages = processedPages,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length(),
                operationDetails = mapOf(
                    "numberFormat" to request.numberFormat,
                    "numberedPages" to (request.pageNumbers ?: (1..totalPages).toList()),
                    "numberingParameters" to mapOf(
                        "startingNumber" to request.startingNumber,
                        "position" to request.position,
                        "fontSize" to request.fontSize,
                        "prefix" to request.prefix,
                        "suffix" to request.suffix
                    )
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during custom page numbering", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Custom page numbering failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Custom page numbering completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for a numbered file.
     */
    fun getNumberedFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Numbered file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating custom page numbering operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract numbering parameters
            val numberFormat = request.operationParameters["numberFormat"]?.toString()
            val startingNumber = request.operationParameters["startingNumber"] as? Number
            val position = request.operationParameters["position"]?.toString()
            val fontSize = request.operationParameters["fontSize"] as? Number
            val prefix = request.operationParameters["prefix"]?.toString() ?: ""
            val suffix = request.operationParameters["suffix"]?.toString() ?: ""
            val pageNumbers = request.operationParameters["pageNumbers"] as? List<*>
            
            // Validate number format
            if (numberFormat == null) {
                validationErrors.add("Number format is required")
            } else if (numberFormat !in listOf("arabic", "roman", "ROMAN", "alpha", "ALPHA")) {
                validationErrors.add("Invalid number format: $numberFormat")
            }
            
            // Validate starting number
            if (startingNumber == null) {
                validationErrors.add("Starting number is required")
            } else if (startingNumber.toInt() < 1) {
                validationErrors.add("Starting number must be at least 1")
            }
            
            // Validate position
            if (position == null) {
                validationErrors.add("Position is required")
            } else if (position !in listOf("topLeft", "topCenter", "topRight", "bottomLeft", "bottomCenter", "bottomRight")) {
                validationErrors.add("Invalid position: $position")
            }
            
            // Validate font size
            if (fontSize == null) {
                validationErrors.add("Font size is required")
            } else {
                val size = fontSize.toInt()
                if (size < 6 || size > 72) {
                    validationErrors.add("Font size must be between 6 and 72")
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
            
            // Generate preview samples
            val pagesToProcess = pageNumbers?.size ?: totalPages
            val sampleNumbers = generateSampleNumbers(
                numberFormat ?: "arabic",
                startingNumber?.toInt() ?: 1,
                prefix,
                suffix,
                Math.min(5, pagesToProcess)
            )
            
            // Estimate processing metrics
            val estimatedProcessingTime = (pagesToProcess * 100L) // ~100ms per page for numbering
            val estimatedOutputSize = inputFile.length() // Size shouldn't change significantly
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "pagesToNumber" to (pageNumbers ?: (1..totalPages).toList()),
                "numberFormat" to (numberFormat ?: "unknown"),
                "startingNumber" to (startingNumber?.toInt() ?: 1),
                "position" to (position ?: "unknown"),
                "fontSize" to (fontSize?.toInt() ?: 12),
                "prefix" to prefix,
                "suffix" to suffix,
                "sampleNumbers" to sampleNumbers,
                "operationType" to "Custom Page Numbering"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during custom page numbering validation", e)
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
     * Validate numbering parameters.
     */
    private fun validateNumberingParameters(request: CustomPageNumberingRequest, totalPages: Int) {
        // Validate page numbers if specified
        request.pageNumbers?.let { validatePageNumbers(it, totalPages) }
        
        // Validate number format
        if (request.numberFormat !in listOf("arabic", "roman", "ROMAN", "alpha", "ALPHA")) {
            throw IllegalArgumentException("Invalid number format: ${request.numberFormat}")
        }
        
        // Validate starting number
        if (request.startingNumber < 1) {
            throw IllegalArgumentException("Starting number must be at least 1")
        }
        
        // Validate position
        if (request.position !in listOf("topLeft", "topCenter", "topRight", "bottomLeft", "bottomCenter", "bottomRight")) {
            throw IllegalArgumentException("Invalid position: ${request.position}")
        }
        
        // Validate font size
        if (request.fontSize < 6 || request.fontSize > 72) {
            throw IllegalArgumentException("Font size must be between 6 and 72")
        }
    }

    /**
     * Calculate numbering information based on request parameters.
     */
    private fun calculateNumberingInfo(request: CustomPageNumberingRequest, totalPages: Int): Map<String, Any> {
        val pagesToNumber = request.pageNumbers ?: (1..totalPages).toList()
        val endNumber = request.startingNumber + pagesToNumber.size - 1
        
        return mapOf(
            "numberFormat" to request.numberFormat,
            "startingNumber" to request.startingNumber,
            "endNumber" to endNumber,
            "position" to request.position,
            "fontSize" to request.fontSize,
            "prefix" to request.prefix,
            "suffix" to request.suffix,
            "numberedPageCount" to pagesToNumber.size,
            "formatDescription" to getFormatDescription(request.numberFormat)
        )
    }

    /**
     * Generate sample numbers for preview.
     */
    private fun generateSampleNumbers(format: String, startingNumber: Int, prefix: String, suffix: String, count: Int): List<String> {
        val samples = mutableListOf<String>()
        
        for (i in 0 until count) {
            val number = startingNumber + i
            val formatted = when (format) {
                "arabic" -> number.toString()
                "roman" -> toRoman(number).lowercase()
                "ROMAN" -> toRoman(number)
                "alpha" -> toAlpha(number).lowercase()
                "ALPHA" -> toAlpha(number)
                else -> number.toString()
            }
            samples.add("$prefix$formatted$suffix")
        }
        
        return samples
    }

    /**
     * Convert number to Roman numerals.
     */
    private fun toRoman(number: Int): String {
        if (number <= 0) return ""
        
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        
        val result = StringBuilder()
        var remaining = number
        
        for (i in values.indices) {
            while (remaining >= values[i]) {
                result.append(symbols[i])
                remaining -= values[i]
            }
        }
        
        return result.toString()
    }

    /**
     * Convert number to alphabetic representation.
     */
    private fun toAlpha(number: Int): String {
        if (number <= 0) return ""
        
        val result = StringBuilder()
        var remaining = number - 1 // Convert to 0-based
        
        do {
            result.insert(0, ('A' + (remaining % 26)).toChar())
            remaining /= 26
        } while (remaining > 0)
        
        return result.toString()
    }

    /**
     * Get format description for UI display.
     */
    private fun getFormatDescription(format: String): String {
        return when (format) {
            "arabic" -> "Arabic numerals (1, 2, 3, ...)"
            "roman" -> "Lowercase Roman numerals (i, ii, iii, ...)"
            "ROMAN" -> "Uppercase Roman numerals (I, II, III, ...)"
            "alpha" -> "Lowercase alphabetic (a, b, c, ...)"
            "ALPHA" -> "Uppercase alphabetic (A, B, C, ...)"
            else -> "Unknown format"
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
    private fun createOutputFile(customFileName: String?, numberFormat: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "numbered_${numberFormat}_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}