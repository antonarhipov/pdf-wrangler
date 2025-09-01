package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Service for color manipulation operations on PDF documents.
 * Supports inversion, grayscale, sepia, brightness, contrast, saturation, and hue adjustments.
 * Task 86: Create ColorManipulationService with inversion and adjustment capabilities
 */
@Service
class ColorManipulationService {

    private val logger = LoggerFactory.getLogger(ColorManipulationService::class.java)

    /**
     * Data class for color manipulation configuration.
     */
    data class ColorConfig(
        val operation: String, // "invert", "grayscale", "sepia", "brightness", "contrast", "saturation", "hue"
        val intensity: Double = 1.0, // For brightness, contrast, saturation (0.0 to 2.0)
        val hueShift: Double = 0.0, // For hue adjustment (-180 to 180 degrees)
        val preserveTransparency: Boolean = true,
        val applyToImages: Boolean = true,
        val applyToText: Boolean = false, // Limited support for text color manipulation
        val quality: String = "high" // "low", "medium", "high"
    )

    /**
     * Data class for color manipulation result.
     */
    data class ColorManipulationResult(
        val success: Boolean,
        val message: String,
        val outputFileName: String?,
        val totalPages: Int?,
        val processedFiles: Int,
        val fileSize: Long?,
        val operationApplied: String
    )

    /**
     * Supported color operations with their descriptions.
     */
    private val supportedOperations = mapOf(
        "invert" to "Inverts all colors in the document",
        "grayscale" to "Converts the document to grayscale",
        "sepia" to "Applies a sepia tone effect",
        "brightness" to "Adjusts brightness levels",
        "contrast" to "Adjusts contrast levels",
        "saturation" to "Adjusts color saturation",
        "hue" to "Shifts hue values"
    )

    /**
     * Applies color manipulation to multiple PDF files.
     *
     * @param files List of PDF files to process
     * @param config Color manipulation configuration
     * @param pageNumbers List of page numbers to process (1-based), empty for all pages
     * @return ColorManipulationResult with processing results
     */
    fun applyColorManipulation(
        files: List<MultipartFile>,
        config: ColorConfig,
        pageNumbers: List<Int> = emptyList()
    ): ColorManipulationResult {
        logger.info("Applying color manipulation '{}' to {} files", config.operation, files.size)

        var processedFiles = 0
        var totalPages = 0
        var totalFileSize = 0L

        try {
            for (file in files) {
                logger.debug("Processing file: {} with operation: {}", file.originalFilename, config.operation)

                val tempFile = File.createTempFile("color_input", ".pdf")
                file.transferTo(tempFile)
                
                Loader.loadPDF(tempFile).use { document ->
                    val pages = applyColorManipulationToDocument(document, config, pageNumbers)
                    totalPages += pages
                    processedFiles++
                    totalFileSize += file.size
                }
                
                tempFile.delete()
            }

            val outputFileName = generateOutputFileName(
                files.first().originalFilename ?: "color_output", 
                config.operation
            )

            return ColorManipulationResult(
                success = true,
                message = "Color manipulation '${config.operation}' applied successfully to $processedFiles files",
                outputFileName = outputFileName,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize,
                operationApplied = config.operation
            )

        } catch (e: Exception) {
            logger.error("Failed to apply color manipulation: {}", e.message, e)
            return ColorManipulationResult(
                success = false,
                message = "Failed to apply color manipulation: ${e.message}",
                outputFileName = null,
                totalPages = totalPages,
                processedFiles = processedFiles,
                fileSize = totalFileSize,
                operationApplied = config.operation
            )
        }
    }

    /**
     * Applies color manipulation to all specified pages in a document.
     */
    private fun applyColorManipulationToDocument(
        document: PDDocument,
        config: ColorConfig,
        pageNumbers: List<Int>
    ): Int {
        val pagesToProcess = if (pageNumbers.isEmpty()) {
            (1..document.numberOfPages).toList()
        } else {
            pageNumbers.filter { it in 1..document.numberOfPages }
        }

        for (pageNumber in pagesToProcess) {
            val page = document.getPage(pageNumber - 1) // Convert to 0-based
            applyColorManipulationToPage(document, page, config)
        }

        return pagesToProcess.size
    }

    /**
     * Applies color manipulation to a single page.
     */
    private fun applyColorManipulationToPage(
        document: PDDocument,
        page: PDPage,
        config: ColorConfig
    ) {
        try {
            when (config.operation.lowercase()) {
                "invert" -> invertPageColors(document, page, config)
                "grayscale" -> convertPageToGrayscale(document, page, config)
                "sepia" -> applySepiaTone(document, page, config)
                "brightness" -> adjustBrightness(document, page, config)
                "contrast" -> adjustContrast(document, page, config)
                "saturation" -> adjustSaturation(document, page, config)
                "hue" -> shiftHue(document, page, config)
                else -> {
                    logger.warn("Unsupported color operation: {}", config.operation)
                    throw IllegalArgumentException("Unsupported color operation: ${config.operation}")
                }
            }
            
            logger.debug("Applied {} color manipulation to page", config.operation)

        } catch (e: Exception) {
            logger.error("Failed to apply color manipulation to page: {}", e.message)
            throw RuntimeException("Failed to process page with color manipulation", e)
        }
    }

    /**
     * Inverts colors on a page by rendering and re-inserting as image.
     */
    private fun invertPageColors(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val invertedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    val invertedColor = Color(
                        255 - color.red,
                        255 - color.green,
                        255 - color.blue
                    )
                    invertedImage.setRGB(x, y, invertedColor.rgb)
                }
            }
            invertedImage
        }
    }

    /**
     * Converts page to grayscale.
     */
    private fun convertPageToGrayscale(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val grayscaleImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    
                    // Use luminance formula: 0.299*R + 0.587*G + 0.114*B
                    val gray = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue).toInt()
                    val grayscaleColor = Color(gray, gray, gray)
                    
                    grayscaleImage.setRGB(x, y, grayscaleColor.rgb)
                }
            }
            grayscaleImage
        }
    }

    /**
     * Applies sepia tone effect to a page.
     */
    private fun applySepiaTone(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val sepiaImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    
                    // Sepia transformation matrix
                    val tr = (0.393 * color.red + 0.769 * color.green + 0.189 * color.blue).toInt().coerceAtMost(255)
                    val tg = (0.349 * color.red + 0.686 * color.green + 0.168 * color.blue).toInt().coerceAtMost(255)
                    val tb = (0.272 * color.red + 0.534 * color.green + 0.131 * color.blue).toInt().coerceAtMost(255)
                    
                    val sepiaColor = Color(tr, tg, tb)
                    sepiaImage.setRGB(x, y, sepiaColor.rgb)
                }
            }
            sepiaImage
        }
    }

    /**
     * Adjusts brightness of a page.
     */
    private fun adjustBrightness(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val brightnessImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            
            val brightnessFactor = config.intensity.toFloat()

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    
                    val newRed = (color.red * brightnessFactor).toInt().coerceIn(0, 255)
                    val newGreen = (color.green * brightnessFactor).toInt().coerceIn(0, 255)
                    val newBlue = (color.blue * brightnessFactor).toInt().coerceIn(0, 255)
                    
                    val adjustedColor = Color(newRed, newGreen, newBlue)
                    brightnessImage.setRGB(x, y, adjustedColor.rgb)
                }
            }
            brightnessImage
        }
    }

    /**
     * Adjusts contrast of a page.
     */
    private fun adjustContrast(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val contrastImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            
            val contrastFactor = config.intensity.toFloat()
            val factor = (259 * (contrastFactor * 255 + 255)) / (255 * (259 - contrastFactor * 255))

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    
                    val newRed = (factor * (color.red - 128) + 128).toInt().coerceIn(0, 255)
                    val newGreen = (factor * (color.green - 128) + 128).toInt().coerceIn(0, 255)
                    val newBlue = (factor * (color.blue - 128) + 128).toInt().coerceIn(0, 255)
                    
                    val adjustedColor = Color(newRed, newGreen, newBlue)
                    contrastImage.setRGB(x, y, adjustedColor.rgb)
                }
            }
            contrastImage
        }
    }

    /**
     * Adjusts saturation of a page.
     */
    private fun adjustSaturation(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val saturationImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            
            val saturationFactor = config.intensity.toFloat()

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    
                    // Convert to HSB, adjust saturation, convert back
                    val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
                    val newSaturation = (hsb[1] * saturationFactor).coerceIn(0.0f, 1.0f)
                    val adjustedColor = Color.getHSBColor(hsb[0], newSaturation, hsb[2])
                    
                    saturationImage.setRGB(x, y, adjustedColor.rgb)
                }
            }
            saturationImage
        }
    }

    /**
     * Shifts hue of a page.
     */
    private fun shiftHue(document: PDDocument, page: PDPage, config: ColorConfig) {
        processPageAsImage(document, page, config) { image ->
            val width = image.width
            val height = image.height
            val hueImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            
            val hueShift = (config.hueShift / 360.0).toFloat()

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val rgb = image.getRGB(x, y)
                    val color = Color(rgb)
                    
                    // Convert to HSB, adjust hue, convert back
                    val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
                    var newHue = hsb[0] + hueShift
                    
                    // Wrap hue to [0, 1] range
                    if (newHue > 1.0f) newHue -= 1.0f
                    if (newHue < 0.0f) newHue += 1.0f
                    
                    val adjustedColor = Color.getHSBColor(newHue, hsb[1], hsb[2])
                    hueImage.setRGB(x, y, adjustedColor.rgb)
                }
            }
            hueImage
        }
    }

    /**
     * Helper method to process a page as an image and apply transformations.
     */
    private fun processPageAsImage(
        document: PDDocument, 
        page: PDPage, 
        config: ColorConfig,
        transformation: (BufferedImage) -> BufferedImage
    ) {
        try {
            // Create a temporary document with just this page for rendering
            val tempDoc = PDDocument()
            tempDoc.addPage(page)
            
            // Render page to image
            val renderer = PDFRenderer(tempDoc)
            val dpi = when (config.quality) {
                "high" -> 300f
                "medium" -> 150f
                else -> 72f
            }
            
            val originalImage = renderer.renderImageWithDPI(0, dpi)
            
            // Apply transformation
            val transformedImage = transformation(originalImage)
            
            // Convert back to PDF content
            replacePageWithImage(document, page, transformedImage)
            
            tempDoc.close()
            
        } catch (e: Exception) {
            logger.error("Failed to process page as image: {}", e.message)
            throw RuntimeException("Failed to apply color transformation", e)
        }
    }

    /**
     * Replaces page content with a processed image.
     */
    private fun replacePageWithImage(document: PDDocument, page: PDPage, image: BufferedImage) {
        try {
            // Create PDImageXObject from BufferedImage
            val tempFile = File.createTempFile("color_temp", ".png")
            ImageIO.write(image, "PNG", tempFile)
            
            val pdImage = PDImageXObject.createFromFile(tempFile.absolutePath, document)
            
            // Clear existing page content and add the image
            val mediaBox = page.mediaBox
            
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, true).use { contentStream ->
                contentStream.drawImage(pdImage, 0f, 0f, mediaBox.width, mediaBox.height)
            }
            
            tempFile.delete()
            
        } catch (e: Exception) {
            logger.error("Failed to replace page with processed image: {}", e.message)
            throw RuntimeException("Failed to update page content", e)
        }
    }

    /**
     * Validates color manipulation configuration.
     */
    fun validateColorConfig(config: ColorConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.operation !in supportedOperations.keys) {
            errors.add("Unsupported color operation: ${config.operation}. Supported: ${supportedOperations.keys.joinToString()}")
        }

        when (config.operation.lowercase()) {
            "brightness", "contrast", "saturation" -> {
                if (config.intensity < 0.0 || config.intensity > 2.0) {
                    errors.add("Intensity for ${config.operation} must be between 0.0 and 2.0")
                }
            }
            "hue" -> {
                if (config.hueShift < -180.0 || config.hueShift > 180.0) {
                    errors.add("Hue shift must be between -180 and 180 degrees")
                }
            }
        }

        if (config.quality !in listOf("low", "medium", "high")) {
            errors.add("Quality must be 'low', 'medium', or 'high'")
        }

        return errors
    }

    /**
     * Gets supported color operations.
     */
    fun getSupportedOperations(): Map<String, String> {
        return supportedOperations
    }

    /**
     * Creates a default color configuration.
     */
    fun createDefaultConfig(operation: String): ColorConfig {
        return when (operation.lowercase()) {
            "brightness" -> ColorConfig(operation, intensity = 1.2)
            "contrast" -> ColorConfig(operation, intensity = 1.3)
            "saturation" -> ColorConfig(operation, intensity = 1.5)
            "hue" -> ColorConfig(operation, hueShift = 30.0)
            else -> ColorConfig(operation, intensity = 1.0)
        }
    }

    /**
     * Generates output filename with operation suffix.
     */
    private fun generateOutputFileName(originalFileName: String, operation: String): String {
        val baseName = originalFileName.substringBeforeLast(".")
        val extension = originalFileName.substringAfterLast(".", "pdf")
        return "${baseName}_${operation}.${extension}"
    }

    /**
     * Estimates processing time based on file size and operation complexity.
     */
    fun estimateProcessingTime(files: List<MultipartFile>, config: ColorConfig): Long {
        val totalSize = files.sumOf { it.size }
        val baseTimePerMB = when (config.operation.lowercase()) {
            "grayscale", "sepia" -> 2000L // Simpler operations
            "invert", "brightness", "contrast" -> 2500L // Medium complexity
            "saturation", "hue" -> 3000L // More complex operations
            else -> 2000L
        }
        
        val qualityMultiplier = when (config.quality) {
            "high" -> 2.0
            "medium" -> 1.5
            else -> 1.0
        }
        
        return ((totalSize / (1024 * 1024)) * baseTimePerMB * qualityMultiplier).toLong()
    }

    /**
     * Checks if operation requires high computational resources.
     */
    fun isResourceIntensiveOperation(operation: String): Boolean {
        return operation.lowercase() in listOf("saturation", "hue", "contrast")
    }
}