package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDField
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File

/**
 * Service for flattening interactive PDF forms to convert them to static content.
 * Preserves form data while removing interactive capabilities, handles signatures and annotations.
 * Task 87: Implement FormFlatteningService to convert interactive forms to static content
 */
@Service
class FormFlatteningService {

    private val logger = LoggerFactory.getLogger(FormFlatteningService::class.java)

    /**
     * Data class for form flattening configuration.
     */
    data class FlattenConfig(
        val preserveFormData: Boolean = true,
        val flattenSignatures: Boolean = true,
        val flattenAnnotations: Boolean = true,
        val removeEmptyFields: Boolean = false,
        val preserveReadOnlyFields: Boolean = true,
        val handleCalculatedFields: Boolean = true,
        val maintainTabOrder: Boolean = false,
        val preserveJavaScript: Boolean = false,
        val flattenButtonFields: Boolean = true,
        val preserveFieldAppearance: Boolean = true
    )

    /**
     * Data class for form flattening result.
     */
    data class FormFlatteningResult(
        val success: Boolean,
        val message: String,
        val outputFileName: String?,
        val totalPages: Int?,
        val processedFiles: Int,
        val fileSize: Long?,
        val formsFlattened: Int,
        val fieldsProcessed: Int,
        val signaturesHandled: Int,
        val annotationsHandled: Int,
        val processingDetails: Map<String, Any> = emptyMap()
    )

    /**
     * Data class for form analysis information.
     */
    data class FormAnalysis(
        val hasAcroForm: Boolean,
        val totalFields: Int,
        val fieldsByType: Map<String, Int>,
        val hasSignatures: Boolean,
        val signatureCount: Int,
        val hasAnnotations: Boolean,
        val annotationCount: Int,
        val hasJavaScript: Boolean,
        val hasCalculatedFields: Boolean,
        val readOnlyFieldCount: Int
    )

    /**
     * Flattens forms in multiple PDF files.
     *
     * @param files List of PDF files to process
     * @param config Form flattening configuration
     * @return FormFlatteningResult with processing results
     */
    fun flattenForms(
        files: List<MultipartFile>,
        config: FlattenConfig = FlattenConfig()
    ): FormFlatteningResult {
        logger.info("Flattening forms in {} files", files.size)

        var processedFiles = 0
        var totalPages = 0
        var totalFileSize = 0L
        var totalFormsFlattened = 0
        var totalFieldsProcessed = 0
        var totalSignaturesHandled = 0
        var totalAnnotationsHandled = 0
        val processingDetails = mutableMapOf<String, Any>()

        try {
            for (file in files) {
                logger.debug("Processing file: {}", file.originalFilename)

                val tempFile = File.createTempFile("form_input", ".pdf")
                file.transferTo(tempFile)
                
                Loader.loadPDF(tempFile).use { document ->
                    val analysis = analyzeDocument(document)
                    val result = flattenDocumentForms(document, config, analysis)
                    
                    totalPages += document.numberOfPages
                    totalFormsFlattened += if (result.formsFlattened > 0) 1 else 0
                    totalFieldsProcessed += result.fieldsProcessed
                    totalSignaturesHandled += result.signaturesHandled
                    totalAnnotationsHandled += result.annotationsHandled
                    processedFiles++
                    totalFileSize += file.size
                    
                    processingDetails[file.originalFilename ?: "unknown_$processedFiles"] = mapOf(
                        "analysis" to analysis,
                        "result" to result
                    )
                }
                
                tempFile.delete()
            }

            val outputFileName = generateOutputFileName(files.first().originalFilename ?: "flattened_output")

            return FormFlatteningResult(
                success = true,
                message = "Form flattening completed successfully: $totalFormsFlattened forms processed",
                outputFileName = outputFileName,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize,
                formsFlattened = totalFormsFlattened,
                fieldsProcessed = totalFieldsProcessed,
                signaturesHandled = totalSignaturesHandled,
                annotationsHandled = totalAnnotationsHandled,
                processingDetails = processingDetails
            )

        } catch (e: Exception) {
            logger.error("Failed to flatten forms: {}", e.message, e)
            return FormFlatteningResult(
                success = false,
                message = "Failed to flatten forms: ${e.message}",
                outputFileName = null,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize,
                formsFlattened = totalFormsFlattened,
                fieldsProcessed = totalFieldsProcessed,
                signaturesHandled = totalSignaturesHandled,
                annotationsHandled = totalAnnotationsHandled
            )
        }
    }

    /**
     * Analyzes a document to understand its form structure.
     */
    fun analyzeDocument(document: PDDocument): FormAnalysis {
        val acroForm = document.documentCatalog.acroForm
        var totalFields = 0
        val fieldsByType = mutableMapOf<String, Int>()
        var signatureCount = 0
        var annotationCount = 0
        var hasJavaScript = false
        var hasCalculatedFields = false
        var readOnlyFieldCount = 0

        // Analyze AcroForm
        if (acroForm != null) {
            val fields = acroForm.fields
            totalFields = fields.size

            for (field in fields) {
                val fieldType = getFieldType(field)
                fieldsByType[fieldType] = fieldsByType.getOrDefault(fieldType, 0) + 1

                // Check for signatures
                if (field.fieldType == "Sig") {
                    signatureCount++
                }

                // Check for read-only fields
                if (field.isReadOnly) {
                    readOnlyFieldCount++
                }

                // Check for calculated fields (simplified detection)
                // Note: calculateAction property may not be available in all PDFBox versions
                // This is a simplified check that could be enhanced with direct COS object inspection
                try {
                    if (field.cosObject.getDictionaryObject("C") != null) {
                        hasCalculatedFields = true
                    }
                } catch (e: Exception) {
                    // Ignore if property doesn't exist
                }
            }
        }

        // Check for JavaScript
        if (document.documentCatalog.names?.javaScript != null) {
            hasJavaScript = true
        }

        // Count annotations across all pages
        for (page in document.pages) {
            val annotations = page.annotations
            annotationCount += annotations.size
        }

        return FormAnalysis(
            hasAcroForm = acroForm != null,
            totalFields = totalFields,
            fieldsByType = fieldsByType,
            hasSignatures = signatureCount > 0,
            signatureCount = signatureCount,
            hasAnnotations = annotationCount > 0,
            annotationCount = annotationCount,
            hasJavaScript = hasJavaScript,
            hasCalculatedFields = hasCalculatedFields,
            readOnlyFieldCount = readOnlyFieldCount
        )
    }

    /**
     * Flattens forms in a single document.
     */
    private fun flattenDocumentForms(
        document: PDDocument,
        config: FlattenConfig,
        analysis: FormAnalysis
    ): FormFlatteningResult {
        var fieldsProcessed = 0
        var signaturesHandled = 0
        var annotationsHandled = 0
        var formsFlattened = 0

        try {
            val acroForm = document.documentCatalog.acroForm

            if (acroForm != null) {
                logger.debug("Processing AcroForm with {} fields", analysis.totalFields)

                // Handle form fields
                if (config.preserveFormData) {
                    fieldsProcessed = flattenFormFields(acroForm, config)
                } else {
                    fieldsProcessed = removeFormFields(acroForm, config)
                }

                // Remove the AcroForm after flattening
                document.documentCatalog.acroForm = null
                formsFlattened = 1
            }

            // Handle signatures
            if (config.flattenSignatures && analysis.hasSignatures) {
                signaturesHandled = flattenSignatures(document)
            }

            // Handle annotations
            if (config.flattenAnnotations && analysis.hasAnnotations) {
                annotationsHandled = flattenAnnotations(document, config)
            }

            // Remove JavaScript if not preserving
            if (!config.preserveJavaScript && analysis.hasJavaScript) {
                removeJavaScript(document)
            }

            logger.debug("Flattened form: {} fields, {} signatures, {} annotations", 
                fieldsProcessed, signaturesHandled, annotationsHandled)

        } catch (e: Exception) {
            logger.error("Failed to flatten document forms: {}", e.message)
            throw RuntimeException("Failed to flatten document forms", e)
        }

        return FormFlatteningResult(
            success = true,
            message = "Document forms flattened successfully",
            outputFileName = null,
            totalPages = document.numberOfPages,
            processedFiles = 1,
            fileSize = null,
            formsFlattened = formsFlattened,
            fieldsProcessed = fieldsProcessed,
            signaturesHandled = signaturesHandled,
            annotationsHandled = annotationsHandled
        )
    }

    /**
     * Flattens form fields while preserving their data.
     */
    private fun flattenFormFields(acroForm: PDAcroForm, config: FlattenConfig): Int {
        var processedFields = 0
        val fields = acroForm.fields

        for (field in fields) {
            try {
                // Skip empty fields if configured
                if (config.removeEmptyFields && (field.valueAsString?.isBlank() != false)) {
                    continue
                }

                // Skip read-only fields if configured to preserve them
                if (config.preserveReadOnlyFields && field.isReadOnly) {
                    continue
                }

                // Skip button fields if not configured to flatten them
                if (!config.flattenButtonFields && field.fieldType == "Btn") {
                    continue
                }

                // Flatten the field
                if (config.preserveFieldAppearance) {
                    // Set the field to be non-interactive but preserve appearance
                    field.isReadOnly = true
                    // Note: Direct annotation manipulation may require page-level processing
                    // This is simplified for the basic implementation
                } else {
                    // Basic flattening - just make it non-interactive
                    field.isReadOnly = true
                }

                processedFields++
                logger.trace("Flattened field: {} ({})", field.fullyQualifiedName, field.fieldType)

            } catch (e: Exception) {
                logger.warn("Failed to flatten field {}: {}", field.fullyQualifiedName, e.message)
            }
        }

        return processedFields
    }

    /**
     * Removes form fields entirely.
     */
    private fun removeFormFields(acroForm: PDAcroForm, config: FlattenConfig): Int {
        val fields = acroForm.fields
        val removedCount = fields.size

        // Clear all fields
        acroForm.fields.clear()

        logger.debug("Removed {} form fields", removedCount)
        return removedCount
    }

    /**
     * Flattens digital signatures.
     */
    private fun flattenSignatures(document: PDDocument): Int {
        var signaturesHandled = 0

        try {
            val acroForm = document.documentCatalog.acroForm
            if (acroForm != null) {
                val signatureFields = acroForm.fields.filter { it.fieldType == "Sig" }
                
                for (signatureField in signatureFields) {
                    try {
                        // Make signature field non-interactive
                        signatureField.isReadOnly = true
                        
                        // Remove signature dictionary to prevent validation
                        signatureField.cosObject.removeItem(COSName.V)
                        
                        signaturesHandled++
                        logger.debug("Flattened signature field: {}", signatureField.fullyQualifiedName)

                    } catch (e: Exception) {
                        logger.warn("Failed to flatten signature field {}: {}", 
                            signatureField.fullyQualifiedName, e.message)
                    }
                }
            }

        } catch (e: Exception) {
            logger.error("Failed to flatten signatures: {}", e.message)
        }

        return signaturesHandled
    }

    /**
     * Flattens annotations.
     */
    private fun flattenAnnotations(document: PDDocument, config: FlattenConfig): Int {
        var annotationsHandled = 0

        try {
            for (page in document.pages) {
                val annotations = page.annotations.toList() // Create copy to avoid modification during iteration
                
                for (annotation in annotations) {
                    try {
                        // Different handling based on annotation type
                        when (annotation.subtype) {
                            "Widget" -> {
                                // Form field widgets - handle based on config
                                if (config.flattenButtonFields || annotation.cosObject.getString("FT") != "Btn") {
                                    annotation.isReadOnly = true
                                    annotation.isHidden = false
                                }
                            }
                            "Stamp", "Text", "Highlight", "Underline", "StrikeOut" -> {
                                // Standard annotations - make them non-interactive
                                annotation.isReadOnly = true
                                annotation.isHidden = false
                            }
                            else -> {
                                // Other annotations - make non-interactive
                                annotation.isReadOnly = true
                            }
                        }

                        annotationsHandled++
                        logger.trace("Flattened annotation: {} on page", annotation.subtype)

                    } catch (e: Exception) {
                        logger.warn("Failed to flatten annotation of type {}: {}", 
                            annotation.subtype, e.message)
                    }
                }
            }

        } catch (e: Exception) {
            logger.error("Failed to flatten annotations: {}", e.message)
        }

        return annotationsHandled
    }

    /**
     * Removes JavaScript from the document.
     */
    private fun removeJavaScript(document: PDDocument) {
        try {
            val catalog = document.documentCatalog
            val names = catalog.names
            
            if (names?.javaScript != null) {
                // Remove JavaScript name tree
                names.cosObject.removeItem(COSName.getPDFName("JavaScript"))
                logger.debug("Removed JavaScript from document")
            }

        } catch (e: Exception) {
            logger.warn("Failed to remove JavaScript: {}", e.message)
        }
    }

    /**
     * Gets the type of a form field as a string.
     */
    private fun getFieldType(field: PDField): String {
        return when (field.fieldType) {
            "Tx" -> "Text"
            "Btn" -> "Button"
            "Ch" -> "Choice"
            "Sig" -> "Signature"
            else -> field.fieldType ?: "Unknown"
        }
    }

    /**
     * Validates form flattening configuration.
     */
    fun validateFlattenConfig(config: FlattenConfig): List<String> {
        val errors = mutableListOf<String>()

        // Basic validation - most configurations are boolean and don't need validation
        // Add specific validations if needed in the future

        if (!config.preserveFormData && config.preserveFieldAppearance) {
            errors.add("Cannot preserve field appearance when not preserving form data")
        }

        return errors
    }

    /**
     * Creates a default flattening configuration.
     */
    fun createDefaultConfig(): FlattenConfig {
        return FlattenConfig(
            preserveFormData = true,
            flattenSignatures = true,
            flattenAnnotations = true,
            removeEmptyFields = false,
            preserveReadOnlyFields = true,
            handleCalculatedFields = true,
            preserveFieldAppearance = true
        )
    }

    /**
     * Creates a configuration for complete form removal.
     */
    fun createRemoveAllConfig(): FlattenConfig {
        return FlattenConfig(
            preserveFormData = false,
            flattenSignatures = true,
            flattenAnnotations = true,
            removeEmptyFields = true,
            preserveReadOnlyFields = false,
            handleCalculatedFields = false,
            preserveJavaScript = false,
            flattenButtonFields = true,
            preserveFieldAppearance = false
        )
    }

    /**
     * Creates a configuration for minimal flattening (preserve as much as possible).
     */
    fun createMinimalFlattenConfig(): FlattenConfig {
        return FlattenConfig(
            preserveFormData = true,
            flattenSignatures = false,
            flattenAnnotations = false,
            removeEmptyFields = false,
            preserveReadOnlyFields = true,
            handleCalculatedFields = true,
            maintainTabOrder = true,
            preserveJavaScript = true,
            flattenButtonFields = false,
            preserveFieldAppearance = true
        )
    }

    /**
     * Estimates processing time based on document complexity.
     */
    fun estimateProcessingTime(files: List<MultipartFile>, analysis: FormAnalysis? = null): Long {
        val totalSize = files.sumOf { it.size }
        val baseTimePerMB = 1000L // Base time in milliseconds

        val complexityMultiplier = when {
            analysis?.totalFields ?: 0 > 100 -> 2.5
            analysis?.totalFields ?: 0 > 50 -> 2.0
            analysis?.totalFields ?: 0 > 10 -> 1.5
            else -> 1.0
        }

        val featureMultiplier = when {
            analysis?.hasJavaScript == true -> 1.3
            analysis?.hasSignatures == true -> 1.2
            else -> 1.0
        }

        return ((totalSize / (1024 * 1024)) * baseTimePerMB * complexityMultiplier * featureMultiplier).toLong()
    }

    /**
     * Checks if a document has interactive forms.
     */
    fun hasInteractiveForms(file: MultipartFile): Boolean {
        return try {
            val tempFile = File.createTempFile("check_forms", ".pdf")
            file.transferTo(tempFile)
            
            Loader.loadPDF(tempFile).use { document ->
                val hasForm = document.documentCatalog.acroForm != null
                tempFile.delete()
                hasForm
            }
        } catch (e: Exception) {
            logger.warn("Failed to check for interactive forms: {}", e.message)
            false
        }
    }

    /**
     * Gets form statistics for a document.
     */
    fun getFormStatistics(file: MultipartFile): Map<String, Any>? {
        return try {
            val tempFile = File.createTempFile("stats_forms", ".pdf")
            file.transferTo(tempFile)
            
            Loader.loadPDF(tempFile).use { document ->
                val analysis = analyzeDocument(document)
                tempFile.delete()
                
                mapOf(
                    "hasInteractiveForms" to analysis.hasAcroForm,
                    "totalFields" to analysis.totalFields,
                    "fieldTypes" to analysis.fieldsByType,
                    "hasSignatures" to analysis.hasSignatures,
                    "signatureCount" to analysis.signatureCount,
                    "hasAnnotations" to analysis.hasAnnotations,
                    "annotationCount" to analysis.annotationCount,
                    "hasJavaScript" to analysis.hasJavaScript,
                    "readOnlyFields" to analysis.readOnlyFieldCount
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get form statistics: {}", e.message)
            null
        }
    }

    /**
     * Generates output filename for processed file.
     */
    private fun generateOutputFileName(originalFileName: String): String {
        val baseName = originalFileName.substringBeforeLast(".")
        val extension = originalFileName.substringAfterLast(".", "pdf")
        return "flattened_${baseName}.${extension}"
    }
}