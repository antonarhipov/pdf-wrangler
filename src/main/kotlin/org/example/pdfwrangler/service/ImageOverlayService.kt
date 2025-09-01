package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.File
import javax.imageio.ImageIO
import java.nio.file.Files

/**
 * Service for applying image overlays to PDF documents with precise positioning control.
 * Supports various overlay operations including scaling, positioning, and opacity control.
 * Task 84: Implement ImageOverlayService with precise positioning control
 */
@Service
class ImageOverlayService(
    private val watermarkPositioningService: WatermarkPositioningService,
    private val opacityAndRotationProcessor: OpacityAndRotationProcessor
) {

    private val logger = LoggerFactory.getLogger(ImageOverlayService::class.java)

    /**
     * Data class for image overlay configuration.
     */
    data class ImageOverlayConfig(
        val overlayImage: MultipartFile,
        val scale: Double = 1.0,
        val opacity: Double = 1.0,
        val position: String = "center",
        val customX: Double? = null,
        val customY: Double? = null,
        val rotation: Double = 0.0,
        val maintainAspectRatio: Boolean = true,
        val maxWidth: Double? = null,
        val maxHeight: Double? = null,
        val blendMode: String = "Normal"
    )

    /**
     * Data class for overlay operation result.
     */
    data class ImageOverlayResult(
        val success: Boolean,
        val message: String,
        val outputFileName: String?,
        val totalPages: Int?,
        val processedFiles: Int,
        val fileSize: Long?
    )

    /**
     * Data class for processed overlay image information.
     */
    data class ProcessedOverlayImage(
        val pdImage: PDImageXObject,
        val originalWidth: Float,
        val originalHeight: Float,
        val scaledWidth: Float,
        val scaledHeight: Float
    )

    /**
     * Applies image overlay to multiple PDF files.
     *
     * @param files List of PDF files to process
     * @param config Image overlay configuration
     * @param pageNumbers List of page numbers to apply overlay (1-based), empty for all pages
     * @return ImageOverlayResult with processing results
     */
    fun applyImageOverlay(
        files: List<MultipartFile>,
        config: ImageOverlayConfig,
        pageNumbers: List<Int> = emptyList()
    ): ImageOverlayResult {
        logger.info("Applying image overlay to {} files", files.size)

        var processedFiles = 0
        var totalPages = 0
        var totalFileSize = 0L

        try {
            for (file in files) {
                logger.debug("Processing file: {}", file.originalFilename)

                // Create temporary file and load PDDocument
                val tempFile = File.createTempFile("overlay_input", ".pdf")
                file.transferTo(tempFile)
                
                Loader.loadPDF(tempFile).use { document ->
                    val processedImage = processImageFile(document, config)
                    val pages = applyOverlayToDocument(document, processedImage, config, pageNumbers)
                    
                    totalPages += pages
                    processedFiles++
                    totalFileSize += file.size
                }
                
                // Clean up temporary file
                tempFile.delete()
            }

            val outputFileName = generateOutputFileName(files.first().originalFilename ?: "overlay_output")

            return ImageOverlayResult(
                success = true,
                message = "Image overlay applied successfully to $processedFiles files",
                outputFileName = outputFileName,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize
            )

        } catch (e: Exception) {
            logger.error("Failed to apply image overlay: {}", e.message, e)
            return ImageOverlayResult(
                success = false,
                message = "Failed to apply image overlay: ${e.message}",
                outputFileName = null,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize
            )
        }
    }

    /**
     * Applies overlay to all specified pages in a document.
     */
    private fun applyOverlayToDocument(
        document: PDDocument,
        processedImage: ProcessedOverlayImage,
        config: ImageOverlayConfig,
        pageNumbers: List<Int>
    ): Int {
        val pagesToProcess = if (pageNumbers.isEmpty()) {
            (1..document.numberOfPages).toList()
        } else {
            pageNumbers.filter { it in 1..document.numberOfPages }
        }

        for (pageNumber in pagesToProcess) {
            val page = document.getPage(pageNumber - 1) // Convert to 0-based
            applyOverlayToPage(document, page, processedImage, config)
        }

        return pagesToProcess.size
    }

    /**
     * Applies image overlay to a single page.
     */
    private fun applyOverlayToPage(
        document: PDDocument,
        page: PDPage,
        processedImage: ProcessedOverlayImage,
        config: ImageOverlayConfig
    ) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
            
            val overlayDimensions = WatermarkPositioningService.WatermarkDimensions(
                width = processedImage.scaledWidth,
                height = processedImage.scaledHeight
            )

            val position = watermarkPositioningService.calculatePosition(
                page = page,
                position = config.position,
                customX = config.customX,
                customY = config.customY,
                watermarkDimensions = overlayDimensions,
                rotation = config.rotation
            )

            // Apply opacity and rotation if needed
            val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(config.opacity, config.blendMode)
            val rotationConfig = if (config.rotation != 0.0) {
                opacityAndRotationProcessor.createRotationConfig(
                    config.rotation,
                    position.x + overlayDimensions.width / 2,
                    position.y + overlayDimensions.height / 2
                )
            } else null

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
                
                logger.debug("Applied image overlay at position ({}, {})", position.x, position.y)

            } finally {
                opacityAndRotationProcessor.restoreGraphicsState(contentStream)
            }
        }
    }

    /**
     * Processes the overlay image file and converts it to PDImageXObject with scaling.
     */
    private fun processImageFile(document: PDDocument, config: ImageOverlayConfig): ProcessedOverlayImage {
        // Read the image file
        val bufferedImage = try {
            ImageIO.read(ByteArrayInputStream(config.overlayImage.bytes))
        } catch (e: IOException) {
            throw RuntimeException("Failed to read overlay image file: ${e.message}", e)
        } ?: throw RuntimeException("Unsupported image format or corrupted overlay image file")

        logger.debug("Processing overlay image: {}x{} pixels", bufferedImage.width, bufferedImage.height)

        // Create PDImageXObject from BufferedImage
        val pdImage = try {
            PDImageXObject.createFromByteArray(
                document, 
                config.overlayImage.bytes, 
                config.overlayImage.originalFilename
            )
        } catch (e: IOException) {
            throw RuntimeException("Failed to create PDF image object: ${e.message}", e)
        }

        val originalWidth = bufferedImage.width.toFloat()
        val originalHeight = bufferedImage.height.toFloat()

        // Calculate scaled dimensions
        val scaledDimensions = calculateScaledDimensions(originalWidth, originalHeight, config)

        return ProcessedOverlayImage(
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
        config: ImageOverlayConfig
    ): Pair<Float, Float> {
        var scaledWidth = originalWidth * config.scale.toFloat()
        var scaledHeight = originalHeight * config.scale.toFloat()

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

        logger.debug("Scaled overlay image dimensions: {}x{} -> {}x{}", 
            originalWidth, originalHeight, scaledWidth, scaledHeight)

        return Pair(scaledWidth, scaledHeight)
    }

    /**
     * Validates image overlay configuration.
     */
    fun validateImageOverlayConfig(config: ImageOverlayConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.overlayImage.isEmpty) {
            errors.add("Overlay image file is required and cannot be empty")
        }

        if (config.scale <= 0 || config.scale > 10) {
            errors.add("Image scale must be between 0.01 and 10.0")
        }

        if (!opacityAndRotationProcessor.isValidOpacity(config.opacity)) {
            errors.add("Opacity must be between 0.0 and 1.0")
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
        val filename = config.overlayImage.originalFilename?.lowercase()
        val supportedFormats = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff")
        if (filename != null && supportedFormats.none { filename.endsWith(it) }) {
            errors.add("Unsupported image format. Supported formats: ${supportedFormats.joinToString()}")
        }

        // Validate blend mode
        if (!opacityAndRotationProcessor.isValidBlendMode(config.blendMode)) {
            errors.add("Invalid blend mode: ${config.blendMode}")
        }

        return errors
    }

    /**
     * Creates a default image overlay configuration.
     */
    fun createDefaultConfig(overlayImage: MultipartFile): ImageOverlayConfig {
        return ImageOverlayConfig(
            overlayImage = overlayImage,
            scale = 1.0,
            opacity = 0.8,
            position = "center",
            rotation = 0.0,
            maintainAspectRatio = true,
            blendMode = "Normal"
        )
    }

    /**
     * Calculates optimal scale for an image to fit within page bounds.
     */
    fun calculateOptimalScale(
        imageWidth: Int,
        imageHeight: Int,
        pageWidth: Float,
        pageHeight: Float,
        maxCoverage: Double = 0.3 // Maximum percentage of page to cover
    ): Double {
        val maxWidth = pageWidth * maxCoverage
        val maxHeight = pageHeight * maxCoverage
        
        val widthScale = maxWidth / imageWidth
        val heightScale = maxHeight / imageHeight
        return minOf(widthScale, heightScale, 1.0).toDouble() // Don't scale up beyond original size
    }

    /**
     * Gets supported image formats for overlays.
     */
    fun getSupportedImageFormats(): List<String> {
        return listOf("JPEG", "PNG", "GIF", "BMP", "TIFF")
    }

    /**
     * Checks if an image format is supported for overlays.
     */
    fun isSupportedImageFormat(filename: String): Boolean {
        val supportedExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff")
        return supportedExtensions.any { filename.lowercase().endsWith(it) }
    }

    /**
     * Calculates the best position for overlay based on page content analysis.
     */
    fun calculateBestPosition(
        page: PDPage,
        overlayDimensions: WatermarkPositioningService.WatermarkDimensions
    ): String {
        // Simple heuristic: place in bottom-right for typical documents
        // In a more advanced implementation, this could analyze page content
        // to find the best placement that doesn't obscure important text
        
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height
        
        // Check if there's enough space in bottom-right
        val marginBuffer = 100f
        if (pageWidth - overlayDimensions.width - marginBuffer > 0 &&
            pageHeight - overlayDimensions.height - marginBuffer > 0) {
            return "bottom-right"
        }
        
        // Fallback to center if bottom-right doesn't fit
        return "center"
    }

    /**
     * Generates output filename for processed file.
     */
    private fun generateOutputFileName(originalFileName: String): String {
        val baseName = originalFileName.substringBeforeLast(".")
        val extension = originalFileName.substringAfterLast(".", "pdf")
        return "overlay_${baseName}.${extension}"
    }

    /**
     * Estimates processing time based on file size and configuration.
     */
    fun estimateProcessingTime(
        files: List<MultipartFile>,
        config: ImageOverlayConfig
    ): Long {
        val totalSize = files.sumOf { it.size }
        val baseTimePerMB = 500L // milliseconds
        val complexityFactor = when {
            config.rotation != 0.0 -> 1.5
            config.opacity < 1.0 -> 1.2
            else -> 1.0
        }
        
        return ((totalSize / (1024 * 1024)) * baseTimePerMB * complexityFactor).toLong()
    }
}