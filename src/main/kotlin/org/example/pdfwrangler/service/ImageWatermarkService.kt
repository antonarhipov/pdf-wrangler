package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Service for applying image watermarks to PDF documents.
 * Supports image overlays with positioning, scaling, and appearance control.
 * Task 75: Build ImageWatermarkService with positioning and scaling control
 */
@Service
class ImageWatermarkService(
    private val watermarkPositioningService: WatermarkPositioningService,
    private val opacityAndRotationProcessor: OpacityAndRotationProcessor,
    private val spacerConfigurationService: SpacerConfigurationService
) {

    private val logger = LoggerFactory.getLogger(ImageWatermarkService::class.java)

    /**
     * Data class for image watermark configuration.
     */
    data class ImageWatermarkConfig(
        val imageFile: MultipartFile,
        val imageScale: Double = 1.0,
        val opacity: Double = 0.5,
        val rotation: Double = 0.0,
        val position: String = "center",
        val customX: Double? = null,
        val customY: Double? = null,
        val horizontalSpacing: Double = 0.0,
        val verticalSpacing: Double = 0.0,
        val repeatWatermark: Boolean = false,
        val maintainAspectRatio: Boolean = true,
        val maxWidth: Double? = null,
        val maxHeight: Double? = null
    )

    /**
     * Data class for processed image information.
     */
    data class ProcessedImage(
        val pdImage: PDImageXObject,
        val originalWidth: Float,
        val originalHeight: Float,
        val scaledWidth: Float,
        val scaledHeight: Float
    )

    /**
     * Applies image watermark to a PDF document.
     *
     * @param document The PDF document
     * @param config Image watermark configuration
     * @param pageNumbers List of page numbers to watermark (1-based), empty for all pages
     * @return Number of pages watermarked
     */
    fun applyImageWatermark(
        document: PDDocument,
        config: ImageWatermarkConfig,
        pageNumbers: List<Int> = emptyList()
    ): Int {
        logger.info("Applying image watermark to {} pages", 
            if (pageNumbers.isEmpty()) document.numberOfPages else pageNumbers.size)

        val processedImage = try {
            processImageFile(document, config)
        } catch (e: Exception) {
            logger.error("Failed to process image file: {}", e.message)
            throw RuntimeException("Failed to process image watermark file", e)
        }

        val pagesToProcess = if (pageNumbers.isEmpty()) {
            (1..document.numberOfPages).toList()
        } else {
            pageNumbers.filter { it in 1..document.numberOfPages }
        }

        var processedPages = 0

        for (pageNumber in pagesToProcess) {
            try {
                val page = document.getPage(pageNumber - 1) // Convert to 0-based
                applyImageWatermarkToPage(document, page, config, processedImage)
                processedPages++
                logger.debug("Applied image watermark to page {}", pageNumber)
            } catch (e: Exception) {
                logger.error("Failed to apply image watermark to page {}: {}", pageNumber, e.message)
                throw RuntimeException("Failed to apply image watermark to page $pageNumber", e)
            }
        }

        logger.info("Successfully applied image watermark to {} pages", processedPages)
        return processedPages
    }

    /**
     * Applies image watermark to a single page.
     */
    private fun applyImageWatermarkToPage(
        document: PDDocument,
        page: PDPage,
        config: ImageWatermarkConfig,
        processedImage: ProcessedImage
    ) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
            
            if (config.repeatWatermark) {
                applyRepeatedImageWatermark(contentStream, page, config, processedImage)
            } else {
                applySingleImageWatermark(contentStream, page, config, processedImage)
            }
        }
    }

    /**
     * Applies a single image watermark to a page.
     */
    private fun applySingleImageWatermark(
        contentStream: PDPageContentStream,
        page: PDPage,
        config: ImageWatermarkConfig,
        processedImage: ProcessedImage
    ) {
        val watermarkDimensions = WatermarkPositioningService.WatermarkDimensions(
            width = processedImage.scaledWidth,
            height = processedImage.scaledHeight
        )

        val position = watermarkPositioningService.calculatePosition(
            page = page,
            position = config.position,
            customX = config.customX,
            customY = config.customY,
            watermarkDimensions = watermarkDimensions,
            rotation = config.rotation
        )

        val centerX = position.x + watermarkDimensions.width / 2
        val centerY = position.y + watermarkDimensions.height / 2

        // Configure opacity and rotation
        val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity)
        val rotationConfig = opacityAndRotationProcessor.createRotationConfig(
            config.rotation, centerX, centerY
        )

        // Apply transformations and draw image
        opacityAndRotationProcessor.saveAndApplyTransformations(
            contentStream, opacityConfig, rotationConfig
        )

        try {
            contentStream.drawImage(
                processedImage.pdImage,
                position.x,
                position.y,
                processedImage.scaledWidth,
                processedImage.scaledHeight
            )
        } finally {
            opacityAndRotationProcessor.restoreGraphicsState(contentStream)
        }
    }

    /**
     * Applies repeated image watermarks to a page.
     */
    private fun applyRepeatedImageWatermark(
        contentStream: PDPageContentStream,
        page: PDPage,
        config: ImageWatermarkConfig,
        processedImage: ProcessedImage
    ) {
        val spacingConfig = SpacerConfigurationService.SpacingConfig(
            horizontalSpacing = config.horizontalSpacing,
            verticalSpacing = config.verticalSpacing,
            spacingType = SpacerConfigurationService.SpacingType.ABSOLUTE
        )

        val gridLayout = spacerConfigurationService.createGridLayout(
            page = page,
            watermarkWidth = processedImage.scaledWidth.toDouble(),
            watermarkHeight = processedImage.scaledHeight.toDouble(),
            spacingConfig = spacingConfig
        )

        val positions = spacerConfigurationService.calculateGridPositions(
            gridLayout = gridLayout,
            margins = spacingConfig.margins
        )

        // Configure opacity once for all watermarks
        val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity)

        opacityAndRotationProcessor.saveAndApplyTransformations(
            contentStream, opacityConfig, null
        )

        try {
            for (pos in positions) {
                if (config.rotation != 0.0) {
                    val centerX = pos.first.toFloat() + processedImage.scaledWidth / 2
                    val centerY = pos.second.toFloat() + processedImage.scaledHeight / 2
                    
                    contentStream.saveGraphicsState()
                    opacityAndRotationProcessor.applyRotation(
                        contentStream, config.rotation, centerX, centerY
                    )
                }

                contentStream.drawImage(
                    processedImage.pdImage,
                    pos.first.toFloat(),
                    pos.second.toFloat(),
                    processedImage.scaledWidth,
                    processedImage.scaledHeight
                )

                if (config.rotation != 0.0) {
                    contentStream.restoreGraphicsState()
                }
            }
        } finally {
            opacityAndRotationProcessor.restoreGraphicsState(contentStream)
        }

        logger.debug("Applied {} repeated image watermarks to page", positions.size)
    }

    /**
     * Processes the image file and converts it to PDImageXObject with scaling.
     */
    private fun processImageFile(document: PDDocument, config: ImageWatermarkConfig): ProcessedImage {
        // Read the image file
        val bufferedImage = try {
            ImageIO.read(ByteArrayInputStream(config.imageFile.bytes))
        } catch (e: IOException) {
            throw RuntimeException("Failed to read image file: ${e.message}", e)
        } ?: throw RuntimeException("Unsupported image format or corrupted image file")

        logger.debug("Processing image: {}x{} pixels", bufferedImage.width, bufferedImage.height)

        // Create PDImageXObject from BufferedImage
        val pdImage = try {
            PDImageXObject.createFromByteArray(document, config.imageFile.bytes, config.imageFile.originalFilename)
        } catch (e: IOException) {
            throw RuntimeException("Failed to create PDF image object: ${e.message}", e)
        }

        val originalWidth = bufferedImage.width.toFloat()
        val originalHeight = bufferedImage.height.toFloat()

        // Calculate scaled dimensions
        val scaledDimensions = calculateScaledDimensions(
            originalWidth, originalHeight, config
        )

        return ProcessedImage(
            pdImage = pdImage,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            scaledWidth = scaledDimensions.first,
            scaledHeight = scaledDimensions.second
        )
    }

    /**
     * Calculates scaled dimensions based on configuration.
     */
    private fun calculateScaledDimensions(
        originalWidth: Float,
        originalHeight: Float,
        config: ImageWatermarkConfig
    ): Pair<Float, Float> {
        var scaledWidth = originalWidth * config.imageScale.toFloat()
        var scaledHeight = originalHeight * config.imageScale.toFloat()

        // Apply maximum width/height constraints if specified
        config.maxWidth?.let { maxWidth ->
            if (scaledWidth > maxWidth) {
                val scaleFactor = maxWidth.toFloat() / scaledWidth
                scaledWidth = maxWidth.toFloat()
                if (config.maintainAspectRatio) {
                    scaledHeight *= scaleFactor
                }
            }
        }

        config.maxHeight?.let { maxHeight ->
            if (scaledHeight > maxHeight) {
                val scaleFactor = maxHeight.toFloat() / scaledHeight
                scaledHeight = maxHeight.toFloat()
                if (config.maintainAspectRatio) {
                    scaledWidth *= scaleFactor
                }
            }
        }

        logger.debug("Scaled image dimensions: {}x{} -> {}x{}", 
            originalWidth, originalHeight, scaledWidth, scaledHeight)

        return Pair(scaledWidth, scaledHeight)
    }

    /**
     * Validates image watermark configuration.
     */
    fun validateImageWatermarkConfig(config: ImageWatermarkConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.imageFile.isEmpty) {
            errors.add("Image file is required and cannot be empty")
        }

        if (config.imageScale <= 0 || config.imageScale > 10) {
            errors.add("Image scale must be between 0.01 and 10.0")
        }

        if (!opacityAndRotationProcessor.isValidOpacity(config.opacity)) {
            errors.add("Opacity must be between 0.0 and 1.0")
        }

        if (config.horizontalSpacing < 0 || config.verticalSpacing < 0) {
            errors.add("Spacing values cannot be negative")
        }

        config.maxWidth?.let { maxWidth ->
            if (maxWidth <= 0) {
                errors.add("Maximum width must be positive")
            }
        }

        config.maxHeight?.let { maxHeight ->
            if (maxHeight <= 0) {
                errors.add("Maximum height must be positive")
            }
        }

        // Validate image format
        val filename = config.imageFile.originalFilename?.lowercase()
        val supportedFormats = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff")
        if (filename != null && supportedFormats.none { filename.endsWith(it) }) {
            errors.add("Unsupported image format. Supported formats: ${supportedFormats.joinToString()}")
        }

        return errors
    }

    /**
     * Creates a default image watermark configuration.
     */
    fun createDefaultConfig(imageFile: MultipartFile): ImageWatermarkConfig {
        return ImageWatermarkConfig(
            imageFile = imageFile,
            imageScale = 1.0,
            opacity = 0.5,
            rotation = 0.0,
            position = "bottom-right",
            maintainAspectRatio = true
        )
    }

    /**
     * Calculates the optimal scale for an image to fit within specified bounds.
     */
    fun calculateOptimalScale(
        imageWidth: Int,
        imageHeight: Int,
        maxWidth: Double,
        maxHeight: Double,
        maintainAspectRatio: Boolean = true
    ): Double {
        if (!maintainAspectRatio) {
            return minOf(maxWidth / imageWidth, maxHeight / imageHeight)
        }

        val widthScale = maxWidth / imageWidth
        val heightScale = maxHeight / imageHeight
        return minOf(widthScale, heightScale, 1.0) // Don't scale up beyond original size
    }

    /**
     * Gets supported image formats.
     */
    fun getSupportedImageFormats(): List<String> {
        return listOf("JPEG", "PNG", "GIF", "BMP", "TIFF", "WEBP")
    }

    /**
     * Checks if an image format is supported.
     */
    fun isSupportedImageFormat(filename: String): Boolean {
        val supportedExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp")
        return supportedExtensions.any { filename.lowercase().endsWith(it) }
    }
}