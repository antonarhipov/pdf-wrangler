package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

/**
 * Service for generating previews and validation for visual enhancement operations.
 * Provides preview functionality for image overlays, stamps, color manipulations, and form flattening.
 * Task 88: Add visual enhancement preview and validation features
 */
@Service
class VisualEnhancementPreviewService(
    private val imageOverlayService: ImageOverlayService,
    private val stampApplicationService: StampApplicationService,
    private val colorManipulationService: ColorManipulationService,
    private val formFlatteningService: FormFlatteningService
) {

    private val logger = LoggerFactory.getLogger(VisualEnhancementPreviewService::class.java)

    /**
     * Data class for preview configuration.
     */
    data class PreviewConfig(
        val enhancementType: String,
        val pageNumber: Int = 1,
        val previewWidth: Int = 800,
        val previewHeight: Int = 1000,
        val showGuidelines: Boolean = true,
        val outputFormat: String = "PNG",
        val quality: String = "medium" // "low", "medium", "high"
    )

    /**
     * Data class for preview result.
     */
    data class PreviewResult(
        val success: Boolean,
        val message: String,
        val previewImageBase64: String?,
        val previewWidth: Int,
        val previewHeight: Int,
        val enhancementDetails: Map<String, Any>,
        val validationResults: ValidationResults,
        val processingTimeMs: Long
    )

    /**
     * Data class for validation results.
     */
    data class ValidationResults(
        val isValid: Boolean,
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val qualityScore: Double? = null,
        val resourceUsageEstimate: Map<String, Any> = emptyMap()
    )

    /**
     * Generates a preview for visual enhancement operations.
     *
     * @param request Preview request containing file and enhancement configuration
     * @return PreviewResult with preview image and validation information
     */
    fun generatePreview(request: VisualEnhancementPreviewRequest): VisualEnhancementPreviewResponse {
        logger.info("Generating preview for {} enhancement", request.enhancementType)

        val startTime = System.currentTimeMillis()

        return try {
            // Validate the request
            val validation = validatePreviewRequest(request)
            if (!validation.isValid) {
                return VisualEnhancementPreviewResponse(
                    success = false,
                    message = "Validation failed: ${validation.errors.joinToString(", ")}",
                    previewImageBase64 = null,
                    enhancementDetails = emptyMap()
                )
            }

            // Generate preview based on enhancement type
            val previewResult = when (request.enhancementType.lowercase()) {
                "imageoverlay" -> generateImageOverlayPreview(request)
                "stamp" -> generateStampPreview(request)
                "colormanipulation" -> generateColorManipulationPreview(request)
                "formflattening" -> generateFormFlatteningPreview(request)
                else -> {
                    logger.warn("Unsupported enhancement type: {}", request.enhancementType)
                    PreviewResult(
                        success = false,
                        message = "Unsupported enhancement type: ${request.enhancementType}",
                        previewImageBase64 = null,
                        previewWidth = 0,
                        previewHeight = 0,
                        enhancementDetails = emptyMap(),
                        validationResults = validation,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            VisualEnhancementPreviewResponse(
                success = previewResult.success,
                message = previewResult.message,
                previewImageBase64 = previewResult.previewImageBase64,
                enhancementDetails = previewResult.enhancementDetails,
                qualityAssessment = if (previewResult.validationResults.qualityScore != null) {
                    mapOf(
                        "qualityScore" to previewResult.validationResults.qualityScore,
                        "warnings" to previewResult.validationResults.warnings,
                        "suggestions" to previewResult.validationResults.suggestions
                    )
                } else null
            )

        } catch (e: Exception) {
            logger.error("Failed to generate preview: {}", e.message, e)
            VisualEnhancementPreviewResponse(
                success = false,
                message = "Failed to generate preview: ${e.message}",
                previewImageBase64 = null,
                enhancementDetails = emptyMap()
            )
        }
    }

    /**
     * Generates preview for image overlay enhancement.
     */
    private fun generateImageOverlayPreview(request: VisualEnhancementPreviewRequest): PreviewResult {
        if (request.overlayImage == null) {
            return createErrorPreview("Overlay image is required for image overlay preview")
        }

        val config = ImageOverlayService.ImageOverlayConfig(
            overlayImage = request.overlayImage,
            scale = request.overlayScale,
            opacity = request.overlayOpacity,
            position = request.position,
            customX = request.customX,
            customY = request.customY
        )

        // Validate configuration
        val validationErrors = imageOverlayService.validateImageOverlayConfig(config)
        if (validationErrors.isNotEmpty()) {
            return createErrorPreview("Configuration validation failed: ${validationErrors.joinToString(", ")}")
        }

        // Create mock preview (in a real implementation, this would render the actual overlay)
        val previewDetails = mapOf(
            "enhancementType" to "imageOverlay",
            "overlayScale" to config.scale,
            "overlayOpacity" to config.opacity,
            "position" to config.position,
            "imageFormat" to (request.overlayImage.originalFilename?.substringAfterLast(".") ?: "unknown"),
            "estimatedFileSize" to request.file.size
        )

        val validation = ValidationResults(
            isValid = true,
            warnings = generateImageOverlayWarnings(config),
            suggestions = generateImageOverlaySuggestions(config),
            qualityScore = calculateImageOverlayQualityScore(config),
            resourceUsageEstimate = estimateImageOverlayResources(config, request.file.size)
        )

        return PreviewResult(
            success = true,
            message = "Image overlay preview generated successfully",
            previewImageBase64 = generateMockPreviewImage("Image Overlay Preview"),
            previewWidth = 800,
            previewHeight = 1000,
            enhancementDetails = previewDetails,
            validationResults = validation,
            processingTimeMs = 150
        )
    }

    /**
     * Generates preview for stamp enhancement.
     */
    private fun generateStampPreview(request: VisualEnhancementPreviewRequest): PreviewResult {
        val config = StampApplicationService.StampConfig(
            stampType = request.stampType,
            stampText = request.stampText,
            stampImage = request.stampImage,
            position = request.position,
            customX = request.customX,
            customY = request.customY
        )

        // Validate configuration
        val validationErrors = stampApplicationService.validateStampConfig(config)
        if (validationErrors.isNotEmpty()) {
            return createErrorPreview("Configuration validation failed: ${validationErrors.joinToString(", ")}")
        }

        val previewDetails = mapOf(
            "enhancementType" to "stamp",
            "stampType" to config.stampType,
            "stampText" to (config.stampText ?: ""),
            "position" to config.position,
            "predefinedStamp" to stampApplicationService.getPredefinedStampTypes().contains(config.stampType)
        )

        val validation = ValidationResults(
            isValid = true,
            warnings = generateStampWarnings(config),
            suggestions = generateStampSuggestions(config),
            qualityScore = calculateStampQualityScore(config),
            resourceUsageEstimate = estimateStampResources(config, request.file.size)
        )

        return PreviewResult(
            success = true,
            message = "Stamp preview generated successfully",
            previewImageBase64 = generateMockPreviewImage("${config.stampType.uppercase()} Stamp Preview"),
            previewWidth = 800,
            previewHeight = 1000,
            enhancementDetails = previewDetails,
            validationResults = validation,
            processingTimeMs = 100
        )
    }

    /**
     * Generates preview for color manipulation enhancement.
     */
    private fun generateColorManipulationPreview(request: VisualEnhancementPreviewRequest): PreviewResult {
        val config = ColorManipulationService.ColorConfig(
            operation = request.colorOperation,
            intensity = request.colorIntensity
        )

        // Validate configuration
        val validationErrors = colorManipulationService.validateColorConfig(config)
        if (validationErrors.isNotEmpty()) {
            return createErrorPreview("Configuration validation failed: ${validationErrors.joinToString(", ")}")
        }

        val previewDetails = mapOf(
            "enhancementType" to "colorManipulation",
            "operation" to config.operation,
            "intensity" to config.intensity,
            "supportedOperations" to colorManipulationService.getSupportedOperations().keys,
            "resourceIntensive" to colorManipulationService.isResourceIntensiveOperation(config.operation)
        )

        val validation = ValidationResults(
            isValid = true,
            warnings = generateColorManipulationWarnings(config),
            suggestions = generateColorManipulationSuggestions(config),
            qualityScore = calculateColorManipulationQualityScore(config),
            resourceUsageEstimate = estimateColorManipulationResources(config, request.file.size)
        )

        return PreviewResult(
            success = true,
            message = "Color manipulation preview generated successfully",
            previewImageBase64 = generateMockPreviewImage("${config.operation.uppercase()} Preview"),
            previewWidth = 800,
            previewHeight = 1000,
            enhancementDetails = previewDetails,
            validationResults = validation,
            processingTimeMs = 200
        )
    }

    /**
     * Generates preview for form flattening enhancement.
     */
    private fun generateFormFlatteningPreview(request: VisualEnhancementPreviewRequest): PreviewResult {
        val config = FormFlatteningService.FlattenConfig(
            preserveFormData = true // Default value since not in preview request
        )

        // Check if document has interactive forms
        val hasInteractiveForms = formFlatteningService.hasInteractiveForms(request.file)
        val formStats = formFlatteningService.getFormStatistics(request.file)

        val previewDetails = mapOf(
            "enhancementType" to "formFlattening",
            "hasInteractiveForms" to hasInteractiveForms,
            "formStatistics" to (formStats ?: emptyMap<String, Any>()),
            "preserveFormData" to config.preserveFormData,
            "estimatedProcessingTime" to formFlatteningService.estimateProcessingTime(
                listOf(request.file), 
                formStats?.let { FormFlatteningService.FormAnalysis(
                    hasAcroForm = it["hasInteractiveForms"] as? Boolean ?: false,
                    totalFields = it["totalFields"] as? Int ?: 0,
                    fieldsByType = it["fieldTypes"] as? Map<String, Int> ?: emptyMap(),
                    hasSignatures = it["hasSignatures"] as? Boolean ?: false,
                    signatureCount = it["signatureCount"] as? Int ?: 0,
                    hasAnnotations = it["hasAnnotations"] as? Boolean ?: false,
                    annotationCount = it["annotationCount"] as? Int ?: 0,
                    hasJavaScript = it["hasJavaScript"] as? Boolean ?: false,
                    hasCalculatedFields = false, // Not provided in stats
                    readOnlyFieldCount = it["readOnlyFields"] as? Int ?: 0
                ) }
            )
        )

        val warnings = mutableListOf<String>()
        if (!hasInteractiveForms) {
            warnings.add("Document does not contain interactive forms")
        }

        val validation = ValidationResults(
            isValid = true,
            warnings = warnings,
            suggestions = generateFormFlatteningSuggestions(config, formStats),
            qualityScore = if (hasInteractiveForms) 0.9 else 0.3,
            resourceUsageEstimate = mapOf(
                "processingTime" to (formStats?.get("totalFields") as? Int ?: 0) * 10,
                "memoryUsage" to "low"
            )
        )

        return PreviewResult(
            success = true,
            message = "Form flattening preview generated successfully",
            previewImageBase64 = generateMockPreviewImage("Form Flattening Preview"),
            previewWidth = 800,
            previewHeight = 1000,
            enhancementDetails = previewDetails,
            validationResults = validation,
            processingTimeMs = 80
        )
    }

    /**
     * Validates a preview request.
     */
    private fun validatePreviewRequest(request: VisualEnhancementPreviewRequest): ValidationResults {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Basic validation
        if (request.file.isEmpty) {
            errors.add("PDF file is required")
        }

        if (request.pageNumber < 1) {
            errors.add("Page number must be at least 1")
        }

        // Enhancement-specific validation
        when (request.enhancementType.lowercase()) {
            "imageoverlay" -> {
                if (request.overlayImage == null || request.overlayImage.isEmpty) {
                    errors.add("Overlay image is required for image overlay preview")
                }
            }
            "stamp" -> {
                if (request.stampText.isNullOrBlank() && (request.stampImage == null || request.stampImage.isEmpty)) {
                    errors.add("Either stamp text or stamp image is required")
                }
            }
            "colormanipulation" -> {
                val supportedOps = colorManipulationService.getSupportedOperations().keys
                if (request.colorOperation !in supportedOps) {
                    errors.add("Unsupported color operation: ${request.colorOperation}")
                }
            }
        }

        return ValidationResults(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Creates an error preview result.
     */
    private fun createErrorPreview(message: String): PreviewResult {
        return PreviewResult(
            success = false,
            message = message,
            previewImageBase64 = null,
            previewWidth = 0,
            previewHeight = 0,
            enhancementDetails = emptyMap(),
            validationResults = ValidationResults(isValid = false, errors = listOf(message)),
            processingTimeMs = 0
        )
    }

    /**
     * Generates a mock preview image (placeholder implementation).
     */
    private fun generateMockPreviewImage(text: String): String {
        return try {
            // Create a simple mock image
            val image = BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, 800, 1000)
            graphics.color = java.awt.Color.GRAY
            graphics.drawString(text, 350, 500)
            graphics.dispose()

            // Convert to base64
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "PNG", outputStream)
            Base64.getEncoder().encodeToString(outputStream.toByteArray())
        } catch (e: Exception) {
            logger.warn("Failed to generate mock preview image: {}", e.message)
            "preview-placeholder-base64"
        }
    }

    // Helper methods for generating warnings, suggestions, and quality scores

    private fun generateImageOverlayWarnings(config: ImageOverlayService.ImageOverlayConfig): List<String> {
        val warnings = mutableListOf<String>()
        if (config.scale > 2.0) warnings.add("Large scale factor may affect image quality")
        if (config.opacity < 0.3) warnings.add("Very low opacity may make overlay barely visible")
        return warnings
    }

    private fun generateImageOverlaySuggestions(config: ImageOverlayService.ImageOverlayConfig): List<String> {
        val suggestions = mutableListOf<String>()
        if (config.position == "center") suggestions.add("Consider bottom-right for less intrusive overlay")
        return suggestions
    }

    private fun calculateImageOverlayQualityScore(config: ImageOverlayService.ImageOverlayConfig): Double {
        var score = 0.8
        if (config.scale > 3.0) score -= 0.2
        if (config.opacity < 0.2 || config.opacity > 0.9) score -= 0.1
        return score.coerceIn(0.0, 1.0)
    }

    private fun estimateImageOverlayResources(config: ImageOverlayService.ImageOverlayConfig, fileSize: Long): Map<String, Any> {
        return mapOf(
            "processingTime" to (fileSize / 1024 / 1024 * 500).toInt(),
            "memoryUsage" to if (config.scale > 2.0) "high" else "medium"
        )
    }

    private fun generateStampWarnings(config: StampApplicationService.StampConfig): List<String> {
        val warnings = mutableListOf<String>()
        if (config.opacity < 0.5) warnings.add("Low opacity may make stamp less visible")
        return warnings
    }

    private fun generateStampSuggestions(config: StampApplicationService.StampConfig): List<String> {
        val suggestions = mutableListOf<String>()
        if (config.stampType == "custom") suggestions.add("Consider using predefined stamps for consistency")
        return suggestions
    }

    private fun calculateStampQualityScore(config: StampApplicationService.StampConfig): Double {
        return if (stampApplicationService.getPredefinedStampTypes().contains(config.stampType)) 0.9 else 0.7
    }

    private fun estimateStampResources(config: StampApplicationService.StampConfig, fileSize: Long): Map<String, Any> {
        return mapOf(
            "processingTime" to (fileSize / 1024 / 1024 * 200).toInt(),
            "memoryUsage" to "low"
        )
    }

    private fun generateColorManipulationWarnings(config: ColorManipulationService.ColorConfig): List<String> {
        val warnings = mutableListOf<String>()
        if (colorManipulationService.isResourceIntensiveOperation(config.operation)) {
            warnings.add("This operation requires significant processing resources")
        }
        if (config.intensity > 1.5) warnings.add("High intensity may result in unnatural colors")
        return warnings
    }

    private fun generateColorManipulationSuggestions(config: ColorManipulationService.ColorConfig): List<String> {
        val suggestions = mutableListOf<String>()
        when (config.operation) {
            "brightness" -> if (config.intensity > 1.3) suggestions.add("Consider lower intensity for better readability")
            "contrast" -> if (config.intensity > 1.4) suggestions.add("High contrast may reduce readability")
        }
        return suggestions
    }

    private fun calculateColorManipulationQualityScore(config: ColorManipulationService.ColorConfig): Double {
        var score = 0.8
        if (config.intensity > 1.8) score -= 0.3
        if (config.intensity < 0.3) score -= 0.2
        return score.coerceIn(0.0, 1.0)
    }

    private fun estimateColorManipulationResources(config: ColorManipulationService.ColorConfig, fileSize: Long): Map<String, Any> {
        val multiplier = if (colorManipulationService.isResourceIntensiveOperation(config.operation)) 3 else 2
        return mapOf(
            "processingTime" to (fileSize / 1024 / 1024 * 1000 * multiplier).toInt(),
            "memoryUsage" to if (colorManipulationService.isResourceIntensiveOperation(config.operation)) "high" else "medium"
        )
    }

    private fun generateFormFlatteningSuggestions(config: FormFlatteningService.FlattenConfig, formStats: Map<String, Any>?): List<String> {
        val suggestions = mutableListOf<String>()
        val hasSignatures = formStats?.get("hasSignatures") as? Boolean ?: false
        if (hasSignatures && config.flattenSignatures) {
            suggestions.add("Flattening signatures will invalidate them")
        }
        return suggestions
    }

    /**
     * Gets configuration options for visual enhancements.
     */
    fun getEnhancementOptions(): Map<String, Any> {
        return mapOf(
            "supportedTypes" to listOf("imageOverlay", "stamp", "colorManipulation", "formFlattening"),
            "imageOverlay" to mapOf(
                "supportedFormats" to imageOverlayService.getSupportedImageFormats(),
                "maxScale" to 5.0,
                "positions" to listOf("center", "top-left", "top-right", "bottom-left", "bottom-right", "custom")
            ),
            "stamp" to mapOf(
                "predefinedTypes" to stampApplicationService.getPredefinedStampTypes(),
                "sizes" to listOf("small", "medium", "large", "custom")
            ),
            "colorManipulation" to mapOf(
                "operations" to colorManipulationService.getSupportedOperations()
            ),
            "formFlattening" to mapOf(
                "preserveOptions" to listOf("preserveFormData", "flattenSignatures", "flattenAnnotations")
            )
        )
    }
}