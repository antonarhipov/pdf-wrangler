package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.cos
import kotlin.math.sin

/**
 * Service for processing watermark appearance properties including opacity and rotation.
 * Handles graphics state management and coordinate transformations for watermarks.
 * Task 77: Implement OpacityAndRotationProcessor for watermark appearance control
 */
@Service
class OpacityAndRotationProcessor {

    private val logger = LoggerFactory.getLogger(OpacityAndRotationProcessor::class.java)

    /**
     * Data class for rotation configuration.
     */
    data class RotationConfig(
        val angle: Double,
        val centerX: Float,
        val centerY: Float
    )

    /**
     * Data class for opacity configuration.
     */
    data class OpacityConfig(
        val value: Double,
        val blendMode: String = "Normal"
    )

    /**
     * Applies opacity settings to a PDF content stream.
     *
     * @param contentStream The PDF content stream
     * @param opacity Opacity value (0.0 to 1.0)
     * @param blendMode Blend mode for the watermark (default: Normal)
     * @return PDExtendedGraphicsState that was applied
     */
    fun applyOpacity(
        contentStream: PDPageContentStream,
        opacity: Double,
        blendMode: String = "Normal"
    ): PDExtendedGraphicsState {
        val clampedOpacity = opacity.coerceIn(0.0, 1.0)
        
        logger.debug("Applying opacity: {} with blend mode: {}", clampedOpacity, blendMode)

        val graphicsState = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = clampedOpacity.toFloat()
            strokingAlphaConstant = clampedOpacity.toFloat()
            
            // Note: Blend mode support is limited in this implementation
            // Advanced blend modes can be added in future versions
            logger.debug("Using standard blend mode with opacity {}", clampedOpacity)
        }

        contentStream.setGraphicsStateParameters(graphicsState)
        return graphicsState
    }

    /**
     * Applies rotation transformation to a PDF content stream.
     *
     * @param contentStream The PDF content stream
     * @param rotation Rotation angle in degrees (positive = clockwise)
     * @param centerX X coordinate of rotation center
     * @param centerY Y coordinate of rotation center
     * @return Matrix representing the applied transformation
     */
    fun applyRotation(
        contentStream: PDPageContentStream,
        rotation: Double,
        centerX: Float,
        centerY: Float
    ): Matrix {
        if (rotation == 0.0) {
            logger.debug("No rotation applied (angle = 0°)")
            return Matrix()
        }

        val radians = Math.toRadians(rotation)
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()

        logger.debug("Applying rotation: {}° around center ({}, {})", rotation, centerX, centerY)

        // Create rotation matrix around the specified center point
        // Translation to origin, rotation, translation back
        val rotationMatrix = Matrix(cos, sin, -sin, cos, 0f, 0f)
        val translationToOrigin = Matrix.getTranslateInstance(-centerX, -centerY)
        val translationBack = Matrix.getTranslateInstance(centerX, centerY)

        // Combine transformations: translate back * rotate * translate to origin
        val combinedMatrix = translationBack.multiply(rotationMatrix).multiply(translationToOrigin)

        contentStream.transform(combinedMatrix)
        return combinedMatrix
    }

    /**
     * Applies both opacity and rotation to a content stream in one operation.
     *
     * @param contentStream The PDF content stream
     * @param opacityConfig Opacity configuration
     * @param rotationConfig Rotation configuration
     * @return Pair of (GraphicsState, TransformationMatrix)
     */
    fun applyOpacityAndRotation(
        contentStream: PDPageContentStream,
        opacityConfig: OpacityConfig,
        rotationConfig: RotationConfig
    ): Pair<PDExtendedGraphicsState, Matrix> {
        logger.debug("Applying combined opacity ({}) and rotation ({}°)", 
            opacityConfig.value, rotationConfig.angle)

        val graphicsState = applyOpacity(contentStream, opacityConfig.value, opacityConfig.blendMode)
        val transformationMatrix = applyRotation(
            contentStream, 
            rotationConfig.angle, 
            rotationConfig.centerX, 
            rotationConfig.centerY
        )

        return Pair(graphicsState, transformationMatrix)
    }

    /**
     * Calculates the bounding box of a rotated rectangle.
     *
     * @param width Original width
     * @param height Original height
     * @param rotation Rotation angle in degrees
     * @return Pair of (rotated width, rotated height)
     */
    fun calculateRotatedBounds(width: Float, height: Float, rotation: Double): Pair<Float, Float> {
        if (rotation == 0.0) return Pair(width, height)

        val radians = Math.toRadians(rotation)
        val cos = kotlin.math.abs(cos(radians).toFloat())
        val sin = kotlin.math.abs(sin(radians).toFloat())

        val rotatedWidth = width * cos + height * sin
        val rotatedHeight = width * sin + height * cos

        logger.debug("Calculated rotated bounds: {}x{} -> {}x{} at {}°", 
            width, height, rotatedWidth, rotatedHeight, rotation)

        return Pair(rotatedWidth, rotatedHeight)
    }

    /**
     * Saves the current graphics state and applies transformations.
     * Should be paired with restoreGraphicsState().
     *
     * @param contentStream The PDF content stream
     * @param opacityConfig Opacity configuration (optional)
     * @param rotationConfig Rotation configuration (optional)
     */
    fun saveAndApplyTransformations(
        contentStream: PDPageContentStream,
        opacityConfig: OpacityConfig? = null,
        rotationConfig: RotationConfig? = null
    ) {
        logger.debug("Saving graphics state and applying transformations")
        contentStream.saveGraphicsState()

        opacityConfig?.let { config ->
            applyOpacity(contentStream, config.value, config.blendMode)
        }

        rotationConfig?.let { config ->
            applyRotation(contentStream, config.angle, config.centerX, config.centerY)
        }
    }

    /**
     * Restores the previously saved graphics state.
     *
     * @param contentStream The PDF content stream
     */
    fun restoreGraphicsState(contentStream: PDPageContentStream) {
        logger.debug("Restoring graphics state")
        contentStream.restoreGraphicsState()
    }

    /**
     * Creates an opacity configuration with validation.
     *
     * @param opacity Opacity value (0.0 to 1.0)
     * @param blendMode Blend mode (default: Normal)
     * @return OpacityConfig with validated values
     */
    fun createOpacityConfig(opacity: Double, blendMode: String = "Normal"): OpacityConfig {
        val validatedOpacity = opacity.coerceIn(0.0, 1.0)
        if (validatedOpacity != opacity) {
            logger.warn("Opacity value {} clamped to {}", opacity, validatedOpacity)
        }

        val validatedBlendMode = validateBlendMode(blendMode)
        return OpacityConfig(validatedOpacity, validatedBlendMode)
    }

    /**
     * Creates a rotation configuration with angle normalization.
     *
     * @param angle Rotation angle in degrees
     * @param centerX X coordinate of rotation center
     * @param centerY Y coordinate of rotation center
     * @return RotationConfig with normalized angle
     */
    fun createRotationConfig(angle: Double, centerX: Float, centerY: Float): RotationConfig {
        val normalizedAngle = normalizeAngle(angle)
        return RotationConfig(normalizedAngle, centerX, centerY)
    }

    /**
     * Validates and normalizes a blend mode name.
     *
     * @param blendMode The blend mode to validate
     * @return Valid blend mode name
     */
    private fun validateBlendMode(blendMode: String): String {
        val validBlendModes = setOf(
            "Normal", "Multiply", "Screen", "Overlay", "SoftLight", "HardLight",
            "ColorDodge", "ColorBurn", "Darken", "Lighten", "Difference", "Exclusion"
        )

        return if (blendMode in validBlendModes) {
            blendMode
        } else {
            logger.warn("Invalid blend mode '{}', using 'Normal'", blendMode)
            "Normal"
        }
    }

    /**
     * Normalizes an angle to the range [0, 360).
     *
     * @param angle Angle in degrees
     * @return Normalized angle
     */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) {
            normalized += 360.0
        }
        return normalized
    }

    /**
     * Checks if the given opacity value is valid.
     *
     * @param opacity Opacity value to check
     * @return true if valid, false otherwise
     */
    fun isValidOpacity(opacity: Double): Boolean {
        return opacity in 0.0..1.0
    }

    /**
     * Checks if the given blend mode is supported.
     *
     * @param blendMode Blend mode to check
     * @return true if supported, false otherwise
     */
    fun isValidBlendMode(blendMode: String): Boolean {
        val validBlendModes = setOf(
            "Normal", "Multiply", "Screen", "Overlay", "SoftLight", "HardLight",
            "ColorDodge", "ColorBurn", "Darken", "Lighten", "Difference", "Exclusion"
        )
        return blendMode in validBlendModes
    }
}