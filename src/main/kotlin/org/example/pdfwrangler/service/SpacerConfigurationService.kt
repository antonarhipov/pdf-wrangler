package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDPage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for configuring spacing between watermarks and calculating layout patterns.
 * Handles width and height spacing for repeated watermarks and grid layouts.
 * Task 78: Build SpacerConfigurationService for width and height spacing
 */
@Service
class SpacerConfigurationService {

    private val logger = LoggerFactory.getLogger(SpacerConfigurationService::class.java)

    /**
     * Data class representing spacing configuration.
     */
    data class SpacingConfig(
        val horizontalSpacing: Double,
        val verticalSpacing: Double,
        val margins: Margins = Margins(),
        val spacingType: SpacingType = SpacingType.ABSOLUTE
    )

    /**
     * Data class for page margins.
     */
    data class Margins(
        val top: Double = 50.0,
        val right: Double = 50.0,
        val bottom: Double = 50.0,
        val left: Double = 50.0
    )

    /**
     * Enumeration for spacing calculation methods.
     */
    enum class SpacingType {
        ABSOLUTE,    // Fixed pixel/point spacing
        RELATIVE,    // Percentage-based spacing
        AUTO_FIT     // Automatically calculated to fit evenly
    }

    /**
     * Data class representing a grid layout configuration.
     */
    data class GridLayout(
        val rows: Int,
        val columns: Int,
        val cellWidth: Double,
        val cellHeight: Double,
        val horizontalSpacing: Double,
        val verticalSpacing: Double,
        val totalWidth: Double,
        val totalHeight: Double
    )

    /**
     * Calculates optimal spacing for watermarks on a page.
     *
     * @param page The PDF page
     * @param watermarkWidth Width of the watermark
     * @param watermarkHeight Height of the watermark
     * @param desiredCount Desired number of watermarks (optional)
     * @param spacingConfig Spacing configuration
     * @return SpacingConfig with calculated optimal spacing
     */
    fun calculateOptimalSpacing(
        page: PDPage,
        watermarkWidth: Double,
        watermarkHeight: Double,
        desiredCount: Int? = null,
        spacingConfig: SpacingConfig = SpacingConfig(0.0, 0.0)
    ): SpacingConfig {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width.toDouble()
        val pageHeight = mediaBox.height.toDouble()

        logger.debug("Calculating optimal spacing for page {}x{}, watermark {}x{}", 
            pageWidth, pageHeight, watermarkWidth, watermarkHeight)

        return when (spacingConfig.spacingType) {
            SpacingType.ABSOLUTE -> spacingConfig
            SpacingType.RELATIVE -> calculateRelativeSpacing(
                pageWidth, pageHeight, watermarkWidth, watermarkHeight, spacingConfig
            )
            SpacingType.AUTO_FIT -> calculateAutoFitSpacing(
                pageWidth, pageHeight, watermarkWidth, watermarkHeight, 
                desiredCount, spacingConfig.margins
            )
        }
    }

    /**
     * Creates a grid layout for repeated watermarks.
     *
     * @param page The PDF page
     * @param watermarkWidth Width of the watermark
     * @param watermarkHeight Height of the watermark
     * @param spacingConfig Spacing configuration
     * @return GridLayout with calculated positions and dimensions
     */
    fun createGridLayout(
        page: PDPage,
        watermarkWidth: Double,
        watermarkHeight: Double,
        spacingConfig: SpacingConfig
    ): GridLayout {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width.toDouble()
        val pageHeight = mediaBox.height.toDouble()

        val effectiveWidth = pageWidth - spacingConfig.margins.left - spacingConfig.margins.right
        val effectiveHeight = pageHeight - spacingConfig.margins.top - spacingConfig.margins.bottom

        val cellWidth = watermarkWidth + spacingConfig.horizontalSpacing
        val cellHeight = watermarkHeight + spacingConfig.verticalSpacing

        val columns = maxOf(1, (effectiveWidth / cellWidth).toInt())
        val rows = maxOf(1, (effectiveHeight / cellHeight).toInt())

        val actualHorizontalSpacing = if (columns > 1) {
            (effectiveWidth - (columns * watermarkWidth)) / (columns - 1)
        } else spacingConfig.horizontalSpacing

        val actualVerticalSpacing = if (rows > 1) {
            (effectiveHeight - (rows * watermarkHeight)) / (rows - 1)
        } else spacingConfig.verticalSpacing

        val gridLayout = GridLayout(
            rows = rows,
            columns = columns,
            cellWidth = watermarkWidth + actualHorizontalSpacing,
            cellHeight = watermarkHeight + actualVerticalSpacing,
            horizontalSpacing = actualHorizontalSpacing,
            verticalSpacing = actualVerticalSpacing,
            totalWidth = effectiveWidth,
            totalHeight = effectiveHeight
        )

        logger.debug("Created grid layout: {}x{} grid with {}x{} cells", 
            columns, rows, cellWidth, cellHeight)

        return gridLayout
    }

    /**
     * Calculates positions for watermarks in a grid layout.
     *
     * @param gridLayout The grid layout configuration
     * @param margins Page margins
     * @return List of (x, y) coordinate pairs
     */
    fun calculateGridPositions(gridLayout: GridLayout, margins: Margins): List<Pair<Double, Double>> {
        val positions = mutableListOf<Pair<Double, Double>>()

        for (row in 0 until gridLayout.rows) {
            for (col in 0 until gridLayout.columns) {
                val x = margins.left + (col * gridLayout.cellWidth)
                val y = margins.bottom + (row * gridLayout.cellHeight)
                positions.add(Pair(x, y))
            }
        }

        logger.debug("Generated {} grid positions", positions.size)
        return positions
    }

    /**
     * Validates spacing configuration.
     *
     * @param spacingConfig The spacing configuration to validate
     * @param page The PDF page
     * @param watermarkWidth Watermark width
     * @param watermarkHeight Watermark height
     * @return ValidationResult indicating if configuration is valid
     */
    fun validateSpacingConfig(
        spacingConfig: SpacingConfig,
        page: PDPage,
        watermarkWidth: Double,
        watermarkHeight: Double
    ): ValidationResult {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width.toDouble()
        val pageHeight = mediaBox.height.toDouble()

        // Check if margins are reasonable
        val totalHorizontalMargin = spacingConfig.margins.left + spacingConfig.margins.right
        val totalVerticalMargin = spacingConfig.margins.top + spacingConfig.margins.bottom

        if (totalHorizontalMargin >= pageWidth * 0.9) {
            return ValidationResult.failure("Horizontal margins are too large for page width")
        }

        if (totalVerticalMargin >= pageHeight * 0.9) {
            return ValidationResult.failure("Vertical margins are too large for page height")
        }

        // Check if watermark fits within margins
        val effectiveWidth = pageWidth - totalHorizontalMargin
        val effectiveHeight = pageHeight - totalVerticalMargin

        if (watermarkWidth > effectiveWidth) {
            return ValidationResult.failure("Watermark width exceeds available space after margins")
        }

        if (watermarkHeight > effectiveHeight) {
            return ValidationResult.failure("Watermark height exceeds available space after margins")
        }

        // Check if spacing values are reasonable
        if (spacingConfig.horizontalSpacing < 0 || spacingConfig.verticalSpacing < 0) {
            return ValidationResult.failure("Spacing values cannot be negative")
        }

        return ValidationResult.success("Spacing configuration is valid")
    }

    /**
     * Creates a default spacing configuration.
     *
     * @param spacingType The type of spacing to use
     * @return Default SpacingConfig
     */
    fun createDefaultSpacingConfig(spacingType: SpacingType = SpacingType.ABSOLUTE): SpacingConfig {
        return when (spacingType) {
            SpacingType.ABSOLUTE -> SpacingConfig(
                horizontalSpacing = 20.0,
                verticalSpacing = 20.0,
                spacingType = spacingType
            )
            SpacingType.RELATIVE -> SpacingConfig(
                horizontalSpacing = 0.05, // 5% of page width
                verticalSpacing = 0.05,   // 5% of page height
                spacingType = spacingType
            )
            SpacingType.AUTO_FIT -> SpacingConfig(
                horizontalSpacing = 0.0,  // Will be calculated
                verticalSpacing = 0.0,    // Will be calculated
                spacingType = spacingType
            )
        }
    }

    /**
     * Calculates relative spacing based on page dimensions.
     */
    private fun calculateRelativeSpacing(
        pageWidth: Double,
        pageHeight: Double,
        watermarkWidth: Double,
        watermarkHeight: Double,
        spacingConfig: SpacingConfig
    ): SpacingConfig {
        val horizontalSpacing = pageWidth * spacingConfig.horizontalSpacing
        val verticalSpacing = pageHeight * spacingConfig.verticalSpacing

        return spacingConfig.copy(
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            spacingType = SpacingType.ABSOLUTE // Convert to absolute after calculation
        )
    }

    /**
     * Calculates auto-fit spacing to distribute watermarks evenly.
     */
    private fun calculateAutoFitSpacing(
        pageWidth: Double,
        pageHeight: Double,
        watermarkWidth: Double,
        watermarkHeight: Double,
        desiredCount: Int?,
        margins: Margins
    ): SpacingConfig {
        val effectiveWidth = pageWidth - margins.left - margins.right
        val effectiveHeight = pageHeight - margins.top - margins.bottom

        val optimalColumns = if (desiredCount != null) {
            // Try to create a roughly square arrangement
            kotlin.math.ceil(kotlin.math.sqrt(desiredCount.toDouble())).toInt()
        } else {
            maxOf(1, (effectiveWidth / (watermarkWidth * 1.5)).toInt())
        }

        val optimalRows = if (desiredCount != null) {
            kotlin.math.ceil(desiredCount.toDouble() / optimalColumns).toInt()
        } else {
            maxOf(1, (effectiveHeight / (watermarkHeight * 1.5)).toInt())
        }

        val horizontalSpacing = if (optimalColumns > 1) {
            (effectiveWidth - (optimalColumns * watermarkWidth)) / (optimalColumns - 1)
        } else 0.0

        val verticalSpacing = if (optimalRows > 1) {
            (effectiveHeight - (optimalRows * watermarkHeight)) / (optimalRows - 1)
        } else 0.0

        return SpacingConfig(
            horizontalSpacing = maxOf(0.0, horizontalSpacing),
            verticalSpacing = maxOf(0.0, verticalSpacing),
            margins = margins,
            spacingType = SpacingType.ABSOLUTE
        )
    }

    /**
     * Simple validation result class.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val errorCode: String? = null
    ) {
        companion object {
            fun success(message: String) = ValidationResult(true, message)
            fun failure(message: String, errorCode: String? = null) = ValidationResult(false, message, errorCode)
        }
    }
}