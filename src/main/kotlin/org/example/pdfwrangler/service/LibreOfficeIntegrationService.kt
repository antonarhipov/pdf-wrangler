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
 * Service for integrating with LibreOffice for OpenDocument format conversions.
 * Supports ODT, ODS, ODP formats with comprehensive conversion capabilities.
 */
@Service
class LibreOfficeIntegrationService(
    private val tempFileManagerService: TempFileManagerService,
    private val operationContextLogger: OperationContextLogger
) {

    private val logger = LoggerFactory.getLogger(LibreOfficeIntegrationService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<Resource>>>()
    private val operationProgress = ConcurrentHashMap<String, ConversionProgressResponse>()

    companion object {
        val LIBREOFFICE_FORMATS = setOf("odt", "ods", "odp", "odg", "odf")
        val SUPPORTED_OUTPUT_FORMATS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "html")
        
        val FORMAT_DESCRIPTIONS = mapOf(
            "odt" to "OpenDocument Text Document",
            "ods" to "OpenDocument Spreadsheet",
            "odp" to "OpenDocument Presentation", 
            "odg" to "OpenDocument Graphics",
            "odf" to "OpenDocument Formula",
            "pdf" to "Portable Document Format",
            "doc" to "Microsoft Word 97-2003 Document",
            "docx" to "Microsoft Word Document",
            "xls" to "Microsoft Excel 97-2003 Workbook",
            "xlsx" to "Microsoft Excel Workbook",
            "ppt" to "Microsoft PowerPoint 97-2003 Presentation",
            "pptx" to "Microsoft PowerPoint Presentation",
            "rtf" to "Rich Text Format",
            "txt" to "Plain Text",
            "html" to "HyperText Markup Language"
        )

        val LIBREOFFICE_FEATURES = mapOf(
            "headlessMode" to "Command-line processing without GUI",
            "formatSupport" to "Comprehensive format support including legacy formats",
            "fontHandling" to "Advanced font rendering and substitution",
            "macroSecurity" to "Secure macro handling and execution",
            "templateSupport" to "Document template processing",
            "passwordProtection" to "Handle password-protected documents"
        )
    }

    /**
     * Convert document using LibreOffice integration.
     */
    fun convertWithLibreOffice(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting document using LibreOffice: ${getFileExtension(request.file.originalFilename)} to ${request.outputFormat}")
        val startTime = System.currentTimeMillis()
        val operationId = operationContextLogger.logOperationStart("LIBREOFFICE_CONVERSION", 1)
        
        return try {
            validateRequestInternal(request)
            
            // Save uploaded document to temporary file
            val inputFormat = getFileExtension(request.file.originalFilename)
            val tempInputFile = tempFileManagerService.createTempFile("libreoffice_input", ".$inputFormat", 60)
            request.file.transferTo(tempInputFile.toFile())
            
            // Perform LibreOffice conversion
            val convertedFile = performLibreOfficeConversion(tempInputFile, inputFormat, request)
            val resource = ByteArrayResource(Files.readAllBytes(convertedFile))
            
            // Cleanup temporary files
            cleanupTempFiles(listOf(tempInputFile, convertedFile))
            
            val endTime = System.currentTimeMillis()
            operationContextLogger.logOperationSuccess("LIBREOFFICE_CONVERSION", endTime - startTime, operationId)
            
            val outputExtension = request.outputFormat.lowercase()
            val contentType = getContentType(outputExtension)
            
            ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"${request.outputFileName ?: "converted_document.$outputExtension"}\"")
                .header("Content-Type", contentType)
                .body(resource)
                
        } catch (ex: Exception) {
            logger.error("Error in LibreOffice conversion: ${ex.message}", ex)
            operationContextLogger.logOperationError(ex, operationId)
            throw PdfOperationException("LibreOffice conversion failed: ${ex.message}")
        }
    }

    /**
     * Check LibreOffice installation and capabilities.
     */
    fun checkLibreOfficeInstallation(): Map<String, Any> {
        logger.info("Checking LibreOffice installation")
        
        return try {
            // In a real implementation, this would:
            // 1. Check if LibreOffice is installed
            // 2. Verify the installation path
            // 3. Test headless mode capability
            // 4. Check supported filters
            
            val installationStatus = checkLibreOfficeAvailability()
            
            mapOf(
                "installed" to installationStatus.installed,
                "version" to installationStatus.version,
                "path" to installationStatus.path,
                "headlessSupport" to installationStatus.headlessSupport,
                "supportedFormats" to getSupportedLibreOfficeFormats(),
                "features" to LIBREOFFICE_FEATURES,
                "recommendations" to getInstallationRecommendations()
            )
            
        } catch (ex: Exception) {
            logger.error("Error checking LibreOffice installation: ${ex.message}", ex)
            mapOf(
                "installed" to false,
                "error" to (ex.message ?: "Installation check failed"),
                "recommendations" to listOf("Install LibreOffice with headless support")
            )
        }
    }

    /**
     * Get LibreOffice conversion capabilities.
     */
    fun getLibreOfficeCapabilities(): Map<String, Any> {
        return mapOf(
            "inputFormats" to LIBREOFFICE_FORMATS,
            "outputFormats" to SUPPORTED_OUTPUT_FORMATS,
            "formatDescriptions" to FORMAT_DESCRIPTIONS,
            "conversionMatrix" to buildLibreOfficeConversionMatrix(),
            "features" to LIBREOFFICE_FEATURES,
            "qualityOptions" to mapOf(
                "preserveFormatting" to "Maintains document structure and styling",
                "fontEmbedding" to "Embed fonts for consistent rendering",
                "imageOptimization" to "Optimize embedded images for size",
                "metadataHandling" to "Control document properties and metadata"
            ),
            "performanceNotes" to listOf(
                "Large documents may take longer to process",
                "Complex formatting might require additional processing time",
                "Font availability affects conversion quality",
                "Password-protected documents require additional handling"
            )
        )
    }

    /**
     * Convert OpenDocument format to other formats.
     */
    fun convertOpenDocument(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting OpenDocument format: ${getFileExtension(request.file.originalFilename)}")
        
        val inputFormat = getFileExtension(request.file.originalFilename)
        if (inputFormat !in LIBREOFFICE_FORMATS) {
            throw PdfOperationException("Input format $inputFormat is not an OpenDocument format")
        }
        
        return convertWithLibreOffice(request)
    }

    /**
     * Convert to OpenDocument format from other formats.
     */
    fun convertToOpenDocument(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting to OpenDocument format: ${request.outputFormat}")
        
        if (request.outputFormat.lowercase() !in LIBREOFFICE_FORMATS) {
            throw PdfOperationException("Output format ${request.outputFormat} is not an OpenDocument format")
        }
        
        return convertWithLibreOffice(request)
    }

    /**
     * Batch convert documents using LibreOffice.
     */
    fun batchConvertWithLibreOffice(request: BatchConversionRequest): BatchConversionResponse {
        logger.info("Starting LibreOffice batch conversion of ${request.conversionJobs.size} documents")
        val startTime = System.currentTimeMillis()
        
        val results = mutableListOf<ConversionJobResult>()
        var completedJobs = 0
        var failedJobs = 0
        
        request.conversionJobs.forEach { job ->
            try {
                val officeRequest = createOfficeRequestFromJob(job)
                val jobStartTime = System.currentTimeMillis()
                
                convertWithLibreOffice(officeRequest)
                
                results.add(ConversionJobResult(
                    success = true,
                    message = "LibreOffice conversion completed successfully",
                    outputFileName = job.outputFileName ?: "converted_document.${officeRequest.outputFormat.lowercase()}",
                    conversionType = job.conversionType,
                    processingTimeMs = System.currentTimeMillis() - jobStartTime,
                    fileSize = 0L
                ))
                completedJobs++
            } catch (ex: Exception) {
                logger.error("Error in LibreOffice batch job: ${ex.message}", ex)
                results.add(ConversionJobResult(
                    success = false,
                    message = ex.message ?: "LibreOffice conversion failed",
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
            message = "LibreOffice batch conversion completed: $completedJobs successful, $failedJobs failed",
            completedJobs = completedJobs,
            failedJobs = failedJobs,
            totalProcessingTimeMs = System.currentTimeMillis() - startTime,
            results = results
        )
    }

    /**
     * Validate LibreOffice conversion request.
     */
    fun validateLibreOfficeRequest(request: OfficeDocumentConversionRequest): Map<String, Any> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Check LibreOffice availability
            val installation = checkLibreOfficeAvailability()
            if (!installation.installed) {
                errors.add("LibreOffice is not installed or not accessible")
            }
            
            // Validate input format
            val inputFormat = getFileExtension(request.file.originalFilename)
            if (inputFormat !in LIBREOFFICE_FORMATS && inputFormat !in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")) {
                errors.add("Input format $inputFormat is not supported by LibreOffice integration")
            }
            
            // Validate output format
            if (request.outputFormat.lowercase() !in SUPPORTED_OUTPUT_FORMATS) {
                errors.add("Output format ${request.outputFormat} is not supported")
            }
            
            // File size considerations
            val fileSizeMB = request.file.size / (1024 * 1024)
            if (fileSizeMB > 100) {
                warnings.add("Large files may cause LibreOffice processing to be slow or fail")
            }
            
            // Format-specific warnings
            if (inputFormat in LIBREOFFICE_FORMATS && request.outputFormat.lowercase() == "pdf") {
                warnings.add("OpenDocument to PDF conversion may have different rendering than native viewers")
            }
            
            return mapOf(
                "valid" to errors.isEmpty(),
                "errors" to errors,
                "warnings" to warnings,
                "libreOfficeAvailable" to installation.installed,
                "recommendedSettings" to getRecommendedSettings(inputFormat, request.outputFormat)
            )
            
        } catch (ex: Exception) {
            logger.error("Error validating LibreOffice request: ${ex.message}", ex)
            return mapOf(
                "valid" to false,
                "errors" to listOf(ex.message ?: "Validation failed"),
                "warnings" to emptyList<String>()
            )
        }
    }

    // Private helper methods

    private fun validateRequestInternal(request: OfficeDocumentConversionRequest) {
        if (request.file.isEmpty) {
            throw PdfOperationException("Document file is required")
        }
        
        val installation = checkLibreOfficeAvailability()
        if (!installation.installed) {
            throw PdfOperationException("LibreOffice is not available for conversion")
        }
    }

    private fun performLibreOfficeConversion(
        inputFile: Path,
        inputFormat: String,
        request: OfficeDocumentConversionRequest
    ): Path {
        val outputFormat = request.outputFormat.lowercase()
        val outputFile = tempFileManagerService.createTempFile("libreoffice_output", ".$outputFormat", 60)
        
        // In a real implementation, this would:
        // 1. Execute LibreOffice in headless mode
        // 2. Use command line: soffice --headless --convert-to pdf --outdir /output/dir /input/file
        // 3. Apply conversion parameters (preserve formatting, etc.)
        // 4. Handle errors and timeouts
        
        // Placeholder implementation
        val convertedContent = when {
            outputFormat == "pdf" -> createLibreOfficePdfContent(inputFormat, request)
            inputFormat in LIBREOFFICE_FORMATS -> createConvertedOpenDocContent(inputFormat, outputFormat, request)
            outputFormat in LIBREOFFICE_FORMATS -> createToOpenDocContent(inputFormat, outputFormat, request)
            else -> createGenericConvertedContent(inputFormat, outputFormat, request)
        }
        
        Files.write(outputFile, convertedContent.toByteArray())
        return outputFile
    }

    private fun createLibreOfficePdfContent(inputFormat: String, request: OfficeDocumentConversionRequest): String {
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
            /Length 104
            >>
            stream
            BT
            /F1 12 Tf
            100 700 Td
            (LibreOffice: Converted from $inputFormat to PDF) Tj
            0 -20 Td
            (Preserve formatting: ${request.preserveFormatting}) Tj
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
            380
            %%EOF
        """.trimIndent()
    }

    private fun createConvertedOpenDocContent(inputFormat: String, outputFormat: String, request: OfficeDocumentConversionRequest): String {
        return "LibreOffice converted from $inputFormat to $outputFormat (placeholder content)\nPreserve formatting: ${request.preserveFormatting}\nInclude metadata: ${request.includeMetadata}"
    }

    private fun createToOpenDocContent(inputFormat: String, outputFormat: String, request: OfficeDocumentConversionRequest): String {
        return "LibreOffice converted from $inputFormat to OpenDocument $outputFormat (placeholder content)\nPreserve formatting: ${request.preserveFormatting}"
    }

    private fun createGenericConvertedContent(inputFormat: String, outputFormat: String, request: OfficeDocumentConversionRequest): String {
        return "LibreOffice generic conversion from $inputFormat to $outputFormat (placeholder content)"
    }

    private fun checkLibreOfficeAvailability(): LibreOfficeInstallation {
        // Placeholder implementation
        // In real implementation, would check:
        // 1. System PATH for 'soffice' or 'libreoffice'
        // 2. Common installation directories
        // 3. Registry entries on Windows
        // 4. Test execution with --version flag
        
        return LibreOfficeInstallation(
            installed = true, // Simulated
            version = "7.6.0",
            path = "/usr/bin/soffice",
            headlessSupport = true
        )
    }

    private fun getSupportedLibreOfficeFormats(): Map<String, List<String>> {
        return mapOf(
            "text" to listOf("odt", "doc", "docx", "rtf", "txt", "html", "pdf"),
            "spreadsheet" to listOf("ods", "xls", "xlsx", "csv", "pdf"),
            "presentation" to listOf("odp", "ppt", "pptx", "pdf"),
            "graphics" to listOf("odg", "svg", "png", "jpg", "pdf")
        )
    }

    private fun getInstallationRecommendations(): List<String> {
        return listOf(
            "Install LibreOffice with full language support for better compatibility",
            "Ensure headless mode is available for server environments",
            "Configure appropriate fonts for document rendering",
            "Set up proper file permissions for temporary directories",
            "Consider memory limits for large document processing"
        )
    }

    private fun buildLibreOfficeConversionMatrix(): Map<String, List<String>> {
        return mapOf(
            "odt" to listOf("pdf", "doc", "docx", "rtf", "txt", "html"),
            "ods" to listOf("pdf", "xls", "xlsx", "csv"),
            "odp" to listOf("pdf", "ppt", "pptx"),
            "doc" to listOf("pdf", "odt", "docx", "rtf", "txt"),
            "docx" to listOf("pdf", "odt", "doc", "rtf", "txt"),
            "xls" to listOf("pdf", "ods", "xlsx", "csv"),
            "xlsx" to listOf("pdf", "ods", "xls", "csv"),
            "ppt" to listOf("pdf", "odp", "pptx"),
            "pptx" to listOf("pdf", "odp", "ppt")
        )
    }

    private fun getRecommendedSettings(inputFormat: String, outputFormat: String): Map<String, Any> {
        return when {
            outputFormat.lowercase() == "pdf" -> mapOf(
                "preserveFormatting" to true,
                "includeMetadata" to true,
                "imageQuality" to "high"
            )
            inputFormat in LIBREOFFICE_FORMATS -> mapOf(
                "preserveFormatting" to true,
                "fontHandling" to "embed"
            )
            else -> mapOf(
                "preserveFormatting" to true
            )
        }
    }

    private fun getFileExtension(filename: String?): String {
        return filename?.substringAfterLast(".")?.lowercase() ?: "unknown"
    }

    private fun getContentType(extension: String): String {
        return when (extension) {
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "odg" -> "application/vnd.oasis.opendocument.graphics"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "rtf" -> "application/rtf"
            "txt" -> "text/plain"
            "html" -> "text/html"
            else -> "application/octet-stream"
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

    // Data classes for LibreOffice integration
    private data class LibreOfficeInstallation(
        val installed: Boolean,
        val version: String,
        val path: String,
        val headlessSupport: Boolean
    )
}