package org.example.pdfwrangler.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max
import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for page rotation operations.
 */
data class PageRotationRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(90|180|270|-90|-180|-270)$",
        message = "Rotation angle must be one of: 90, 180, 270, -90, -180, -270"
    )
    val rotationAngle: String,
    
    val pageNumbers: List<Int>? = null, // null means all pages
    val outputFileName: String? = null
)

/**
 * Request DTO for page rearrangement operations.
 */
data class PageRearrangementRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(reorder|duplicate|remove)$",
        message = "Operation must be one of: reorder, duplicate, remove"
    )
    val operation: String,
    
    val pageNumbers: List<Int>, // Required for all operations
    val newOrder: List<Int>? = null, // Required for reorder operation
    val outputFileName: String? = null
)

/**
 * Request DTO for page scaling operations.
 */
data class PageScalingRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(percentage|fitToSize|customDimensions)$",
        message = "Scale type must be one of: percentage, fitToSize, customDimensions"
    )
    val scaleType: String,
    
    @field:Min(value = 1, message = "Scale percentage must be at least 1")
    @field:Max(value = 1000, message = "Scale percentage must not exceed 1000")
    val scalePercentage: Int? = null, // For percentage scaling
    
    @field:Pattern(
        regexp = "^(A4|A3|A5|LETTER|LEGAL|TABLOID)$",
        message = "Paper size must be one of: A4, A3, A5, LETTER, LEGAL, TABLOID"
    )
    val paperSize: String? = null, // For fitToSize scaling
    
    @field:Min(value = 1, message = "Custom width must be at least 1")
    val customWidth: Int? = null, // For custom dimensions
    
    @field:Min(value = 1, message = "Custom height must be at least 1")
    val customHeight: Int? = null, // For custom dimensions
    
    val pageNumbers: List<Int>? = null, // null means all pages
    val maintainAspectRatio: Boolean = true,
    val outputFileName: String? = null
)

/**
 * Request DTO for multi-page to single-page converter.
 */
data class MultiPageToSinglePageRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(vertical|horizontal)$",
        message = "Layout must be one of: vertical, horizontal"
    )
    val layout: String = "vertical",
    
    @field:Min(value = 0, message = "Spacing must be non-negative")
    val spacing: Int = 10, // Spacing between pages in points
    val outputFileName: String? = null
)

/**
 * Request DTO for multiple pages per sheet operations.
 */
data class MultiplePagesPerSheetRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Min(value = 2, message = "Pages per sheet must be at least 2")
    @field:Max(value = 16, message = "Pages per sheet must not exceed 16")
    val pagesPerSheet: Int,
    
    @field:Pattern(
        regexp = "^(grid|linear)$",
        message = "Layout must be one of: grid, linear"
    )
    val layout: String = "grid",
    
    @field:Min(value = 0, message = "Margin must be non-negative")
    val margin: Int = 10, // Margin in points
    val outputFileName: String? = null
)

/**
 * Request DTO for page cropping operations.
 */
data class PageCroppingRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(removeMargins|customCrop|autoDetect)$",
        message = "Crop type must be one of: removeMargins, customCrop, autoDetect"
    )
    val cropType: String,
    
    // Custom crop coordinates (in points from bottom-left origin)
    val cropX: Int? = null,
    val cropY: Int? = null,
    val cropWidth: Int? = null,
    val cropHeight: Int? = null,
    
    // Margin removal settings
    @field:Min(value = 0, message = "Margin threshold must be non-negative")
    val marginThreshold: Int? = 10, // For removeMargins type
    
    val pageNumbers: List<Int>? = null, // null means all pages
    val outputFileName: String? = null
)

/**
 * Request DTO for blank page detection operations.
 */
data class BlankPageDetectionRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Min(value = 0, message = "Sensitivity must be between 0 and 100")
    @field:Max(value = 100, message = "Sensitivity must be between 0 and 100")
    val sensitivity: Int = 95, // Percentage threshold for blank detection
    
    val removeBlankPages: Boolean = false, // If true, remove detected blank pages
    val outputFileName: String? = null
)

/**
 * Request DTO for custom page numbering operations.
 */
data class CustomPageNumberingRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(arabic|roman|ROMAN|alpha|ALPHA)$",
        message = "Number format must be one of: arabic, roman, ROMAN, alpha, ALPHA"
    )
    val numberFormat: String = "arabic",
    
    @field:Min(value = 1, message = "Starting number must be at least 1")
    val startingNumber: Int = 1,
    
    @field:Pattern(
        regexp = "^(topLeft|topCenter|topRight|bottomLeft|bottomCenter|bottomRight)$",
        message = "Position must be one of: topLeft, topCenter, topRight, bottomLeft, bottomCenter, bottomRight"
    )
    val position: String = "bottomCenter",
    
    @field:Min(value = 6, message = "Font size must be at least 6")
    @field:Max(value = 72, message = "Font size must not exceed 72")
    val fontSize: Int = 12,
    
    val prefix: String = "",
    val suffix: String = "",
    val pageNumbers: List<Int>? = null, // null means all pages
    val outputFileName: String? = null
)

/**
 * Generic response DTO for page operations.
 */
data class PageOperationResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val totalPages: Int?,
    val processedPages: Int,
    val processingTimeMs: Long,
    val fileSize: Long?,
    val operationDetails: Map<String, Any>? = null
)

/**
 * Response DTO for blank page detection.
 */
data class BlankPageDetectionResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val totalPages: Int,
    val blankPages: List<Int>,
    val processingTimeMs: Long,
    val fileSize: Long?
)

/**
 * Request DTO for page operation validation and preview.
 */
data class PageOperationValidationRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(rotation|rearrangement|scaling|conversion|cropping|numbering)$",
        message = "Operation type must be one of: rotation, rearrangement, scaling, conversion, cropping, numbering"
    )
    val operationType: String,
    
    val operationParameters: Map<String, Any>
)

/**
 * Response DTO for page operation validation and preview.
 */
data class PageOperationValidationResponse(
    val valid: Boolean,
    val validationErrors: List<String>,
    val previewInfo: Map<String, Any>,
    val estimatedProcessingTime: Long,
    val estimatedOutputSize: Long
)