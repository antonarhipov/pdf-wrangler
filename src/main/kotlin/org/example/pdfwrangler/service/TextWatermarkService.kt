package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.IOException

/**
 * Service for applying text watermarks to PDF documents.
 * Supports customizable text overlays with font, color, and positioning options.
 * Task 74: Implement TextWatermarkService with customizable text overlays
 */
@Service
class TextWatermarkService(
    private val watermarkPositioningService: WatermarkPositioningService,
    private val opacityAndRotationProcessor: OpacityAndRotationProcessor,
    private val spacerConfigurationService: SpacerConfigurationService
) {

    private val logger = LoggerFactory.getLogger(TextWatermarkService::class.java)

    /**
     * Data class for text watermark configuration.
     */
    data class TextWatermarkConfig(
        val text: String,
        val fontFamily: String = "Arial",
        val fontSize: Int = 24,
        val fontColor: String = "#000000",
        val fontBold: Boolean = false,
        val fontItalic: Boolean = false,
        val opacity: Double = 0.5,
        val rotation: Double = 0.0,
        val position: String = "center",
        val customX: Double? = null,
        val customY: Double? = null,
        val horizontalSpacing: Double = 0.0,
        val verticalSpacing: Double = 0.0,
        val repeatWatermark: Boolean = false
    )

    /**
     * Applies text watermark to a PDF document.
     *
     * @param document The PDF document
     * @param config Text watermark configuration
     * @param pageNumbers List of page numbers to watermark (1-based), empty for all pages
     * @return Number of pages watermarked
     */
    fun applyTextWatermark(
        document: PDDocument,
        config: TextWatermarkConfig,
        pageNumbers: List<Int> = emptyList()
    ): Int {
        logger.info("Applying text watermark '{}' to {} pages", 
            config.text.take(20) + if (config.text.length > 20) "..." else "", 
            if (pageNumbers.isEmpty()) document.numberOfPages else pageNumbers.size)

        val pagesToProcess = if (pageNumbers.isEmpty()) {
            (1..document.numberOfPages).toList()
        } else {
            pageNumbers.filter { it in 1..document.numberOfPages }
        }

        var processedPages = 0

        for (pageNumber in pagesToProcess) {
            try {
                val page = document.getPage(pageNumber - 1) // Convert to 0-based
                applyTextWatermarkToPage(document, page, config)
                processedPages++
                logger.debug("Applied text watermark to page {}", pageNumber)
            } catch (e: Exception) {
                logger.error("Failed to apply text watermark to page {}: {}", pageNumber, e.message)
                throw RuntimeException("Failed to apply text watermark to page $pageNumber", e)
            }
        }

        logger.info("Successfully applied text watermark to {} pages", processedPages)
        return processedPages
    }

    /**
     * Applies text watermark to a single page.
     */
    private fun applyTextWatermarkToPage(
        document: PDDocument,
        page: PDPage,
        config: TextWatermarkConfig
    ) {
        val font = selectFont(config.fontFamily, config.fontBold, config.fontItalic)
        val textColor = parseColor(config.fontColor)
        val textDimensions = calculateTextDimensions(config.text, font, config.fontSize)

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
            
            if (config.repeatWatermark) {
                applyRepeatedTextWatermark(contentStream, page, config, font, textColor, textDimensions)
            } else {
                applySingleTextWatermark(contentStream, page, config, font, textColor, textDimensions)
            }
        }
    }

    /**
     * Applies a single text watermark to a page.
     */
    private fun applySingleTextWatermark(
        contentStream: PDPageContentStream,
        page: PDPage,
        config: TextWatermarkConfig,
        font: PDFont,
        textColor: Color,
        textDimensions: WatermarkPositioningService.WatermarkDimensions
    ) {
        val position = watermarkPositioningService.calculatePosition(
            page = page,
            position = config.position,
            customX = config.customX,
            customY = config.customY,
            watermarkDimensions = textDimensions,
            rotation = config.rotation
        )

        val centerX = position.x + textDimensions.width / 2
        val centerY = position.y + textDimensions.height / 2

        // Configure opacity and rotation
        val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity)
        val rotationConfig = opacityAndRotationProcessor.createRotationConfig(
            config.rotation, centerX, centerY
        )

        // Apply transformations and draw text
        opacityAndRotationProcessor.saveAndApplyTransformations(
            contentStream, opacityConfig, rotationConfig
        )

        try {
            contentStream.setFont(font, config.fontSize.toFloat())
            contentStream.setNonStrokingColor(textColor)
            contentStream.beginText()
            contentStream.newLineAtOffset(position.x, position.y)
            contentStream.showText(config.text)
            contentStream.endText()
        } finally {
            opacityAndRotationProcessor.restoreGraphicsState(contentStream)
        }
    }

    /**
     * Applies repeated text watermarks to a page.
     */
    private fun applyRepeatedTextWatermark(
        contentStream: PDPageContentStream,
        page: PDPage,
        config: TextWatermarkConfig,
        font: PDFont,
        textColor: Color,
        textDimensions: WatermarkPositioningService.WatermarkDimensions
    ) {
        val spacingConfig = SpacerConfigurationService.SpacingConfig(
            horizontalSpacing = config.horizontalSpacing,
            verticalSpacing = config.verticalSpacing,
            spacingType = SpacerConfigurationService.SpacingType.ABSOLUTE
        )

        val gridLayout = spacerConfigurationService.createGridLayout(
            page = page,
            watermarkWidth = textDimensions.width.toDouble(),
            watermarkHeight = textDimensions.height.toDouble(),
            spacingConfig = spacingConfig
        )

        val positions = spacerConfigurationService.calculateGridPositions(
            gridLayout = gridLayout,
            margins = spacingConfig.margins
        )

        // Configure opacity and rotation once for all watermarks
        val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity)

        opacityAndRotationProcessor.saveAndApplyTransformations(
            contentStream, opacityConfig, null
        )

        try {
            contentStream.setFont(font, config.fontSize.toFloat())
            contentStream.setNonStrokingColor(textColor)

            for (pos in positions) {
                if (config.rotation != 0.0) {
                    val centerX = pos.first.toFloat() + textDimensions.width / 2
                    val centerY = pos.second.toFloat() + textDimensions.height / 2
                    val rotationConfig = opacityAndRotationProcessor.createRotationConfig(
                        config.rotation, centerX, centerY
                    )

                    contentStream.saveGraphicsState()
                    opacityAndRotationProcessor.applyRotation(
                        contentStream, config.rotation, centerX, centerY
                    )
                }

                contentStream.beginText()
                contentStream.newLineAtOffset(pos.first.toFloat(), pos.second.toFloat())
                contentStream.showText(config.text)
                contentStream.endText()

                if (config.rotation != 0.0) {
                    contentStream.restoreGraphicsState()
                }
            }
        } finally {
            opacityAndRotationProcessor.restoreGraphicsState(contentStream)
        }

        logger.debug("Applied {} repeated text watermarks to page", positions.size)
    }

    /**
     * Calculates the dimensions of text with the given font and size.
     */
    private fun calculateTextDimensions(
        text: String, 
        font: PDFont, 
        fontSize: Int
    ): WatermarkPositioningService.WatermarkDimensions {
        return try {
            val textWidth = font.getStringWidth(text) / 1000.0f * fontSize
            val textHeight = font.fontDescriptor.fontBoundingBox.height / 1000.0f * fontSize
            
            WatermarkPositioningService.WatermarkDimensions(
                width = textWidth,
                height = textHeight
            )
        } catch (e: IOException) {
            logger.warn("Failed to calculate text dimensions, using defaults: {}", e.message)
            WatermarkPositioningService.WatermarkDimensions(
                width = text.length * fontSize * 0.6f, // Rough estimation
                height = fontSize.toFloat()
            )
        }
    }

    /**
     * Selects the appropriate font based on configuration.
     */
    private fun selectFont(fontFamily: String, bold: Boolean, italic: Boolean): PDFont {
        return try {
            when (fontFamily.lowercase()) {
                "arial", "helvetica" -> when {
                    bold && italic -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE)
                    bold -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                    italic -> PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
                    else -> PDType1Font(Standard14Fonts.FontName.HELVETICA)
                }
                "times", "times new roman" -> when {
                    bold && italic -> PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC)
                    bold -> PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)
                    italic -> PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC)
                    else -> PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)
                }
                "courier" -> when {
                    bold && italic -> PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE)
                    bold -> PDType1Font(Standard14Fonts.FontName.COURIER_BOLD)
                    italic -> PDType1Font(Standard14Fonts.FontName.COURIER_OBLIQUE)
                    else -> PDType1Font(Standard14Fonts.FontName.COURIER)
                }
                else -> {
                    logger.warn("Font family '{}' not supported, using Helvetica", fontFamily)
                    PDType1Font(Standard14Fonts.FontName.HELVETICA)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load font '{}', using default: {}", fontFamily, e.message)
            PDType1Font(Standard14Fonts.FontName.HELVETICA)
        }
    }

    /**
     * Parses a color string (hex format) to a Color object.
     */
    private fun parseColor(colorString: String): Color {
        return try {
            val hex = colorString.removePrefix("#")
            when (hex.length) {
                3 -> {
                    // Short hex format (e.g., "f0a")
                    val r = hex.substring(0, 1).repeat(2).toInt(16)
                    val g = hex.substring(1, 2).repeat(2).toInt(16)
                    val b = hex.substring(2, 3).repeat(2).toInt(16)
                    Color(r, g, b)
                }
                6 -> {
                    // Full hex format (e.g., "ff00aa")
                    Color.decode("#$hex")
                }
                else -> {
                    logger.warn("Invalid color format '{}', using black", colorString)
                    Color.BLACK
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse color '{}', using black: {}", colorString, e.message)
            Color.BLACK
        }
    }

    /**
     * Validates text watermark configuration.
     */
    fun validateTextWatermarkConfig(config: TextWatermarkConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.text.isBlank()) {
            errors.add("Watermark text cannot be empty")
        }

        if (config.fontSize < 1 || config.fontSize > 200) {
            errors.add("Font size must be between 1 and 200")
        }

        if (!opacityAndRotationProcessor.isValidOpacity(config.opacity)) {
            errors.add("Opacity must be between 0.0 and 1.0")
        }

        if (config.horizontalSpacing < 0 || config.verticalSpacing < 0) {
            errors.add("Spacing values cannot be negative")
        }

        // Validate color format
        try {
            parseColor(config.fontColor)
        } catch (e: Exception) {
            errors.add("Invalid color format: ${config.fontColor}")
        }

        return errors
    }

    /**
     * Creates a default text watermark configuration.
     */
    fun createDefaultConfig(text: String): TextWatermarkConfig {
        return TextWatermarkConfig(
            text = text,
            fontFamily = "Arial",
            fontSize = 24,
            fontColor = "#808080",
            fontBold = false,
            fontItalic = false,
            opacity = 0.3,
            rotation = 45.0,
            position = "center"
        )
    }
}