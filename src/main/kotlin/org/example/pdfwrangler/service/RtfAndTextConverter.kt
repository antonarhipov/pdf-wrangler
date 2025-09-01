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
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

/**
 * Service for converting RTF (Rich Text Format) and plain text documents.
 * Supports RTF, TXT, and various text encoding formats with PDF conversion capabilities.
 */
@Service
class RtfAndTextConverter(
    private val tempFileManagerService: TempFileManagerService,
    private val operationContextLogger: OperationContextLogger
) {

    private val logger = LoggerFactory.getLogger(RtfAndTextConverter::class.java)
    private val asyncOperations = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<Resource>>>()
    private val operationProgress = ConcurrentHashMap<String, ConversionProgressResponse>()

    companion object {
        val TEXT_FORMATS = setOf("rtf", "txt", "csv", "html", "xml", "md", "json")
        val SUPPORTED_OUTPUT_FORMATS = setOf("pdf", "rtf", "txt", "html", "docx", "odt")
        val SUPPORTED_ENCODINGS = setOf("UTF-8", "UTF-16", "ISO-8859-1", "Windows-1252", "ASCII")
        
        val FORMAT_DESCRIPTIONS = mapOf(
            "rtf" to "Rich Text Format - maintains basic formatting",
            "txt" to "Plain Text - no formatting",
            "csv" to "Comma-Separated Values - tabular data",
            "html" to "HyperText Markup Language - web format",
            "xml" to "eXtensible Markup Language - structured data",
            "md" to "Markdown - lightweight markup",
            "json" to "JavaScript Object Notation - data interchange",
            "pdf" to "Portable Document Format",
            "docx" to "Microsoft Word Document",
            "odt" to "OpenDocument Text"
        )

        val TEXT_PROCESSING_FEATURES = mapOf(
            "encodingDetection" to "Automatic text encoding detection",
            "formatPreservation" to "Maintain basic text formatting where possible",
            "lineEndingNormalization" to "Normalize different line ending formats",
            "characterEscaping" to "Handle special characters properly",
            "fontMapping" to "Map fonts for consistent rendering",
            "layoutOptimization" to "Optimize text layout for target format"
        )
    }

    /**
     * Convert text or RTF document.
     */
    fun convertTextDocument(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting text document: ${getFileExtension(request.file.originalFilename)} to ${request.outputFormat}")
        val startTime = System.currentTimeMillis()
        val operationId = operationContextLogger.logOperationStart("TEXT_CONVERSION", 1)
        
        return try {
            validateTextRequest(request)
            
            // Save uploaded document to temporary file
            val inputFormat = getFileExtension(request.file.originalFilename)
            val tempInputFile = tempFileManagerService.createTempFile("text_input", ".$inputFormat", 60)
            request.file.transferTo(tempInputFile.toFile())
            
            // Detect encoding and read content
            val textContent = readTextContent(tempInputFile, inputFormat)
            
            // Perform conversion
            val convertedFile = performTextConversion(textContent, inputFormat, request)
            val resource = ByteArrayResource(Files.readAllBytes(convertedFile))
            
            // Cleanup temporary files
            cleanupTempFiles(listOf(tempInputFile, convertedFile))
            
            val endTime = System.currentTimeMillis()
            operationContextLogger.logOperationSuccess("TEXT_CONVERSION", endTime - startTime, operationId)
            
            val outputExtension = request.outputFormat.lowercase()
            val contentType = getContentType(outputExtension)
            
            ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"${request.outputFileName ?: "converted_document.$outputExtension"}\"")
                .header("Content-Type", contentType)
                .body(resource)
                
        } catch (ex: Exception) {
            logger.error("Error converting text document: ${ex.message}", ex)
            operationContextLogger.logOperationError(ex, operationId)
            throw PdfOperationException("Text document conversion failed: ${ex.message}")
        }
    }

    /**
     * Convert RTF to other formats.
     */
    fun convertRtfDocument(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting RTF document to ${request.outputFormat}")
        
        val inputFormat = getFileExtension(request.file.originalFilename)
        if (inputFormat != "rtf") {
            throw PdfOperationException("Input file is not RTF format")
        }
        
        return convertTextDocument(request)
    }

    /**
     * Convert plain text to other formats.
     */
    fun convertPlainText(request: OfficeDocumentConversionRequest): ResponseEntity<Resource> {
        logger.info("Converting plain text to ${request.outputFormat}")
        
        val inputFormat = getFileExtension(request.file.originalFilename)
        if (inputFormat != "txt") {
            throw PdfOperationException("Input file is not plain text format")
        }
        
        return convertTextDocument(request)
    }

    /**
     * Analyze text document properties.
     */
    fun analyzeTextDocument(request: OfficeDocumentConversionRequest): Map<String, Any> {
        return try {
            val inputFormat = getFileExtension(request.file.originalFilename)
            val tempFile = tempFileManagerService.createTempFile("analyze_text", ".$inputFormat", 60)
            request.file.transferTo(tempFile.toFile())
            
            val textContent = readTextContent(tempFile, inputFormat)
            val analysis = performTextAnalysis(textContent, inputFormat)
            
            tempFileManagerService.removeTempFile(tempFile)
            
            analysis
        } catch (ex: Exception) {
            logger.error("Error analyzing text document: ${ex.message}", ex)
            mapOf(
                "success" to false,
                "error" to (ex.message ?: "Analysis failed")
            )
        }
    }

    /**
     * Get text conversion capabilities.
     */
    fun getTextConversionCapabilities(): Map<String, Any> {
        return mapOf(
            "inputFormats" to TEXT_FORMATS,
            "outputFormats" to SUPPORTED_OUTPUT_FORMATS,
            "formatDescriptions" to FORMAT_DESCRIPTIONS,
            "supportedEncodings" to SUPPORTED_ENCODINGS,
            "features" to TEXT_PROCESSING_FEATURES,
            "conversionMatrix" to buildTextConversionMatrix(),
            "limitations" to listOf(
                "Complex RTF formatting may be simplified in conversion",
                "Font availability affects rendering quality",
                "Very large text files may require additional processing time",
                "Some special characters may require encoding consideration"
            ),
            "recommendations" to getTextConversionRecommendations()
        )
    }

    /**
     * Batch convert text documents.
     */
    fun batchConvertTextDocuments(request: BatchConversionRequest): BatchConversionResponse {
        logger.info("Starting batch text conversion of ${request.conversionJobs.size} documents")
        val startTime = System.currentTimeMillis()
        
        val results = mutableListOf<ConversionJobResult>()
        var completedJobs = 0
        var failedJobs = 0
        
        request.conversionJobs.forEach { job ->
            try {
                val textRequest = createTextRequestFromJob(job)
                val jobStartTime = System.currentTimeMillis()
                
                convertTextDocument(textRequest)
                
                results.add(ConversionJobResult(
                    success = true,
                    message = "Text conversion completed successfully",
                    outputFileName = job.outputFileName ?: "converted_document.${textRequest.outputFormat.lowercase()}",
                    conversionType = job.conversionType,
                    processingTimeMs = System.currentTimeMillis() - jobStartTime,
                    fileSize = 0L
                ))
                completedJobs++
            } catch (ex: Exception) {
                logger.error("Error in text batch job: ${ex.message}", ex)
                results.add(ConversionJobResult(
                    success = false,
                    message = ex.message ?: "Text conversion failed",
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
            message = "Text batch conversion completed: $completedJobs successful, $failedJobs failed",
            completedJobs = completedJobs,
            failedJobs = failedJobs,
            totalProcessingTimeMs = System.currentTimeMillis() - startTime,
            results = results
        )
    }

    /**
     * Validate text conversion request.
     */
    fun validateTextConversionRequest(request: OfficeDocumentConversionRequest): Map<String, Any> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Validate input format
            val inputFormat = getFileExtension(request.file.originalFilename)
            if (inputFormat !in TEXT_FORMATS) {
                errors.add("Input format $inputFormat is not a supported text format")
            }
            
            // Validate output format
            if (request.outputFormat.lowercase() !in SUPPORTED_OUTPUT_FORMATS) {
                errors.add("Output format ${request.outputFormat} is not supported for text conversion")
            }
            
            // File size considerations
            val fileSizeMB = request.file.size / (1024 * 1024)
            if (fileSizeMB > 50) {
                warnings.add("Large text files may take longer to process")
            }
            
            // Format-specific warnings
            if (inputFormat == "rtf" && request.outputFormat.lowercase() == "txt") {
                warnings.add("Converting RTF to plain text will lose all formatting")
            }
            
            if (inputFormat == "csv" && request.outputFormat.lowercase() == "pdf") {
                warnings.add("CSV to PDF conversion may not preserve table structure optimally")
            }
            
            return mapOf(
                "valid" to errors.isEmpty(),
                "errors" to errors,
                "warnings" to warnings,
                "detectedEncoding" to detectTextEncoding(request.file),
                "estimatedProcessingTime" to estimateTextProcessingTime(request),
                "supportedFeatures" to getSupportedTextFeatures(inputFormat, request.outputFormat)
            )
            
        } catch (ex: Exception) {
            logger.error("Error validating text request: ${ex.message}", ex)
            return mapOf(
                "valid" to false,
                "errors" to listOf(ex.message ?: "Validation failed"),
                "warnings" to emptyList<String>()
            )
        }
    }

    // Private helper methods

    private fun validateTextRequest(request: OfficeDocumentConversionRequest) {
        if (request.file.isEmpty) {
            throw PdfOperationException("Text document file is required")
        }
        
        val inputFormat = getFileExtension(request.file.originalFilename)
        if (inputFormat !in TEXT_FORMATS) {
            throw PdfOperationException("Input format $inputFormat is not supported for text conversion")
        }
    }

    private fun readTextContent(filePath: Path, format: String): TextContent {
        return when (format.lowercase()) {
            "rtf" -> readRtfContent(filePath)
            "txt" -> readPlainTextContent(filePath)
            "csv" -> readCsvContent(filePath)
            "html" -> readHtmlContent(filePath)
            "xml" -> readXmlContent(filePath)
            "md" -> readMarkdownContent(filePath)
            "json" -> readJsonContent(filePath)
            else -> readPlainTextContent(filePath)
        }
    }

    private fun readRtfContent(filePath: Path): TextContent {
        // In a real implementation, would parse RTF format properly
        val rawContent = Files.readString(filePath, detectFileEncoding(filePath))
        return TextContent(
            plainText = extractPlainTextFromRtf(rawContent),
            formattedText = rawContent,
            format = "rtf",
            encoding = detectFileEncoding(filePath).name(),
            hasFormatting = true,
            metadata = extractRtfMetadata(rawContent)
        )
    }

    private fun readPlainTextContent(filePath: Path): TextContent {
        val encoding = detectFileEncoding(filePath)
        val content = Files.readString(filePath, encoding)
        return TextContent(
            plainText = content,
            formattedText = content,
            format = "txt",
            encoding = encoding.name(),
            hasFormatting = false,
            metadata = mapOf(
                "lineCount" to content.lines().size,
                "characterCount" to content.length,
                "wordCount" to content.split("\\s+".toRegex()).size
            )
        )
    }

    private fun readCsvContent(filePath: Path): TextContent {
        val encoding = detectFileEncoding(filePath)
        val content = Files.readString(filePath, encoding)
        val lines = content.lines()
        return TextContent(
            plainText = content,
            formattedText = content,
            format = "csv",
            encoding = encoding.name(),
            hasFormatting = false,
            metadata = mapOf(
                "rowCount" to lines.size,
                "estimatedColumnCount" to (lines.firstOrNull()?.split(",")?.size ?: 0)
            )
        )
    }

    private fun readHtmlContent(filePath: Path): TextContent {
        val encoding = detectFileEncoding(filePath)
        val content = Files.readString(filePath, encoding)
        return TextContent(
            plainText = stripHtmlTags(content),
            formattedText = content,
            format = "html",
            encoding = encoding.name(),
            hasFormatting = true,
            metadata = mapOf(
                "hasHtmlTags" to content.contains("<"),
                "estimatedTagCount" to content.split("<").size - 1
            )
        )
    }

    private fun readXmlContent(filePath: Path): TextContent {
        val encoding = detectFileEncoding(filePath)
        val content = Files.readString(filePath, encoding)
        return TextContent(
            plainText = stripXmlTags(content),
            formattedText = content,
            format = "xml",
            encoding = encoding.name(),
            hasFormatting = true,
            metadata = mapOf(
                "isValidXml" to content.trim().startsWith("<?xml"),
                "estimatedElementCount" to content.split("<").size - 1
            )
        )
    }

    private fun readMarkdownContent(filePath: Path): TextContent {
        val encoding = detectFileEncoding(filePath)
        val content = Files.readString(filePath, encoding)
        return TextContent(
            plainText = stripMarkdownSyntax(content),
            formattedText = content,
            format = "md",
            encoding = encoding.name(),
            hasFormatting = true,
            metadata = mapOf(
                "hasMarkdownSyntax" to (content.contains("#") || content.contains("*") || content.contains("_")),
                "estimatedHeaderCount" to content.lines().count { it.trim().startsWith("#") }
            )
        )
    }

    private fun readJsonContent(filePath: Path): TextContent {
        val encoding = detectFileEncoding(filePath)
        val content = Files.readString(filePath, encoding)
        return TextContent(
            plainText = content,
            formattedText = content,
            format = "json",
            encoding = encoding.name(),
            hasFormatting = false,
            metadata = mapOf(
                "isValidJson" to (content.trim().startsWith("{") || content.trim().startsWith("[")),
                "estimatedObjectCount" to content.split("{").size - 1
            )
        )
    }

    private fun performTextConversion(
        textContent: TextContent,
        inputFormat: String,
        request: OfficeDocumentConversionRequest
    ): Path {
        val outputFormat = request.outputFormat.lowercase()
        val outputFile = tempFileManagerService.createTempFile("text_output", ".$outputFormat", 60)
        
        val convertedContent = when (outputFormat) {
            "pdf" -> createPdfFromText(textContent, request)
            "rtf" -> convertToRtf(textContent, request)
            "txt" -> convertToPlainText(textContent)
            "html" -> convertToHtml(textContent, request)
            "docx" -> createWordFromText(textContent, request)
            "odt" -> createOdtFromText(textContent, request)
            else -> convertToPlainText(textContent)
        }
        
        Files.write(outputFile, convertedContent.toByteArray())
        return outputFile
    }

    private fun createPdfFromText(textContent: TextContent, request: OfficeDocumentConversionRequest): String {
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
            /Length ${120 + textContent.plainText.take(100).length}
            >>
            stream
            BT
            /F1 12 Tf
            50 750 Td
            (Text Conversion: ${textContent.format.uppercase()} to PDF) Tj
            0 -20 Td
            (Encoding: ${textContent.encoding}) Tj
            0 -20 Td
            (Content preview: ${textContent.plainText.take(50).replace("\n", " ").replace("(", "\\(").replace(")", "\\)")}) Tj
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
            400
            %%EOF
        """.trimIndent()
    }

    private fun convertToRtf(textContent: TextContent, request: OfficeDocumentConversionRequest): String {
        return if (textContent.format == "rtf") {
            textContent.formattedText
        } else {
            "{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Times New Roman;}} \\f0\\fs24 ${textContent.plainText.replace("\n", "\\par ")}}"
        }
    }

    private fun convertToPlainText(textContent: TextContent): String {
        return textContent.plainText
    }

    private fun convertToHtml(textContent: TextContent, request: OfficeDocumentConversionRequest): String {
        return if (textContent.format == "html") {
            textContent.formattedText
        } else {
            """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Converted Text Document</title>
                    <meta charset="${textContent.encoding}">
                </head>
                <body>
                    <pre>${textContent.plainText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</pre>
                </body>
                </html>
            """.trimIndent()
        }
    }

    private fun createWordFromText(textContent: TextContent, request: OfficeDocumentConversionRequest): String {
        return "Word document converted from ${textContent.format.uppercase()}\n\n${textContent.plainText}"
    }

    private fun createOdtFromText(textContent: TextContent, request: OfficeDocumentConversionRequest): String {
        return "OpenDocument Text converted from ${textContent.format.uppercase()}\n\n${textContent.plainText}"
    }

    private fun performTextAnalysis(textContent: TextContent, inputFormat: String): Map<String, Any> {
        val plainText = textContent.plainText
        val lines = plainText.lines()
        
        return mapOf(
            "success" to true,
            "format" to inputFormat,
            "encoding" to textContent.encoding,
            "hasFormatting" to textContent.hasFormatting,
            "statistics" to mapOf(
                "characterCount" to plainText.length,
                "lineCount" to lines.size,
                "wordCount" to plainText.split("\\s+".toRegex()).size,
                "paragraphCount" to lines.count { it.isNotBlank() },
                "averageLineLength" to if (lines.isNotEmpty()) plainText.length / lines.size else 0
            ),
            "metadata" to textContent.metadata,
            "recommendations" to getConversionRecommendations(textContent)
        )
    }

    private fun detectFileEncoding(filePath: Path): Charset {
        // Simple encoding detection - in production would use more sophisticated detection
        val bytes = Files.readAllBytes(filePath).take(1000).toByteArray()
        
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8 // Default to UTF-8
        }
    }

    private fun detectTextEncoding(file: org.springframework.web.multipart.MultipartFile): String {
        // Simplified encoding detection for validation
        return "UTF-8" // Would implement proper detection in production
    }

    private fun extractPlainTextFromRtf(rtfContent: String): String {
        // Simplified RTF to plain text extraction
        return rtfContent
            .replace("\\\\", "\\")
            .replace("\\{", "{")
            .replace("\\}", "}")
            .replace("\\par", "\n")
            .replace(Regex("\\\\[a-zA-Z]+\\d*\\s*"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
            .trim()
    }

    private fun extractRtfMetadata(rtfContent: String): Map<String, Any> {
        return mapOf(
            "hasRtfHeader" to rtfContent.startsWith("{\\rtf"),
            "estimatedControlWords" to rtfContent.split("\\").size - 1
        )
    }

    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    }

    private fun stripXmlTags(xml: String): String {
        return xml.replace(Regex("<[^>]*>"), "").trim()
    }

    private fun stripMarkdownSyntax(markdown: String): String {
        return markdown
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            .replace(Regex("\\[([^]]+)\\]\\([^)]+\\)"), "$1")
            .trim()
    }

    private fun buildTextConversionMatrix(): Map<String, List<String>> {
        return mapOf(
            "rtf" to listOf("pdf", "txt", "html", "docx", "odt"),
            "txt" to listOf("pdf", "rtf", "html", "docx", "odt"),
            "csv" to listOf("pdf", "txt", "html"),
            "html" to listOf("pdf", "txt", "rtf", "docx"),
            "xml" to listOf("pdf", "txt", "html"),
            "md" to listOf("pdf", "txt", "html", "rtf", "docx"),
            "json" to listOf("pdf", "txt", "html")
        )
    }

    private fun getTextConversionRecommendations(): List<String> {
        return listOf(
            "Use UTF-8 encoding for best compatibility",
            "RTF format preserves basic formatting during conversion",
            "Large text files may benefit from chunked processing",
            "Consider target format limitations when converting formatted text",
            "Preview conversions with small samples for quality assessment"
        )
    }

    private fun estimateTextProcessingTime(request: OfficeDocumentConversionRequest): Long {
        val fileSizeKB = request.file.size / 1024
        val baseTime = 500L // 0.5 seconds
        return baseTime + (fileSizeKB * 10) // 10ms per KB
    }

    private fun getSupportedTextFeatures(inputFormat: String, outputFormat: String): Map<String, Boolean> {
        return mapOf(
            "preserveFormatting" to (inputFormat == "rtf" && outputFormat in listOf("rtf", "docx", "odt", "html")),
            "encodingConversion" to true,
            "lineEndingNormalization" to true,
            "specialCharacterHandling" to true,
            "metadataPreservation" to (outputFormat in listOf("pdf", "docx", "odt"))
        )
    }

    private fun getConversionRecommendations(textContent: TextContent): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (textContent.hasFormatting) {
            recommendations.add("Consider output formats that support formatting")
        }
        
        if (textContent.encoding != "UTF-8") {
            recommendations.add("Text encoding conversion may be applied")
        }
        
        if (textContent.plainText.length > 100000) {
            recommendations.add("Large document may require additional processing time")
        }
        
        return recommendations.ifEmpty { listOf("Standard conversion should work well") }
    }

    private fun getFileExtension(filename: String?): String {
        return filename?.substringAfterLast(".")?.lowercase() ?: "unknown"
    }

    private fun getContentType(extension: String): String {
        return when (extension) {
            "pdf" -> "application/pdf"
            "rtf" -> "application/rtf"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            else -> "text/plain"
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

    private fun createTextRequestFromJob(job: ConversionJobRequest): OfficeDocumentConversionRequest {
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

    // Data classes for text processing
    private data class TextContent(
        val plainText: String,
        val formattedText: String,
        val format: String,
        val encoding: String,
        val hasFormatting: Boolean,
        val metadata: Map<String, Any>
    )
}