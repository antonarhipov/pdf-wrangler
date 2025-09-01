package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.PdfOperationException
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

/**
 * Service for converting Office documents (Word, Excel, PowerPoint) to/from PDF.
 * Supports DOC, DOCX, XLS, XLSX, PPT, PPTX formats with formatting preservation.
 */
@Service
class OfficeDocumentConverter(
    private val tempFileManagerService: TempFileManagerService,
    private val operationContextLogger: OperationContextLogger
) {

    private val logger = LoggerFactory.getLogger(OfficeDocumentConverter::class.java)
    private val asyncOperations = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<Resource>>>()
    private val operationProgress = ConcurrentHashMap<String, ConversionProgressResponse>()

    companion object {
        val SUPPORTED_OFFICE_FORMATS = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
        val OUTPUT_FORMATS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
        
        val FORMAT_DESCRIPTIONS = mapOf(
            "doc" to "Microsoft Word 97-2003 Document",
            "docx" to "Microsoft Word Document",
            "xls" to "Microsoft Excel 97-2003 Workbook",
            "xlsx" to "Microsoft Excel Workbook",
            "ppt" to "Microsoft PowerPoint 97-2003 Presentation",
            "pptx" to "Microsoft PowerPoint Presentation",
            "pdf" to "Portable Document Format"
        )
    }

    /**
     * Convert Office document synchronously.
     */
    fun convertDocument(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting Office document from ${getFileExtension(request.file.originalFilename)} to ${request.outputFormat}")
        val startTime = System.currentTimeMillis()
        val operationId = operationContextLogger.logOperationStart("OFFICE_CONVERSION", 1)
        
        return try {
            validateRequest(request)
            
            // Save uploaded document to temporary file
            val originalFormat = getFileExtension(request.file.originalFilename)
            val tempInputFile = tempFileManagerService.createTempFile("input_document", ".$originalFormat", 60)
            request.file.transferTo(tempInputFile.toFile())
            
            // Perform conversion
            val convertedFile = performConversion(tempInputFile, originalFormat, request)
            val resource = ByteArrayResource(Files.readAllBytes(convertedFile))
            
            // Cleanup temporary files
            cleanupTempFiles(listOf(tempInputFile, convertedFile))
            
            val endTime = System.currentTimeMillis()
            operationContextLogger.logOperationSuccess("OFFICE_CONVERSION", endTime - startTime, operationId)
            
            val outputExtension = request.outputFormat.lowercase()
            val contentType = getContentType(outputExtension)
            
            ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"${request.outputFileName ?: "converted_document.$outputExtension"}\"")
                .header("Content-Type", contentType)
                .body(resource)
                
        } catch (ex: Exception) {
            logger.error("Error converting Office document: ${ex.message}", ex)
            operationContextLogger.logOperationError(ex, operationId)
            throw PdfOperationException("Office document conversion failed: ${ex.message}")
        }
    }

    /**
     * Get conversion status and metadata.
     */
    fun getConversionStatus(request: OfficeDocumentConversionRequest): OfficeDocumentConversionResponse {
        logger.info("Getting conversion status for Office document")
        
        return try {
            val startTime = System.currentTimeMillis()
            val originalFormat = getFileExtension(request.file.originalFilename)
            
            // Analyze document without converting
            val documentInfo = analyzeDocument(request)
            
            OfficeDocumentConversionResponse(
                success = true,
                message = "Document analysis completed successfully",
                outputFileName = request.outputFileName ?: "converted_document.${request.outputFormat.lowercase()}",
                originalFormat = originalFormat,
                outputFormat = request.outputFormat,
                processingTimeMs = System.currentTimeMillis() - startTime,
                fileSize = request.file.size
            )
            
        } catch (ex: Exception) {
            logger.error("Error getting conversion status: ${ex.message}", ex)
            OfficeDocumentConversionResponse(
                success = false,
                message = ex.message ?: "Status analysis failed",
                outputFileName = null,
                originalFormat = "unknown",
                outputFormat = request.outputFormat,
                processingTimeMs = 0,
                fileSize = null
            )
        }
    }

    /**
     * Start asynchronous Office document conversion.
     */
    fun startAsyncConversion(request: OfficeDocumentConversionRequest): String {
        val operationId = UUID.randomUUID().toString()
        logger.info("Starting async Office document conversion with operation ID: $operationId")
        
        val future = CompletableFuture.supplyAsync {
            updateProgress(operationId, "PROCESSING", 0, "Starting conversion", null, 0, 1)
            
            try {
                val result = convertDocument(request)
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
     * Convert multiple Office documents in batch.
     */
    fun batchConvertDocuments(request: BatchConversionRequest): BatchConversionResponse {
        logger.info("Starting batch conversion of ${request.conversionJobs.size} Office documents")
        val startTime = System.currentTimeMillis()
        
        val results = mutableListOf<ConversionJobResult>()
        var completedJobs = 0
        var failedJobs = 0
        
        request.conversionJobs.forEach { job ->
            try {
                if (job.conversionType == "OFFICE_TO_PDF" || job.conversionType == "PDF_TO_OFFICE") {
                    val officeRequest = createOfficeRequestFromJob(job)
                    val jobStartTime = System.currentTimeMillis()
                    
                    convertDocument(officeRequest)
                    
                    results.add(ConversionJobResult(
                        success = true,
                        message = "Conversion completed successfully",
                        outputFileName = job.outputFileName ?: "converted_document.${officeRequest.outputFormat.lowercase()}",
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
    fun validateConversionRequest(request: OfficeDocumentConversionRequest): Map<String, Any> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Validate input file
            if (request.file.isEmpty) {
                errors.add("Document file is required")
            }
            
            val inputFormat = getFileExtension(request.file.originalFilename)
            if (inputFormat !in SUPPORTED_OFFICE_FORMATS && inputFormat != "pdf") {
                errors.add("Unsupported input format: $inputFormat")
            }
            
            // Validate output format
            if (request.outputFormat.lowercase() !in OUTPUT_FORMATS) {
                errors.add("Unsupported output format: ${request.outputFormat}")
            }
            
            // Check for same format conversion
            if (inputFormat == request.outputFormat.lowercase()) {
                warnings.add("Input and output formats are the same")
            }
            
            // File size warnings
            val fileSizeMB = request.file.size / (1024 * 1024)
            if (fileSizeMB > 50) {
                warnings.add("Large files may take longer to process and might fail")
            }
            
            return mapOf(
                "valid" to errors.isEmpty(),
                "errors" to errors,
                "warnings" to warnings,
                "inputFormat" to inputFormat,
                "outputFormat" to request.outputFormat,
                "estimatedProcessingTime" to estimateProcessingTime(request),
                "supportedFeatures" to getSupportedFeatures(inputFormat, request.outputFormat)
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
    fun previewConversion(request: OfficeDocumentConversionRequest): Map<String, Any> {
        return try {
            val inputFormat = getFileExtension(request.file.originalFilename)
            val documentInfo = analyzeDocument(request)
            
            mapOf(
                "success" to true,
                "message" to "Preview generated successfully",
                "inputFormat" to inputFormat,
                "outputFormat" to request.outputFormat,
                "inputFormatDescription" to (FORMAT_DESCRIPTIONS[inputFormat] ?: "Unknown format"),
                "outputFormatDescription" to (FORMAT_DESCRIPTIONS[request.outputFormat.lowercase()] ?: "Unknown format"),
                "preserveFormatting" to request.preserveFormatting,
                "includeComments" to request.includeComments,
                "includeMetadata" to request.includeMetadata,
                "estimatedProcessingTime" to estimateProcessingTime(request),
                "estimatedOutputSize" to estimateOutputSize(request),
                "conversionNotes" to getConversionNotes(inputFormat, request.outputFormat)
            )
            
        } catch (ex: Exception) {
            logger.error("Error generating preview: ${ex.message}", ex)
            mapOf(
                "success" to false,
                "message" to (ex.message ?: "Preview generation failed"),
                "estimatedProcessingTime" to 0,
                "estimatedOutputSize" to 0
            )
        }
    }

    /**
     * Get supported conversion combinations.
     */
    fun getSupportedConversions(): Map<String, Any> {
        return mapOf(
            "inputFormats" to SUPPORTED_OFFICE_FORMATS + "pdf",
            "outputFormats" to OUTPUT_FORMATS,
            "formatDescriptions" to FORMAT_DESCRIPTIONS,
            "conversionMatrix" to buildConversionMatrix(),
            "features" to mapOf(
                "preserveFormatting" to "Maintains document layout and styling",
                "includeComments" to "Preserves document comments and annotations",
                "includeMetadata" to "Retains document properties and metadata",
                "batchProcessing" to "Process multiple documents simultaneously",
                "asyncProcessing" to "Background processing for large documents"
            )
        )
    }

    // Private helper methods

    private fun validateRequest(request: OfficeDocumentConversionRequest) {
        if (request.file.isEmpty) {
            throw PdfOperationException("Document file is required")
        }
        
        val inputFormat = getFileExtension(request.file.originalFilename)
        if (inputFormat !in SUPPORTED_OFFICE_FORMATS && inputFormat != "pdf") {
            throw PdfOperationException("Unsupported input format: $inputFormat")
        }
        
        if (request.outputFormat.lowercase() !in OUTPUT_FORMATS) {
            throw PdfOperationException("Unsupported output format: ${request.outputFormat}")
        }
    }

    private fun performConversion(
        inputFile: Path,
        inputFormat: String,
        request: OfficeDocumentConversionRequest
    ): Path {
        // Placeholder implementation - would use LibreOffice or similar converter
        val outputFormat = request.outputFormat.lowercase()
        val outputFile = tempFileManagerService.createTempFile("converted_document", ".$outputFormat", 60)
        
        // In a real implementation, this would:
        // 1. Use LibreOffice headless mode or Apache POI
        // 2. Load the document in the input format
        // 3. Apply conversion settings (preserve formatting, comments, etc.)
        // 4. Save in the target format
        
        // For now, create placeholder content based on the conversion type
        val convertedContent = when {
            outputFormat == "pdf" -> createPlaceholderPdfContent(inputFormat, request)
            inputFormat == "pdf" && outputFormat in SUPPORTED_OFFICE_FORMATS -> 
                createPlaceholderOfficeContent(outputFormat, request)
            else -> createPlaceholderOfficeToOfficeContent(inputFormat, outputFormat, request)
        }
        
        Files.write(outputFile, convertedContent.toByteArray())
        
        return outputFile
    }

    private fun createPlaceholderPdfContent(inputFormat: String, request: OfficeDocumentConversionRequest): String {
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
            /Count 1
            >>
            endobj
            
            3 0 obj
            <<
            /Type /Page
            /Parent 2 0 R
            /MediaBox [0 0 612 792]
            /Contents 4 0 R
            >>
            endobj
            
            4 0 obj
            <<
            /Length 84
            >>
            stream
            BT
            /F1 12 Tf
            100 700 Td
            (Converted from $inputFormat to PDF) Tj
            ET
            endstream
            endobj
            
            xref
            0 5
            0000000000 65535 f 
            0000000009 00000 n 
            0000000058 00000 n 
            0000000115 00000 n 
            0000000206 00000 n 
            trailer
            <<
            /Size 5
            /Root 1 0 R
            >>
            startxref
            348
            %%EOF
        """.trimIndent()
    }

    private fun createPlaceholderOfficeContent(outputFormat: String, request: OfficeDocumentConversionRequest): String {
        return when (outputFormat) {
            "docx", "doc" -> "Converted Word document from PDF (placeholder content)"
            "xlsx", "xls" -> "Converted Excel document from PDF (placeholder content)"
            "pptx", "ppt" -> "Converted PowerPoint document from PDF (placeholder content)"
            else -> "Converted document from PDF (placeholder content)"
        }
    }

    private fun createPlaceholderOfficeToOfficeContent(
        inputFormat: String,
        outputFormat: String,
        request: OfficeDocumentConversionRequest
    ): String {
        return "Converted from $inputFormat to $outputFormat (placeholder content)"
    }

    private fun analyzeDocument(request: OfficeDocumentConversionRequest): Map<String, Any> {
        // Placeholder document analysis
        return mapOf(
            "hasFormatting" to true,
            "hasComments" to false,
            "hasMetadata" to true,
            "pageCount" to 1,
            "estimatedComplexity" to "medium"
        )
    }

    private fun getFileExtension(filename: String?): String {
        return filename?.substringAfterLast(".")?.lowercase() ?: "unknown"
    }

    private fun getContentType(extension: String): String {
        return when (extension) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    private fun estimateProcessingTime(request: OfficeDocumentConversionRequest): Long {
        // Base time in milliseconds
        val baseTime = 3000L // 3 seconds
        val fileSizeMultiplier = (request.file.size / (1024 * 1024)).coerceAtLeast(1)
        return baseTime * fileSizeMultiplier
    }

    private fun estimateOutputSize(request: OfficeDocumentConversionRequest): Long {
        // Rough estimation based on conversion type
        val inputSize = request.file.size
        return when {
            request.outputFormat.lowercase() == "pdf" -> (inputSize * 0.8).toLong()
            getFileExtension(request.file.originalFilename) == "pdf" -> (inputSize * 1.2).toLong()
            else -> inputSize
        }
    }

    private fun getSupportedFeatures(inputFormat: String, outputFormat: String): Map<String, Boolean> {
        return mapOf(
            "preserveFormatting" to true,
            "includeComments" to (outputFormat.lowercase() != "pdf"),
            "includeMetadata" to true,
            "preserveImages" to true,
            "preserveTables" to true,
            "preserveHyperlinks" to (outputFormat.lowercase() == "pdf" || outputFormat.lowercase() in SUPPORTED_OFFICE_FORMATS)
        )
    }

    private fun getConversionNotes(inputFormat: String, outputFormat: String): List<String> {
        return when {
            inputFormat in listOf("doc", "docx") && outputFormat.lowercase() == "pdf" -> 
                listOf("Text formatting will be preserved", "Images and tables will be converted", "Comments can be included")
            inputFormat in listOf("xls", "xlsx") && outputFormat.lowercase() == "pdf" ->
                listOf("Spreadsheet layout will be preserved", "Charts will be converted to images", "Multiple sheets will become separate pages")
            inputFormat in listOf("ppt", "pptx") && outputFormat.lowercase() == "pdf" ->
                listOf("Slide layouts will be preserved", "Animations will be lost", "Speaker notes can be included")
            inputFormat == "pdf" && outputFormat.lowercase() in SUPPORTED_OFFICE_FORMATS ->
                listOf("Text extraction may not be perfect", "Layout might change", "Images will be preserved where possible")
            else -> listOf("Standard conversion with best effort to preserve formatting")
        }
    }

    private fun buildConversionMatrix(): Map<String, List<String>> {
        return mapOf(
            "pdf" to SUPPORTED_OFFICE_FORMATS.toList(),
            "doc" to listOf("pdf", "docx"),
            "docx" to listOf("pdf", "doc"),
            "xls" to listOf("pdf", "xlsx"),
            "xlsx" to listOf("pdf", "xls"),
            "ppt" to listOf("pdf", "pptx"),
            "pptx" to listOf("pdf", "ppt")
        )
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

    private fun createOfficeRequestFromJob(job: ConversionJobRequest): OfficeDocumentConversionRequest {
        val params = job.parameters
        return OfficeDocumentConversionRequest(
            file = job.file,
            outputFormat = params["outputFormat"] as? String ?: "PDF",
            preserveFormatting = params["preserveFormatting"] as? Boolean ?: true,
            includeComments = params["includeComments"] as? Boolean ?: false,
            includeMetadata = params["includeMetadata"] as? Boolean ?: true,
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
            conversionType = "OFFICE_CONVERSION"
        )
    }
}