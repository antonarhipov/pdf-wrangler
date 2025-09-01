package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.cos
import kotlin.math.sin

/**
 * Service for calculating watermark positions on PDF pages.
 * Supports flexible placement options including predefined positions and custom coordinates.
 * Task 76: Create WatermarkPositioningService for flexible placement options
 */
@Service
class WatermarkPositioningService {

    private val logger = LoggerFactory.getLogger(WatermarkPositioningService::class.java)

    /**
     * Position enumeration for predefined watermark positions.
     */
    enum class Position {
        CENTER,
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
        CUSTOM
    }

    /**
     * Data class representing watermark position coordinates.
     */
    data class WatermarkPosition(
        val x: Float,
        val y: Float,
        val pageWidth: Float,
        val pageHeight: Float
    )

    /**
     * Data class for watermark dimensions.
     */
    data class WatermarkDimensions(
        val width: Float,
        val height: Float
    )

    /**
     * Calculates the position for a watermark on a PDF page.
     *
     * @param page The PDF page
     * @param position The position type (center, top-left, etc., or custom)
     * @param customX Custom X coordinate (0.0 to 1.0, where 0.0 is left edge)
     * @param customY Custom Y coordinate (0.0 to 1.0, where 0.0 is bottom edge)
     * @param watermarkDimensions The dimensions of the watermark
     * @param rotation Rotation angle in degrees
     * @return WatermarkPosition with calculated coordinates
     */
    fun calculatePosition(
        page: PDPage,
        position: String,
        customX: Double? = null,
        customY: Double? = null,
        watermarkDimensions: WatermarkDimensions,
        rotation: Double = 0.0
    ): WatermarkPosition {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        logger.debug("Calculating watermark position for page {}x{}, position: {}, rotation: {}°",
            pageWidth, pageHeight, position, rotation)

        val positionEnum = try {
            Position.valueOf(position.uppercase().replace("-", "_"))
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid position '{}', defaulting to CENTER", position)
            Position.CENTER
        }

        val (baseX, baseY) = when (positionEnum) {
            Position.CENTER -> Pair(
                pageWidth / 2 - watermarkDimensions.width / 2,
                pageHeight / 2 - watermarkDimensions.height / 2
            )
            Position.TOP_LEFT -> Pair(
                50f,
                pageHeight - watermarkDimensions.height - 50f
            )
            Position.TOP_CENTER -> Pair(
                pageWidth / 2 - watermarkDimensions.width / 2,
                pageHeight - watermarkDimensions.height - 50f
            )
            Position.TOP_RIGHT -> Pair(
                pageWidth - watermarkDimensions.width - 50f,
                pageHeight - watermarkDimensions.height - 50f
            )
            Position.CENTER_LEFT -> Pair(
                50f,
                pageHeight / 2 - watermarkDimensions.height / 2
            )
            Position.CENTER_RIGHT -> Pair(
                pageWidth - watermarkDimensions.width - 50f,
                pageHeight / 2 - watermarkDimensions.height / 2
            )
            Position.BOTTOM_LEFT -> Pair(
                50f,
                50f
            )
            Position.BOTTOM_CENTER -> Pair(
                pageWidth / 2 - watermarkDimensions.width / 2,
                50f
            )
            Position.BOTTOM_RIGHT -> Pair(
                pageWidth - watermarkDimensions.width - 50f,
                50f
            )
            Position.CUSTOM -> {
                val x = (customX ?: 0.5) * pageWidth - watermarkDimensions.width / 2
                val y = (customY ?: 0.5) * pageHeight - watermarkDimensions.height / 2
                Pair(x.toFloat(), y.toFloat())
            }
        }

        // Apply rotation adjustments if needed
        val adjustedPosition = if (rotation != 0.0) {
            adjustForRotation(baseX, baseY, pageWidth, pageHeight, watermarkDimensions, rotation)
        } else {
            Pair(baseX, baseY)
        }

        val finalPosition = WatermarkPosition(
            x = adjustedPosition.first.coerceIn(0f, pageWidth - watermarkDimensions.width),
            y = adjustedPosition.second.coerceIn(0f, pageHeight - watermarkDimensions.height),
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )

        logger.debug("Calculated watermark position: x={}, y={}", finalPosition.x, finalPosition.y)
        return finalPosition
    }

    /**
     * Calculates multiple positions for repeated watermarks with spacing.
     *
     * @param page The PDF page
     * @param watermarkDimensions The dimensions of the watermark
     * @param horizontalSpacing Horizontal spacing between watermarks
     * @param verticalSpacing Vertical spacing between watermarks
     * @param rotation Rotation angle in degrees
     * @return List of WatermarkPosition objects
     */
    fun calculateRepeatedPositions(
        page: PDPage,
        watermarkDimensions: WatermarkDimensions,
        horizontalSpacing: Double,
        verticalSpacing: Double,
        rotation: Double = 0.0
    ): List<WatermarkPosition> {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        val positions = mutableListOf<WatermarkPosition>()
        
        val effectiveWidth = watermarkDimensions.width + horizontalSpacing.toFloat()
        val effectiveHeight = watermarkDimensions.height + verticalSpacing.toFloat()

        logger.debug("Calculating repeated watermark positions with spacing: h={}, v={}", 
            horizontalSpacing, verticalSpacing)

        var y = 50f
        while (y + watermarkDimensions.height <= pageHeight - 50f) {
            var x = 50f
            while (x + watermarkDimensions.width <= pageWidth - 50f) {
                val position = WatermarkPosition(
                    x = x,
                    y = y,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight
                )
                positions.add(position)
                x += effectiveWidth
            }
            y += effectiveHeight
        }

        logger.debug("Generated {} repeated watermark positions", positions.size)
        return positions
    }

    /**
     * Validates if the given coordinates are within page bounds.
     *
     * @param page The PDF page
     * @param x X coordinate
     * @param y Y coordinate
     * @param watermarkDimensions Dimensions of the watermark
     * @return true if position is valid, false otherwise
     */
    fun isValidPosition(
        page: PDPage,
        x: Float,
        y: Float,
        watermarkDimensions: WatermarkDimensions
    ): Boolean {
        val mediaBox = page.mediaBox
        return x >= 0 && y >= 0 &&
                x + watermarkDimensions.width <= mediaBox.width &&
                y + watermarkDimensions.height <= mediaBox.height
    }

    /**
     * Calculates the center point of a page.
     *
     * @param page The PDF page
     * @return Pair of (x, y) coordinates for the center
     */
    fun getPageCenter(page: PDPage): Pair<Float, Float> {
        val mediaBox = page.mediaBox
        return Pair(mediaBox.width / 2, mediaBox.height / 2)
    }

    /**
     * Adjusts position coordinates for rotation.
     *
     * @param x Original X coordinate
     * @param y Original Y coordinate
     * @param pageWidth Page width
     * @param pageHeight Page height
     * @param watermarkDimensions Watermark dimensions
     * @param rotation Rotation angle in degrees
     * @return Adjusted (x, y) coordinates
     */
    private fun adjustForRotation(
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
        watermarkDimensions: WatermarkDimensions,
        rotation: Double
    ): Pair<Float, Float> {
        if (rotation == 0.0) return Pair(x, y)

        val radians = Math.toRadians(rotation)
        val centerX = pageWidth / 2
        val centerY = pageHeight / 2

        // Calculate rotated dimensions
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        
        val rotatedWidth = kotlin.math.abs(watermarkDimensions.width * cos) + 
                          kotlin.math.abs(watermarkDimensions.height * sin)
        val rotatedHeight = kotlin.math.abs(watermarkDimensions.width * sin) + 
                           kotlin.math.abs(watermarkDimensions.height * cos)

        // Adjust position to account for rotated bounds
        val adjustedX = x + (watermarkDimensions.width - rotatedWidth) / 2
        val adjustedY = y + (watermarkDimensions.height - rotatedHeight) / 2

        return Pair(adjustedX, adjustedY)
    }
}