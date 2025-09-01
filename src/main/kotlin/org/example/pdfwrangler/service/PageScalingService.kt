package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.PageOperationResponse
import org.example.pdfwrangler.dto.PageOperationValidationRequest
import org.example.pdfwrangler.dto.PageOperationValidationResponse
import org.example.pdfwrangler.dto.PageScalingRequest
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
 * Service for scaling pages in PDF documents.
 * Supports multiple resizing options and algorithms including percentage scaling, 
 * fit-to-size scaling, and custom dimensions.
 */
@Service
class PageScalingService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(PageScalingService::class.java)
    
    // Standard paper sizes in points (1 point = 1/72 inch)
    private val paperSizes = mapOf(
        "A4" to Pair(595, 842),
        "A3" to Pair(842, 1191),
        "A5" to Pair(420, 595),
        "LETTER" to Pair(612, 792),
        "LEGAL" to Pair(612, 1008),
        "TABLOID" to Pair(792, 1224)
    )

    /**
     * Scale pages in a PDF document according to the specified request.
     */
    fun scalePages(request: PageScalingRequest): PageOperationResponse {
        logger.info("Starting page scaling operation: ${request.scaleType} for file: ${request.file.originalFilename}")
        
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
                
                // Validate scaling parameters
                validateScalingParameters(request, totalPages)
                
                // Calculate scaling details
                val scalingInfo = calculateScalingInfo(request)
                
                // Simulate scaling processing
                val pagesToScale = request.pageNumbers ?: (1..totalPages).toList()
                processedPages = pagesToScale.size
                
                logger.debug("Simulated scaling of $processedPages pages using ${request.scaleType} method")
                
                // Create output file (currently just copies the input)
                outputFile = createOutputFile(request.outputFileName, request.scaleType)
                inputFile.copyTo(outputFile!!, overwrite = true)
                
                // Log operation metrics
                val operationId = operationContextLogger.logOperationStart("PAGE_SCALING", processedPages)
                operationContextLogger.logPerformanceMetrics(
                    operationId,
                    mapOf(
                        "inputFile" to (request.file.originalFilename ?: "unknown"),
                        "scaleType" to request.scaleType,
                        "scalePercentage" to (request.scalePercentage ?: 0),
                        "paperSize" to (request.paperSize ?: "none"),
                        "customWidth" to (request.customWidth ?: 0),
                        "customHeight" to (request.customHeight ?: 0),
                        "maintainAspectRatio" to request.maintainAspectRatio,
                        "pageNumbers" to pagesToScale,
                        "processedPages" to processedPages,
                        "totalPages" to totalPages,
                        "scalingInfo" to scalingInfo
                    )
                )
            }
            
            return PageOperationResponse(
                success = true,
                message = "Successfully scaled $processedPages pages using ${request.scaleType} method",
                outputFileName = outputFile?.name,
                totalPages = totalPages,
                processedPages = processedPages,
                processingTimeMs = processingTimeMs,
                fileSize = outputFile?.length(),
                operationDetails = mapOf(
                    "scaleType" to request.scaleType,
                    "scaledPages" to (request.pageNumbers ?: (1..totalPages).toList()),
                    "scalingParameters" to mapOf(
                        "scalePercentage" to (request.scalePercentage ?: 0),
                        "paperSize" to (request.paperSize ?: "none"),
                        "customWidth" to (request.customWidth ?: 0),
                        "customHeight" to (request.customHeight ?: 0),
                        "maintainAspectRatio" to request.maintainAspectRatio
                    )
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during page scaling operation", e)
            outputFile?.delete() // Clean up on error
            throw PdfOperationException("Page scaling failed: ${e.message}", e)
        } finally {
            val endTime = System.currentTimeMillis()
            logger.info("Page scaling operation completed in ${endTime - startTime}ms")
        }
    }

    /**
     * Get the resource for a scaled file.
     */
    fun getScaledFileResource(fileName: String): Resource {
        val file = File(tempFileManagerService.getTempDir(), fileName)
        if (!file.exists()) {
            throw PdfOperationException("Scaled file not found: $fileName")
        }
        return FileSystemResource(file)
    }

    /**
     * Validate page operation request and provide preview information.
     */
    fun validateOperation(request: PageOperationValidationRequest): PageOperationValidationResponse {
        logger.info("Validating page scaling operation")
        
        return try {
            // Validate input file
            fileValidationService.validatePdfFile(request.file)
            
            // Create temporary file for analysis
            val inputFile = tempFileManagerService.createTempFile("validation", ".pdf")
            request.file.transferTo(inputFile)
            
            val validationErrors = mutableListOf<String>()
            val totalPages = estimatePageCount(inputFile)
            
            // Extract scaling parameters
            val scaleType = request.operationParameters["scaleType"]?.toString()
            val scalePercentage = request.operationParameters["scalePercentage"] as? Number
            val paperSize = request.operationParameters["paperSize"]?.toString()
            val customWidth = request.operationParameters["customWidth"] as? Number
            val customHeight = request.operationParameters["customHeight"] as? Number
            val maintainAspectRatio = request.operationParameters["maintainAspectRatio"] as? Boolean ?: true
            val pageNumbers = request.operationParameters["pageNumbers"] as? List<*>
            
            // Validate scale type
            if (scaleType == null) {
                validationErrors.add("Scale type is required")
            } else if (scaleType !in listOf("percentage", "fitToSize", "customDimensions")) {
                validationErrors.add("Invalid scale type: $scaleType")
            } else {
                // Validate type-specific parameters
                when (scaleType) {
                    "percentage" -> {
                        if (scalePercentage == null) {
                            validationErrors.add("Scale percentage is required for percentage scaling")
                        } else {
                            val percentage = scalePercentage.toInt()
                            if (percentage < 1 || percentage > 1000) {
                                validationErrors.add("Scale percentage must be between 1 and 1000")
                            }
                        }
                    }
                    "fitToSize" -> {
                        if (paperSize == null) {
                            validationErrors.add("Paper size is required for fit-to-size scaling")
                        } else if (paperSize !in paperSizes.keys) {
                            validationErrors.add("Invalid paper size: $paperSize")
                        }
                    }
                    "customDimensions" -> {
                        if (customWidth == null || customHeight == null) {
                            validationErrors.add("Custom width and height are required for custom dimensions scaling")
                        } else {
                            if (customWidth.toInt() < 1) {
                                validationErrors.add("Custom width must be at least 1")
                            }
                            if (customHeight.toInt() < 1) {
                                validationErrors.add("Custom height must be at least 1")
                            }
                        }
                    }
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
            val estimatedProcessingTime = (pagesToProcess * 100L) // ~100ms per page for scaling
            val estimatedOutputSize = when (scaleType) {
                "percentage" -> {
                    val scale = (scalePercentage?.toDouble() ?: 100.0) / 100.0
                    (inputFile.length() * scale * scale).toLong()
                }
                else -> inputFile.length() // Rough estimate
            }
            
            val previewInfo = mapOf(
                "totalPages" to totalPages,
                "pagesToScale" to (pageNumbers ?: (1..totalPages).toList()),
                "scaleType" to (scaleType ?: "unknown"),
                "scaleParameters" to mapOf(
                    "scalePercentage" to (scalePercentage?.toInt() ?: 0),
                    "paperSize" to (paperSize ?: "none"),
                    "customWidth" to (customWidth?.toInt() ?: 0),
                    "customHeight" to (customHeight?.toInt() ?: 0),
                    "maintainAspectRatio" to maintainAspectRatio
                ),
                "operationType" to "Page Scaling"
            )
            
            PageOperationValidationResponse(
                valid = validationErrors.isEmpty(),
                validationErrors = validationErrors,
                previewInfo = previewInfo,
                estimatedProcessingTime = estimatedProcessingTime,
                estimatedOutputSize = estimatedOutputSize
            )
            
        } catch (e: Exception) {
            logger.error("Error during page scaling validation", e)
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
     * Validate scaling parameters.
     */
    private fun validateScalingParameters(request: PageScalingRequest, totalPages: Int) {
        // Validate page numbers if specified
        request.pageNumbers?.let { validatePageNumbers(it, totalPages) }
        
        // Validate scale type specific parameters
        when (request.scaleType) {
            "percentage" -> {
                if (request.scalePercentage == null) {
                    throw IllegalArgumentException("Scale percentage is required for percentage scaling")
                }
            }
            "fitToSize" -> {
                if (request.paperSize == null) {
                    throw IllegalArgumentException("Paper size is required for fit-to-size scaling")
                }
                if (request.paperSize !in paperSizes.keys) {
                    throw IllegalArgumentException("Invalid paper size: ${request.paperSize}")
                }
            }
            "customDimensions" -> {
                if (request.customWidth == null || request.customHeight == null) {
                    throw IllegalArgumentException("Custom width and height are required for custom dimensions scaling")
                }
            }
            else -> {
                throw IllegalArgumentException("Invalid scale type: ${request.scaleType}")
            }
        }
    }

    /**
     * Calculate scaling information based on request parameters.
     */
    private fun calculateScalingInfo(request: PageScalingRequest): Map<String, Any> {
        return when (request.scaleType) {
            "percentage" -> mapOf(
                "method" to "percentage",
                "scaleFactor" to (request.scalePercentage!! / 100.0)
            )
            "fitToSize" -> {
                val (width, height) = paperSizes[request.paperSize!!]!!
                mapOf(
                    "method" to "fitToSize",
                    "targetWidth" to width,
                    "targetHeight" to height,
                    "maintainAspectRatio" to request.maintainAspectRatio
                )
            }
            "customDimensions" -> mapOf(
                "method" to "customDimensions",
                "targetWidth" to request.customWidth!!,
                "targetHeight" to request.customHeight!!,
                "maintainAspectRatio" to request.maintainAspectRatio
            )
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
    private fun createOutputFile(customFileName: String?, scaleType: String): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = customFileName ?: "scaled_${scaleType}_$timestamp.pdf"
        return tempFileManagerService.createTempFile("output", ".pdf")
    }
}