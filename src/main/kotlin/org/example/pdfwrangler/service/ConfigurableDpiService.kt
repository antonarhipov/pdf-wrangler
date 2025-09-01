package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.geom.AffineTransform
import kotlin.math.roundToInt

/**
 * Service for configurable DPI (Dots Per Inch) settings and resolution control.
 * Handles image scaling and quality adjustments based on DPI requirements.
 */
@Service
class ConfigurableDpiService {

    private val logger = LoggerFactory.getLogger(ConfigurableDpiService::class.java)
    
    companion object {
        const val DEFAULT_DPI = 72
        const val PRINT_DPI = 300
        const val HIGH_QUALITY_DPI = 600
        const val WEB_DPI = 96
        const val SCREEN_DPI = 150
    }

    /**
     * Apply DPI settings to an image, scaling as necessary.
     */
    fun applyDpiSettings(image: BufferedImage, targetDpi: Int): BufferedImage {
        logger.debug("Applying DPI settings: $targetDpi to image of size ${image.width}x${image.height}")
        
        // Validate DPI range
        val validatedDpi = validateDpi(targetDpi)
        if (validatedDpi != targetDpi) {
            logger.warn("DPI $targetDpi out of range, using $validatedDpi")
        }
        
        // If the image is already at the correct "logical" DPI, return as-is
        // In practice, we assume input images are at 72 DPI and scale accordingly
        val scaleFactor = validatedDpi / DEFAULT_DPI.toDouble()
        
        return if (scaleFactor == 1.0) {
            image
        } else {
            scaleImageForDpi(image, scaleFactor, validatedDpi)
        }
    }

    /**
     * Scale image based on DPI requirements with quality preservation.
     */
    private fun scaleImageForDpi(image: BufferedImage, scaleFactor: Double, targetDpi: Int): BufferedImage {
        val newWidth = (image.width * scaleFactor).roundToInt()
        val newHeight = (image.height * scaleFactor).roundToInt()
        
        logger.debug("Scaling image from ${image.width}x${image.height} to ${newWidth}x${newHeight} for DPI $targetDpi")
        
        val scaledImage = BufferedImage(newWidth, newHeight, image.type)
        val g2d = scaledImage.createGraphics()
        
        // Configure high-quality rendering
        configureHighQualityRendering(g2d)
        
        // Apply scaling transformation
        val transform = AffineTransform.getScaleInstance(scaleFactor, scaleFactor)
        g2d.transform = transform
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        
        return scaledImage
    }

    /**
     * Configure graphics context for high-quality rendering.
     */
    private fun configureHighQualityRendering(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    /**
     * Validate DPI value and return a corrected value if necessary.
     */
    fun validateDpi(dpi: Int): Int {
        return when {
            dpi < 72 -> {
                logger.warn("DPI $dpi is too low, minimum is 72")
                72
            }
            dpi > 600 -> {
                logger.warn("DPI $dpi is too high, maximum is 600")
                600
            }
            else -> dpi
        }
    }

    /**
     * Get recommended DPI for different use cases.
     */
    fun getRecommendedDpi(useCase: String): Int {
        return when (useCase.uppercase()) {
            "WEB" -> WEB_DPI
            "SCREEN" -> SCREEN_DPI
            "PRINT" -> PRINT_DPI
            "HIGH_QUALITY" -> HIGH_QUALITY_DPI
            "DEFAULT" -> DEFAULT_DPI
            else -> {
                logger.warn("Unknown use case: $useCase, returning default DPI")
                DEFAULT_DPI
            }
        }
    }

    /**
     * Calculate output image dimensions for given DPI and page size.
     */
    fun calculateImageDimensions(pageWidthInches: Double, pageHeightInches: Double, dpi: Int): Pair<Int, Int> {
        val validatedDpi = validateDpi(dpi)
        val width = (pageWidthInches * validatedDpi).roundToInt()
        val height = (pageHeightInches * validatedDpi).roundToInt()
        
        logger.debug("Calculated dimensions: ${width}x${height} for page size ${pageWidthInches}x${pageHeightInches} inches at $validatedDpi DPI")
        return Pair(width, height)
    }

    /**
     * Calculate estimated file size based on DPI and format.
     */
    fun estimateFileSizeBytes(widthInches: Double, heightInches: Double, dpi: Int, format: String): Long {
        val (width, height) = calculateImageDimensions(widthInches, heightInches, dpi)
        val pixels = width.toLong() * height.toLong()
        
        val bytesPerPixel = when (format.uppercase()) {
            "PNG" -> 4L // RGBA
            "JPG", "JPEG" -> 3L // RGB
            "GIF" -> 1L // Indexed color
            "BMP" -> 3L // RGB
            "WEBP" -> 3L // RGB (compressed)
            "TIFF" -> 4L // RGBA (uncompressed)
            else -> 3L // Default to RGB
        }
        
        val rawSize = pixels * bytesPerPixel
        
        // Apply compression estimates
        val compressionFactor = when (format.uppercase()) {
            "PNG" -> 0.6 // PNG compression
            "JPG", "JPEG" -> 0.1 // JPEG compression
            "GIF" -> 0.4 // GIF compression
            "WEBP" -> 0.08 // WebP compression
            "TIFF" -> 1.0 // No compression
            "BMP" -> 1.0 // No compression
            else -> 0.3 // Conservative estimate
        }
        
        val estimatedSize = (rawSize * compressionFactor).toLong()
        logger.debug("Estimated file size: $estimatedSize bytes for ${width}x${height} $format image")
        
        return estimatedSize
    }

    /**
     * Apply DPI-specific optimizations based on target DPI range.
     */
    fun applyDpiOptimizations(image: BufferedImage, targetDpi: Int): BufferedImage {
        return when {
            targetDpi <= 96 -> {
                logger.debug("Applying web optimizations for low DPI")
                optimizeForWeb(image)
            }
            targetDpi <= 150 -> {
                logger.debug("Applying screen optimizations for medium DPI")
                optimizeForScreen(image)
            }
            targetDpi <= 300 -> {
                logger.debug("Applying print optimizations for high DPI")
                optimizeForPrint(image)
            }
            else -> {
                logger.debug("Applying high-quality optimizations for very high DPI")
                optimizeForHighQuality(image)
            }
        }
    }

    /**
     * Optimize image for web display (low DPI).
     */
    private fun optimizeForWeb(image: BufferedImage): BufferedImage {
        // For web, prioritize smaller file sizes and faster loading
        // Apply slight smoothing to reduce artifacts at low resolution
        return applySmoothingFilter(image, 0.5f)
    }

    /**
     * Optimize image for screen display (medium DPI).
     */
    private fun optimizeForScreen(image: BufferedImage): BufferedImage {
        // Balanced approach for screen viewing
        return applySmoothingFilter(image, 0.3f)
    }

    /**
     * Optimize image for print (high DPI).
     */
    private fun optimizeForPrint(image: BufferedImage): BufferedImage {
        // For print, maintain sharpness and detail
        return applySharpeningFilter(image, 0.3f)
    }

    /**
     * Optimize image for high-quality output (very high DPI).
     */
    private fun optimizeForHighQuality(image: BufferedImage): BufferedImage {
        // Maximum quality preservation
        return applySharpeningFilter(image, 0.5f)
    }

    /**
     * Apply smoothing filter to reduce artifacts.
     */
    private fun applySmoothingFilter(image: BufferedImage, intensity: Float): BufferedImage {
        if (intensity <= 0) return image
        
        val smoothed = BufferedImage(image.width, image.height, image.type)
        val g2d = smoothed.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        
        return smoothed
    }

    /**
     * Apply sharpening filter to enhance detail.
     */
    private fun applySharpeningFilter(image: BufferedImage, intensity: Float): BufferedImage {
        if (intensity <= 0) return image
        
        // Simple unsharp mask implementation
        val sharpened = BufferedImage(image.width, image.height, image.type)
        
        for (x in 1 until image.width - 1) {
            for (y in 1 until image.height - 1) {
                val center = image.getRGB(x, y)
                val neighbors = arrayOf(
                    image.getRGB(x - 1, y),
                    image.getRGB(x + 1, y),
                    image.getRGB(x, y - 1),
                    image.getRGB(x, y + 1)
                )
                
                val sharpenedPixelValue = applyUnsharpMask(center, neighbors, intensity)
                sharpened.setRGB(x, y, sharpenedPixelValue)
            }
        }
        
        // Copy edges unchanged
        for (x in 0 until image.width) {
            sharpened.setRGB(x, 0, image.getRGB(x, 0))
            sharpened.setRGB(x, image.height - 1, image.getRGB(x, image.height - 1))
        }
        for (y in 0 until image.height) {
            sharpened.setRGB(0, y, image.getRGB(0, y))
            sharpened.setRGB(image.width - 1, y, image.getRGB(image.width - 1, y))
        }
        
        return sharpened
    }

    /**
     * Apply unsharp mask algorithm to a pixel.
     */
    private fun applyUnsharpMask(center: Int, neighbors: Array<Int>, intensity: Float): Int {
        val centerR = (center shr 16) and 0xFF
        val centerG = (center shr 8) and 0xFF
        val centerB = center and 0xFF
        
        val avgR = neighbors.map { (it shr 16) and 0xFF }.average()
        val avgG = neighbors.map { (it shr 8) and 0xFF }.average()
        val avgB = neighbors.map { it and 0xFF }.average()
        
        val sharpenedR = (centerR + intensity * (centerR - avgR)).roundToInt().coerceIn(0, 255)
        val sharpenedG = (centerG + intensity * (centerG - avgG)).roundToInt().coerceIn(0, 255)
        val sharpenedB = (centerB + intensity * (centerB - avgB)).roundToInt().coerceIn(0, 255)
        
        return (sharpenedR shl 16) or (sharpenedG shl 8) or sharpenedB
    }

    /**
     * Get DPI configuration information.
     */
    fun getDpiConfigurationInfo(): Map<String, Any> {
        return mapOf(
            "supportedRange" to mapOf("min" to 72, "max" to 600),
            "recommendedValues" to mapOf(
                "web" to WEB_DPI,
                "screen" to SCREEN_DPI,
                "print" to PRINT_DPI,
                "highQuality" to HIGH_QUALITY_DPI,
                "default" to DEFAULT_DPI
            ),
            "useCaseRecommendations" to mapOf(
                "web" to "Use 96 DPI for web display and faster loading",
                "screen" to "Use 150 DPI for general screen viewing",
                "print" to "Use 300 DPI for print-quality output",
                "highQuality" to "Use 600 DPI for professional printing or archival purposes"
            )
        )
    }
}