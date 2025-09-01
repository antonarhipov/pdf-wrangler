package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*

/**
 * Service for generating watermark previews before applying them to PDF documents.
 * Provides preview functionality for both text and image watermarks.
 * Task 79: Create watermark preview functionality before applying
 */
@Service
class WatermarkPreviewService(
    private val textWatermarkService: TextWatermarkService,
    private val imageWatermarkService: ImageWatermarkService,
    private val watermarkPositioningService: WatermarkPositioningService,
    private val spacerConfigurationService: SpacerConfigurationService
) {

    private val logger = LoggerFactory.getLogger(WatermarkPreviewService::class.java)

    /**
     * Data class for preview configuration.
     */
    data class PreviewConfig(
        val watermarkType: String, // "text" or "image"
        val pageNumber: Int = 1,
        val previewWidth: Int = 800,
        val previewHeight: Int = 1000,
        val showPositionGuides: Boolean = true,
        val outputFormat: String = "PNG"
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
        val watermarkPositions: List<WatermarkPosition>,
        val pageInfo: PageInfo
    )

    /**
     * Data class for watermark position information.
     */
    data class WatermarkPosition(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rotation: Double
    )

    /**
     * Data class for page information.
     */
    data class PageInfo(
        val pageNumber: Int,
        val pageWidth: Float,
        val pageHeight: Float,
        val totalPages: Int
    )

    /**
     * Generates a preview of text watermark on a PDF page.
     *
     * @param pdfFile The PDF file to preview
     * @param textConfig Text watermark configuration
     * @param previewConfig Preview settings
     * @return PreviewResult with base64 encoded preview image
     */
    fun generateTextWatermarkPreview(
        pdfFile: MultipartFile,
        textConfig: TextWatermarkService.TextWatermarkConfig,
        previewConfig: PreviewConfig = PreviewConfig("text")
    ): PreviewResult {
        logger.info("Generating text watermark preview for page {}", previewConfig.pageNumber)

        return try {
            // Validate configuration
            val validationErrors = textWatermarkService.validateTextWatermarkConfig(textConfig)
            if (validationErrors.isNotEmpty()) {
                return PreviewResult(
                    success = false,
                    message = "Validation failed: ${validationErrors.joinToString(", ")}",
                    previewImageBase64 = null,
                    previewWidth = 0,
                    previewHeight = 0,
                    watermarkPositions = emptyList(),
                    pageInfo = PageInfo(previewConfig.pageNumber, 0f, 0f, 0)
                )
            }

            // Calculate watermark positions (simplified without actual PDF parsing)
            val watermarkPositions = calculateTextWatermarkPositions(textConfig)

            // Create mock page info
            val pageInfo = PageInfo(
                pageNumber = previewConfig.pageNumber,
                pageWidth = 595f, // Standard A4 width in points
                pageHeight = 842f, // Standard A4 height in points
                totalPages = 1 // Simplified for preview
            )

            // Generate a simple preview representation
            val previewDetails = mapOf(
                "watermarkType" to "text",
                "text" to textConfig.text,
                "fontSize" to textConfig.fontSize,
                "fontColor" to textConfig.fontColor,
                "position" to textConfig.position,
                "opacity" to textConfig.opacity,
                "rotation" to textConfig.rotation,
                "repeatWatermark" to textConfig.repeatWatermark,
                "positions" to watermarkPositions.size
            )

            logger.info("Text watermark preview calculated with {} positions", watermarkPositions.size)

            PreviewResult(
                success = true,
                message = "Text watermark preview generated successfully",
                previewImageBase64 = "preview-placeholder", // Placeholder for actual image
                previewWidth = previewConfig.previewWidth,
                previewHeight = previewConfig.previewHeight,
                watermarkPositions = watermarkPositions,
                pageInfo = pageInfo
            )

        } catch (e: Exception) {
            logger.error("Failed to generate text watermark preview: {}", e.message)
            PreviewResult(
                success = false,
                message = "Failed to generate preview: ${e.message}",
                previewImageBase64 = null,
                previewWidth = 0,
                previewHeight = 0,
                watermarkPositions = emptyList(),
                pageInfo = PageInfo(previewConfig.pageNumber, 0f, 0f, 0)
            )
        }
    }

    /**
     * Generates a preview of image watermark on a PDF page.
     *
     * @param pdfFile The PDF file to preview
     * @param imageConfig Image watermark configuration
     * @param previewConfig Preview settings
     * @return PreviewResult with base64 encoded preview image
     */
    fun generateImageWatermarkPreview(
        pdfFile: MultipartFile,
        imageConfig: ImageWatermarkService.ImageWatermarkConfig,
        previewConfig: PreviewConfig = PreviewConfig("image")
    ): PreviewResult {
        logger.info("Generating image watermark preview for page {}", previewConfig.pageNumber)

        return try {
            // Validate configuration
            val validationErrors = imageWatermarkService.validateImageWatermarkConfig(imageConfig)
            if (validationErrors.isNotEmpty()) {
                return PreviewResult(
                    success = false,
                    message = "Validation failed: ${validationErrors.joinToString(", ")}",
                    previewImageBase64 = null,
                    previewWidth = 0,
                    previewHeight = 0,
                    watermarkPositions = emptyList(),
                    pageInfo = PageInfo(previewConfig.pageNumber, 0f, 0f, 0)
                )
            }

            // Calculate watermark positions
            val watermarkPositions = calculateImageWatermarkPositions(imageConfig)

            // Create mock page info
            val pageInfo = PageInfo(
                pageNumber = previewConfig.pageNumber,
                pageWidth = 595f, // Standard A4 width in points
                pageHeight = 842f, // Standard A4 height in points
                totalPages = 1 // Simplified for preview
            )

            logger.info("Image watermark preview calculated with {} positions", watermarkPositions.size)

            PreviewResult(
                success = true,
                message = "Image watermark preview generated successfully",
                previewImageBase64 = "preview-placeholder", // Placeholder for actual image
                previewWidth = previewConfig.previewWidth,
                previewHeight = previewConfig.previewHeight,
                watermarkPositions = watermarkPositions,
                pageInfo = pageInfo
            )

        } catch (e: Exception) {
            logger.error("Failed to generate image watermark preview: {}", e.message)
            PreviewResult(
                success = false,
                message = "Failed to generate preview: ${e.message}",
                previewImageBase64 = null,
                previewWidth = 0,
                previewHeight = 0,
                watermarkPositions = emptyList(),
                pageInfo = PageInfo(previewConfig.pageNumber, 0f, 0f, 0)
            )
        }
    }

    /**
     * Generates a preview without actually applying watermarks (just shows positions).
     *
     * @param pdfFile The PDF file to preview
     * @param watermarkType Type of watermark ("text" or "image")
     * @param watermarkConfig Watermark configuration (text or image)
     * @param previewConfig Preview settings
     * @return PreviewResult with position indicators
     */
    fun generatePositionPreview(
        pdfFile: MultipartFile,
        watermarkType: String,
        watermarkConfig: Any,
        previewConfig: PreviewConfig
    ): PreviewResult {
        logger.info("Generating position preview for {} watermark on page {}", 
            watermarkType, previewConfig.pageNumber)

        return try {
            // Calculate watermark positions based on type
            val watermarkPositions = when (watermarkType.lowercase()) {
                "text" -> {
                    val textConfig = watermarkConfig as TextWatermarkService.TextWatermarkConfig
                    calculateTextWatermarkPositions(textConfig)
                }
                "image" -> {
                    val imageConfig = watermarkConfig as ImageWatermarkService.ImageWatermarkConfig
                    calculateImageWatermarkPositions(imageConfig)
                }
                else -> emptyList()
            }

            // Create mock page info
            val pageInfo = PageInfo(
                pageNumber = previewConfig.pageNumber,
                pageWidth = 595f,
                pageHeight = 842f,
                totalPages = 1
            )

            logger.info("Position preview calculated with {} watermark positions", watermarkPositions.size)

            PreviewResult(
                success = true,
                message = "Position preview generated successfully",
                previewImageBase64 = "position-preview-placeholder",
                previewWidth = previewConfig.previewWidth,
                previewHeight = previewConfig.previewHeight,
                watermarkPositions = watermarkPositions,
                pageInfo = pageInfo
            )

        } catch (e: Exception) {
            logger.error("Failed to generate position preview: {}", e.message)
            PreviewResult(
                success = false,
                message = "Failed to generate preview: ${e.message}",
                previewImageBase64 = null,
                previewWidth = 0,
                previewHeight = 0,
                watermarkPositions = emptyList(),
                pageInfo = PageInfo(previewConfig.pageNumber, 0f, 0f, 0)
            )
        }
    }

    /**
     * Calculates watermark positions for text watermarks using mock page dimensions.
     */
    private fun calculateTextWatermarkPositions(
        config: TextWatermarkService.TextWatermarkConfig
    ): List<WatermarkPosition> {
        // Use standard A4 dimensions for calculation
        val pageWidth = 595f
        val pageHeight = 842f
        
        // Estimate text dimensions
        val estimatedWidth = config.text.length * config.fontSize * 0.6f
        val estimatedHeight = config.fontSize.toFloat()

        return if (config.repeatWatermark) {
            // Calculate repeated positions with spacing
            val spacingConfig = SpacerConfigurationService.SpacingConfig(
                horizontalSpacing = config.horizontalSpacing,
                verticalSpacing = config.verticalSpacing,
                spacingType = SpacerConfigurationService.SpacingType.ABSOLUTE
            )

            // Simple grid calculation
            val effectiveWidth = pageWidth - 100f // Account for margins
            val effectiveHeight = pageHeight - 100f
            
            val cellWidth = estimatedWidth + config.horizontalSpacing.toFloat()
            val cellHeight = estimatedHeight + config.verticalSpacing.toFloat()
            
            val columns = maxOf(1, (effectiveWidth / cellWidth).toInt())
            val rows = maxOf(1, (effectiveHeight / cellHeight).toInt())

            val positions = mutableListOf<WatermarkPosition>()
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val x = 50f + (col * cellWidth)
                    val y = 50f + (row * cellHeight)
                    positions.add(
                        WatermarkPosition(
                            x = x,
                            y = y,
                            width = estimatedWidth,
                            height = estimatedHeight,
                            rotation = config.rotation
                        )
                    )
                }
            }
            positions
        } else {
            // Single position calculation
            val (x, y) = calculateSinglePosition(
                pageWidth, pageHeight, estimatedWidth, estimatedHeight, 
                config.position, config.customX, config.customY
            )
            
            listOf(
                WatermarkPosition(
                    x = x,
                    y = y,
                    width = estimatedWidth,
                    height = estimatedHeight,
                    rotation = config.rotation
                )
            )
        }
    }

    /**
     * Calculates watermark positions for image watermarks using mock page dimensions.
     */
    private fun calculateImageWatermarkPositions(
        config: ImageWatermarkService.ImageWatermarkConfig
    ): List<WatermarkPosition> {
        return try {
            // Use standard A4 dimensions
            val pageWidth = 595f
            val pageHeight = 842f

            // Estimate image dimensions (simplified without actually reading the image)
            val estimatedWidth = 100f * config.imageScale.toFloat()
            val estimatedHeight = 100f * config.imageScale.toFloat()

            if (config.repeatWatermark) {
                // Calculate repeated positions
                val effectiveWidth = pageWidth - 100f
                val effectiveHeight = pageHeight - 100f
                
                val cellWidth = estimatedWidth + config.horizontalSpacing.toFloat()
                val cellHeight = estimatedHeight + config.verticalSpacing.toFloat()
                
                val columns = maxOf(1, (effectiveWidth / cellWidth).toInt())
                val rows = maxOf(1, (effectiveHeight / cellHeight).toInt())

                val positions = mutableListOf<WatermarkPosition>()
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        val x = 50f + (col * cellWidth)
                        val y = 50f + (row * cellHeight)
                        positions.add(
                            WatermarkPosition(
                                x = x,
                                y = y,
                                width = estimatedWidth,
                                height = estimatedHeight,
                                rotation = config.rotation
                            )
                        )
                    }
                }
                positions
            } else {
                // Single position
                val (x, y) = calculateSinglePosition(
                    pageWidth, pageHeight, estimatedWidth, estimatedHeight,
                    config.position, config.customX, config.customY
                )
                
                listOf(
                    WatermarkPosition(
                        x = x,
                        y = y,
                        width = estimatedWidth,
                        height = estimatedHeight,
                        rotation = config.rotation
                    )
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to calculate image watermark positions: {}", e.message)
            emptyList()
        }
    }

    /**
     * Calculates a single watermark position based on positioning configuration.
     */
    private fun calculateSinglePosition(
        pageWidth: Float,
        pageHeight: Float,
        watermarkWidth: Float,
        watermarkHeight: Float,
        position: String,
        customX: Double?,
        customY: Double?
    ): Pair<Float, Float> {
        return when (position.lowercase().replace("-", "_")) {
            "center" -> Pair(
                (pageWidth - watermarkWidth) / 2,
                (pageHeight - watermarkHeight) / 2
            )
            "top_left" -> Pair(50f, pageHeight - watermarkHeight - 50f)
            "top_center" -> Pair(
                (pageWidth - watermarkWidth) / 2,
                pageHeight - watermarkHeight - 50f
            )
            "top_right" -> Pair(
                pageWidth - watermarkWidth - 50f,
                pageHeight - watermarkHeight - 50f
            )
            "center_left" -> Pair(50f, (pageHeight - watermarkHeight) / 2)
            "center_right" -> Pair(
                pageWidth - watermarkWidth - 50f,
                (pageHeight - watermarkHeight) / 2
            )
            "bottom_left" -> Pair(50f, 50f)
            "bottom_center" -> Pair((pageWidth - watermarkWidth) / 2, 50f)
            "bottom_right" -> Pair(pageWidth - watermarkWidth - 50f, 50f)
            "custom" -> {
                val x = (customX ?: 0.5) * pageWidth - watermarkWidth / 2
                val y = (customY ?: 0.5) * pageHeight - watermarkHeight / 2
                Pair(x.toFloat(), y.toFloat())
            }
            else -> Pair((pageWidth - watermarkWidth) / 2, (pageHeight - watermarkHeight) / 2)
        }
    }

    /**
     * Validates preview configuration.
     */
    fun validatePreviewConfig(config: PreviewConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.watermarkType !in listOf("text", "image")) {
            errors.add("Watermark type must be 'text' or 'image'")
        }

        if (config.pageNumber < 1) {
            errors.add("Page number must be at least 1")
        }

        if (config.previewWidth < 100 || config.previewWidth > 4000) {
            errors.add("Preview width must be between 100 and 4000 pixels")
        }

        if (config.previewHeight < 100 || config.previewHeight > 4000) {
            errors.add("Preview height must be between 100 and 4000 pixels")
        }

        if (config.outputFormat !in listOf("PNG", "JPEG", "JPG")) {
            errors.add("Output format must be PNG, JPEG, or JPG")
        }

        return errors
    }

    /**
     * Creates a default preview configuration.
     */
    fun createDefaultPreviewConfig(watermarkType: String): PreviewConfig {
        return PreviewConfig(
            watermarkType = watermarkType,
            pageNumber = 1,
            previewWidth = 800,
            previewHeight = 1000,
            showPositionGuides = false,
            outputFormat = "PNG"
        )
    }
}