package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp
import java.awt.color.ColorSpace

/**
 * Service for processing image color modes.
 * Supports RGB, Greyscale, and Black & White conversions.
 */
@Service
class ColorModeProcessor {

    private val logger = LoggerFactory.getLogger(ColorModeProcessor::class.java)

    /**
     * Apply color mode transformation to an image.
     */
    fun applyColorMode(image: BufferedImage, colorMode: String): BufferedImage {
        logger.debug("Applying color mode: $colorMode to image of size ${image.width}x${image.height}")
        
        return when (colorMode.uppercase()) {
            "RGB" -> ensureRgbImage(image)
            "GRAYSCALE" -> convertToGrayscale(image)
            "BLACK_WHITE" -> convertToBlackAndWhite(image)
            else -> {
                logger.warn("Unknown color mode: $colorMode, defaulting to RGB")
                ensureRgbImage(image)
            }
        }
    }

    /**
     * Convert image to RGB format if it isn't already.
     */
    private fun ensureRgbImage(image: BufferedImage): BufferedImage {
        return if (image.type == BufferedImage.TYPE_INT_RGB) {
            image
        } else {
            logger.debug("Converting image to RGB format")
            val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g2d = rgbImage.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()
            rgbImage
        }
    }

    /**
     * Convert image to grayscale.
     */
    private fun convertToGrayscale(image: BufferedImage): BufferedImage {
        logger.debug("Converting image to grayscale")
        
        return try {
            // Use ColorConvertOp for high-quality grayscale conversion
            val colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY)
            val colorConvertOp = ColorConvertOp(colorSpace, null)
            
            // Create destination image
            val grayImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
            colorConvertOp.filter(image, grayImage)
            
            grayImage
        } catch (ex: Exception) {
            logger.warn("ColorConvertOp failed, using manual grayscale conversion: ${ex.message}")
            convertToGrayscaleManual(image)
        }
    }

    /**
     * Manual grayscale conversion as fallback.
     */
    private fun convertToGrayscaleManual(image: BufferedImage): BufferedImage {
        val grayImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g2d = grayImage.createGraphics()
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        return grayImage
    }

    /**
     * Convert image to black and white using threshold-based conversion.
     */
    private fun convertToBlackAndWhite(image: BufferedImage, threshold: Int = 128): BufferedImage {
        logger.debug("Converting image to black and white with threshold: $threshold")
        
        // First convert to grayscale
        val grayImage = if (image.type == BufferedImage.TYPE_BYTE_GRAY) {
            image
        } else {
            convertToGrayscale(image)
        }
        
        // Then apply threshold to create black and white
        val bwImage = BufferedImage(grayImage.width, grayImage.height, BufferedImage.TYPE_BYTE_BINARY)
        
        for (x in 0 until grayImage.width) {
            for (y in 0 until grayImage.height) {
                val rgb = grayImage.getRGB(x, y)
                val gray = Color(rgb).red // In grayscale, R=G=B, so we can use any channel
                
                val bwColor = if (gray > threshold) Color.WHITE else Color.BLACK
                bwImage.setRGB(x, y, bwColor.rgb)
            }
        }
        
        return bwImage
    }

    /**
     * Convert image to black and white with custom threshold.
     */
    fun convertToBlackAndWhite(image: BufferedImage): BufferedImage {
        return convertToBlackAndWhite(image, 128)
    }

    /**
     * Convert image to black and white with specified threshold (0-255).
     */
    fun convertToBlackAndWhiteWithThreshold(image: BufferedImage, threshold: Int): BufferedImage {
        val validThreshold = threshold.coerceIn(0, 255)
        if (threshold != validThreshold) {
            logger.warn("Threshold $threshold out of range, using $validThreshold")
        }
        return convertToBlackAndWhite(image, validThreshold)
    }

    /**
     * Get optimal threshold for black and white conversion using Otsu's method.
     */
    fun calculateOptimalThreshold(image: BufferedImage): Int {
        logger.debug("Calculating optimal threshold using Otsu's method")
        
        // Convert to grayscale if needed
        val grayImage = if (image.type == BufferedImage.TYPE_BYTE_GRAY) {
            image
        } else {
            convertToGrayscale(image)
        }
        
        // Create histogram
        val histogram = IntArray(256)
        for (x in 0 until grayImage.width) {
            for (y in 0 until grayImage.height) {
                val rgb = grayImage.getRGB(x, y)
                val gray = Color(rgb).red
                histogram[gray]++
            }
        }
        
        // Apply Otsu's algorithm
        val totalPixels = grayImage.width * grayImage.height
        var sum = 0.0
        for (i in 0..255) {
            sum += i * histogram[i]
        }
        
        var sumBackground = 0.0
        var weightBackground = 0
        var maxVariance = 0.0
        var threshold = 0
        
        for (t in 0..255) {
            weightBackground += histogram[t]
            if (weightBackground == 0) continue
            
            val weightForeground = totalPixels - weightBackground
            if (weightForeground == 0) break
            
            sumBackground += t * histogram[t]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground
            
            val variance = weightBackground * weightForeground * (meanBackground - meanForeground) * (meanBackground - meanForeground)
            
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }
        
        logger.debug("Calculated optimal threshold: $threshold")
        return threshold
    }

    /**
     * Apply adaptive color mode conversion with quality settings.
     */
    fun applyColorModeWithQuality(image: BufferedImage, colorMode: String, quality: Float): BufferedImage {
        val processedImage = applyColorMode(image, colorMode)
        
        // Apply quality-based post-processing if needed
        return if (quality < 0.8 && colorMode.uppercase() != "BLACK_WHITE") {
            applyQualityReduction(processedImage, quality)
        } else {
            processedImage
        }
    }

    /**
     * Apply quality reduction through controlled dithering or color reduction.
     */
    private fun applyQualityReduction(image: BufferedImage, quality: Float): BufferedImage {
        if (quality >= 1.0) return image
        
        logger.debug("Applying quality reduction with factor: $quality")
        
        // Simple quality reduction by reducing color depth
        val reducedImage = BufferedImage(image.width, image.height, image.type)
        val reductionFactor = (1.0 / quality).toInt().coerceAtLeast(2)
        
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val rgb = image.getRGB(x, y)
                val color = Color(rgb)
                
                val newR = (color.red / reductionFactor) * reductionFactor
                val newG = (color.green / reductionFactor) * reductionFactor
                val newB = (color.blue / reductionFactor) * reductionFactor
                
                val reducedColor = Color(
                    newR.coerceIn(0, 255),
                    newG.coerceIn(0, 255),
                    newB.coerceIn(0, 255)
                )
                
                reducedImage.setRGB(x, y, reducedColor.rgb)
            }
        }
        
        return reducedImage
    }

    /**
     * Check if image is already in the specified color mode.
     */
    fun isImageInColorMode(image: BufferedImage, colorMode: String): Boolean {
        return when (colorMode.uppercase()) {
            "RGB" -> image.type == BufferedImage.TYPE_INT_RGB
            "GRAYSCALE" -> image.type == BufferedImage.TYPE_BYTE_GRAY
            "BLACK_WHITE" -> image.type == BufferedImage.TYPE_BYTE_BINARY
            else -> false
        }
    }

    /**
     * Get color mode information for an image.
     */
    fun getImageColorInfo(image: BufferedImage): Map<String, Any> {
        val colorModel = image.colorModel
        
        return mapOf(
            "type" to image.type,
            "hasAlpha" to colorModel.hasAlpha(),
            "colorSpace" to colorModel.colorSpace.type,
            "numComponents" to colorModel.numComponents,
            "pixelSize" to colorModel.pixelSize,
            "suggestedColorMode" to when (image.type) {
                BufferedImage.TYPE_BYTE_GRAY -> "GRAYSCALE"
                BufferedImage.TYPE_BYTE_BINARY -> "BLACK_WHITE"
                else -> "RGB"
            }
        )
    }
}