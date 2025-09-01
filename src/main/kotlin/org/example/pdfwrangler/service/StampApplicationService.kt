package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/**
 * Service for applying stamps to PDF documents for official markings and seals.
 * Supports text stamps, image stamps, and predefined official stamp types.
 * Task 85: Build StampApplicationService for official markings and seals
 */
@Service
class StampApplicationService(
    private val watermarkPositioningService: WatermarkPositioningService,
    private val opacityAndRotationProcessor: OpacityAndRotationProcessor
) {

    private val logger = LoggerFactory.getLogger(StampApplicationService::class.java)

    /**
     * Data class for stamp configuration.
     */
    data class StampConfig(
        val stampType: String, // "text", "image", "approved", "draft", "confidential", "urgent", "custom"
        val stampText: String? = null,
        val stampImage: MultipartFile? = null,
        val stampSize: String = "medium", // "small", "medium", "large", "custom"
        val customWidth: Double? = null,
        val customHeight: Double? = null,
        val position: String = "top-right",
        val customX: Double? = null,
        val customY: Double? = null,
        val opacity: Double = 1.0,
        val rotation: Double = 0.0,
        val fontFamily: String = "Arial",
        val fontSize: Int = 14,
        val fontColor: String = "#FF0000",
        val backgroundColor: String? = null,
        val borderEnabled: Boolean = true,
        val borderWidth: Float = 2.0f,
        val borderColor: String = "#FF0000",
        val includeTimestamp: Boolean = false,
        val timestampFormat: String = "yyyy-MM-dd HH:mm"
    )

    /**
     * Data class for stamp application result.
     */
    data class StampResult(
        val success: Boolean,
        val message: String,
        val outputFileName: String?,
        val totalPages: Int?,
        val processedFiles: Int,
        val fileSize: Long?
    )

    /**
     * Predefined stamp configurations for common official markings.
     */
    private val predefinedStamps = mapOf(
        "approved" to StampConfig(
            stampType = "approved",
            stampText = "APPROVED",
            fontColor = "#00AA00",
            borderColor = "#00AA00",
            stampSize = "medium"
        ),
        "draft" to StampConfig(
            stampType = "draft",
            stampText = "DRAFT",
            fontColor = "#808080",
            borderColor = "#808080",
            stampSize = "large",
            opacity = 0.7
        ),
        "confidential" to StampConfig(
            stampType = "confidential",
            stampText = "CONFIDENTIAL",
            fontColor = "#FF0000",
            borderColor = "#FF0000",
            stampSize = "medium",
            fontSize = 16
        ),
        "urgent" to StampConfig(
            stampType = "urgent",
            stampText = "URGENT",
            fontColor = "#FF4500",
            borderColor = "#FF4500",
            stampSize = "medium",
            fontSize = 18
        )
    )

    /**
     * Standard stamp sizes in points.
     */
    private val standardSizes = mapOf(
        "small" to Pair(80.0, 30.0),
        "medium" to Pair(120.0, 40.0),
        "large" to Pair(160.0, 50.0)
    )

    /**
     * Applies stamps to multiple PDF files.
     *
     * @param files List of PDF files to process
     * @param config Stamp configuration
     * @param pageNumbers List of page numbers to apply stamp (1-based), empty for all pages
     * @return StampResult with processing results
     */
    fun applyStamp(
        files: List<MultipartFile>,
        config: StampConfig,
        pageNumbers: List<Int> = emptyList()
    ): StampResult {
        logger.info("Applying stamp '{}' to {} files", config.stampType, files.size)

        var processedFiles = 0
        var totalPages = 0
        var totalFileSize = 0L

        try {
            // Get effective configuration (use predefined if available)
            val effectiveConfig = getEffectiveConfig(config)

            for (file in files) {
                logger.debug("Processing file: {}", file.originalFilename)

                val tempFile = File.createTempFile("stamp_input", ".pdf")
                file.transferTo(tempFile)
                
                Loader.loadPDF(tempFile).use { document ->
                    val pages = applyStampToDocument(document, effectiveConfig, pageNumbers)
                    totalPages += pages
                    processedFiles++
                    totalFileSize += file.size
                }
                
                tempFile.delete()
            }

            val outputFileName = generateOutputFileName(files.first().originalFilename ?: "stamped_output")

            return StampResult(
                success = true,
                message = "Stamp applied successfully to $processedFiles files",
                outputFileName = outputFileName,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize
            )

        } catch (e: Exception) {
            logger.error("Failed to apply stamp: {}", e.message, e)
            return StampResult(
                success = false,
                message = "Failed to apply stamp: ${e.message}",
                outputFileName = null,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize
            )
        }
    }

    /**
     * Applies stamp to all specified pages in a document.
     */
    private fun applyStampToDocument(
        document: PDDocument,
        config: StampConfig,
        pageNumbers: List<Int>
    ): Int {
        val pagesToProcess = if (pageNumbers.isEmpty()) {
            (1..document.numberOfPages).toList()
        } else {
            pageNumbers.filter { it in 1..document.numberOfPages }
        }

        for (pageNumber in pagesToProcess) {
            val page = document.getPage(pageNumber - 1) // Convert to 0-based
            applyStampToPage(document, page, config)
        }

        return pagesToProcess.size
    }

    /**
     * Applies stamp to a single page.
     */
    private fun applyStampToPage(
        document: PDDocument,
        page: PDPage,
        config: StampConfig
    ) {
        when (config.stampType) {
            "image", "custom" -> if (config.stampImage != null) {
                applyImageStamp(document, page, config)
            } else {
                applyTextStamp(document, page, config)
            }
            else -> applyTextStamp(document, page, config)
        }
    }

    /**
     * Applies a text-based stamp to a page.
     */
    private fun applyTextStamp(
        document: PDDocument,
        page: PDPage,
        config: StampConfig
    ) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
            
            val stampText = buildStampText(config)
            val font = selectFont(config.fontFamily, false, false) // Stamps typically use regular weight
            val textColor = parseColor(config.fontColor)
            val borderColor = parseColor(config.borderColor)
            
            // Calculate text dimensions
            val textWidth = font.getStringWidth(stampText) / 1000.0f * config.fontSize
            val textHeight = config.fontSize.toFloat()
            
            // Get stamp dimensions
            val (stampWidth, stampHeight) = getStampDimensions(config, textWidth, textHeight)
            
            val stampDimensions = WatermarkPositioningService.WatermarkDimensions(
                width = stampWidth,
                height = stampHeight
            )

            val position = watermarkPositioningService.calculatePosition(
                page = page,
                position = config.position,
                customX = config.customX,
                customY = config.customY,
                watermarkDimensions = stampDimensions,
                rotation = config.rotation
            )

            // Apply opacity and rotation
            val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity)
            val rotationConfig = if (config.rotation != 0.0) {
                opacityAndRotationProcessor.createRotationConfig(
                    config.rotation,
                    position.x + stampWidth / 2,
                    position.y + stampHeight / 2
                )
            } else null

            opacityAndRotationProcessor.saveAndApplyTransformations(
                contentStream, opacityConfig, rotationConfig
            )

            try {
                // Draw background if specified
                config.backgroundColor?.let { bgColor ->
                    contentStream.setNonStrokingColor(parseColor(bgColor))
                    contentStream.addRect(position.x, position.y, stampWidth, stampHeight)
                    contentStream.fill()
                }

                // Draw border if enabled
                if (config.borderEnabled) {
                    contentStream.setStrokingColor(borderColor)
                    contentStream.setLineWidth(config.borderWidth)
                    contentStream.addRect(position.x, position.y, stampWidth, stampHeight)
                    contentStream.stroke()
                }

                // Draw text
                contentStream.setFont(font, config.fontSize.toFloat())
                contentStream.setNonStrokingColor(textColor)
                contentStream.beginText()
                
                // Center text within stamp bounds
                val textX = position.x + (stampWidth - textWidth) / 2
                val textY = position.y + (stampHeight - textHeight) / 2
                contentStream.newLineAtOffset(textX, textY)
                contentStream.showText(stampText)
                contentStream.endText()

                logger.debug("Applied text stamp '{}' at position ({}, {})", stampText, position.x, position.y)

            } finally {
                opacityAndRotationProcessor.restoreGraphicsState(contentStream)
            }
        }
    }

    /**
     * Applies an image-based stamp to a page.
     */
    private fun applyImageStamp(
        document: PDDocument,
        page: PDPage,
        config: StampConfig
    ) {
        if (config.stampImage == null) {
            logger.warn("Image stamp requested but no image provided")
            return
        }

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
            
            try {
                // Create PDImageXObject from the stamp image
                val pdImage = PDImageXObject.createFromByteArray(
                    document, 
                    config.stampImage.bytes, 
                    config.stampImage.originalFilename
                )

                // Calculate image dimensions
                val originalWidth = pdImage.width.toFloat()
                val originalHeight = pdImage.height.toFloat()
                val (stampWidth, stampHeight) = getStampDimensions(config, originalWidth, originalHeight)

                val stampDimensions = WatermarkPositioningService.WatermarkDimensions(
                    width = stampWidth,
                    height = stampHeight
                )

                val position = watermarkPositioningService.calculatePosition(
                    page = page,
                    position = config.position,
                    customX = config.customX,
                    customY = config.customY,
                    watermarkDimensions = stampDimensions,
                    rotation = config.rotation
                )

                // Apply opacity and rotation
                val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity)
                val rotationConfig = if (config.rotation != 0.0) {
                    opacityAndRotationProcessor.createRotationConfig(
                        config.rotation,
                        position.x + stampWidth / 2,
                        position.y + stampHeight / 2
                    )
                } else null

                opacityAndRotationProcessor.saveAndApplyTransformations(
                    contentStream, opacityConfig, rotationConfig
                )

                try {
                    contentStream.drawImage(pdImage, position.x, position.y, stampWidth, stampHeight)
                    logger.debug("Applied image stamp at position ({}, {})", position.x, position.y)

                } finally {
                    opacityAndRotationProcessor.restoreGraphicsState(contentStream)
                }

            } catch (e: Exception) {
                logger.error("Failed to process stamp image: {}", e.message)
                throw RuntimeException("Failed to apply image stamp", e)
            }
        }
    }

    /**
     * Gets effective configuration, using predefined stamp if available.
     */
    private fun getEffectiveConfig(config: StampConfig): StampConfig {
        val predefined = predefinedStamps[config.stampType]
        return if (predefined != null) {
            // Merge predefined with provided config, prioritizing user settings
            predefined.copy(
                position = config.position,
                customX = config.customX,
                customY = config.customY,
                opacity = config.opacity,
                rotation = config.rotation,
                stampSize = config.stampSize,
                customWidth = config.customWidth,
                customHeight = config.customHeight,
                includeTimestamp = config.includeTimestamp,
                timestampFormat = config.timestampFormat
            )
        } else {
            config
        }
    }

    /**
     * Builds the stamp text including timestamp if requested.
     */
    private fun buildStampText(config: StampConfig): String {
        val baseText = config.stampText ?: config.stampType.uppercase()
        
        return if (config.includeTimestamp) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(config.timestampFormat))
            "$baseText\n$timestamp"
        } else {
            baseText
        }
    }

    /**
     * Gets stamp dimensions based on configuration.
     */
    private fun getStampDimensions(
        config: StampConfig, 
        contentWidth: Float, 
        contentHeight: Float
    ): Pair<Float, Float> {
        return when (config.stampSize) {
            "custom" -> Pair(
                config.customWidth?.toFloat() ?: contentWidth,
                config.customHeight?.toFloat() ?: contentHeight
            )
            else -> {
                val standardSize = standardSizes[config.stampSize] ?: standardSizes["medium"]!!
                Pair(standardSize.first.toFloat(), standardSize.second.toFloat())
            }
        }
    }

    /**
     * Selects the appropriate font based on family name.
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
                else -> PDType1Font(Standard14Fonts.FontName.HELVETICA)
            }
        } catch (e: Exception) {
            logger.error("Failed to load font '{}', using default: {}", fontFamily, e.message)
            PDType1Font(Standard14Fonts.FontName.HELVETICA)
        }
    }

    /**
     * Parses a color string to a Color object.
     */
    private fun parseColor(colorString: String): Color {
        return try {
            val hex = colorString.removePrefix("#")
            when (hex.length) {
                3 -> {
                    val r = hex.substring(0, 1).repeat(2).toInt(16)
                    val g = hex.substring(1, 2).repeat(2).toInt(16)
                    val b = hex.substring(2, 3).repeat(2).toInt(16)
                    Color(r, g, b)
                }
                6 -> Color.decode("#$hex")
                else -> {
                    logger.warn("Invalid color format '{}', using red", colorString)
                    Color.RED
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse color '{}', using red: {}", colorString, e.message)
            Color.RED
        }
    }

    /**
     * Validates stamp configuration.
     */
    fun validateStampConfig(config: StampConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.stampType == "image" && config.stampImage == null) {
            errors.add("Image is required for image stamp type")
        }

        if (config.stampType == "text" && config.stampText.isNullOrBlank()) {
            errors.add("Text is required for text stamp type")
        }

        if (!opacityAndRotationProcessor.isValidOpacity(config.opacity)) {
            errors.add("Opacity must be between 0.0 and 1.0")
        }

        if (config.fontSize < 1 || config.fontSize > 100) {
            errors.add("Font size must be between 1 and 100")
        }

        if (config.stampSize == "custom") {
            if (config.customWidth == null || config.customWidth <= 0) {
                errors.add("Custom width must be positive when using custom size")
            }
            if (config.customHeight == null || config.customHeight <= 0) {
                errors.add("Custom height must be positive when using custom size")
            }
        }

        return errors
    }

    /**
     * Gets available predefined stamp types.
     */
    fun getPredefinedStampTypes(): List<String> {
        return predefinedStamps.keys.toList()
    }

    /**
     * Gets predefined stamp configuration.
     */
    fun getPredefinedStampConfig(stampType: String): StampConfig? {
        return predefinedStamps[stampType]
    }

    /**
     * Generates output filename for processed file.
     */
    private fun generateOutputFileName(originalFileName: String): String {
        val baseName = originalFileName.substringBeforeLast(".")
        val extension = originalFileName.substringAfterLast(".", "pdf")
        return "stamped_${baseName}.${extension}"
    }

    /**
     * Creates a default stamp configuration.
     */
    fun createDefaultConfig(stampType: String = "draft"): StampConfig {
        return predefinedStamps[stampType] ?: StampConfig(
            stampType = stampType,
            stampText = stampType.uppercase(),
            position = "top-right"
        )
    }
}