package org.example.pdfwrangler.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for watermarking operations.
 * Supports both text and image watermarks with flexible positioning and appearance options.
 */
data class WatermarkRequest(
    @field:NotEmpty(message = "At least one PDF file is required")
    val files: List<MultipartFile>,
    
    @field:Pattern(
        regexp = "^(text|image)$",
        message = "Watermark type must be either 'text' or 'image'"
    )
    val watermarkType: String,
    
    // Text watermark properties
    val text: String? = null,
    val fontFamily: String = "Arial",
    val fontSize: Int = 24,
    val fontColor: String = "#000000",
    val fontBold: Boolean = false,
    val fontItalic: Boolean = false,
    
    // Image watermark properties
    val imageFile: MultipartFile? = null,
    val imageScale: Double = 1.0,
    
    // Position settings
    @field:Pattern(
        regexp = "^(center|top-left|top-center|top-right|center-left|center-right|bottom-left|bottom-center|bottom-right|custom)$",
        message = "Position must be one of: center, top-left, top-center, top-right, center-left, center-right, bottom-left, bottom-center, bottom-right, custom"
    )
    val position: String = "center",
    
    // Custom positioning (when position = "custom")
    val customX: Double? = null,
    val customY: Double? = null,
    
    // Appearance settings
    @field:DecimalMin(value = "0.0", message = "Opacity must be between 0.0 and 1.0")
    @field:DecimalMax(value = "1.0", message = "Opacity must be between 0.0 and 1.0")
    val opacity: Double = 0.5,
    
    val rotation: Double = 0.0,
    
    // Spacing configuration
    val horizontalSpacing: Double = 0.0,
    val verticalSpacing: Double = 0.0,
    val repeatWatermark: Boolean = false,
    
    // Application settings
    val applyToAllPages: Boolean = true,
    val pageNumbers: List<Int> = emptyList(),
    val outputFileName: String? = null
)

/**
 * Response DTO for watermarking operations.
 */
data class WatermarkResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val totalPages: Int?,
    val processedFiles: Int,
    val processingTimeMs: Long,
    val fileSize: Long?,
    val watermarkType: String,
    val appliedToPages: List<Int>?
)

/**
 * Request DTO for batch watermarking operations.
 */
data class BatchWatermarkRequest(
    val watermarkJobs: List<WatermarkJobRequest>
)

/**
 * Individual watermark job within a batch request.
 */
data class WatermarkJobRequest(
    @field:NotEmpty(message = "At least one PDF file is required")
    val files: List<MultipartFile>,
    val watermarkType: String,
    val text: String? = null,
    val fontFamily: String = "Arial",
    val fontSize: Int = 24,
    val fontColor: String = "#000000",
    val fontBold: Boolean = false,
    val fontItalic: Boolean = false,
    val imageFile: MultipartFile? = null,
    val imageScale: Double = 1.0,
    val position: String = "center",
    val customX: Double? = null,
    val customY: Double? = null,
    val opacity: Double = 0.5,
    val rotation: Double = 0.0,
    val horizontalSpacing: Double = 0.0,
    val verticalSpacing: Double = 0.0,
    val repeatWatermark: Boolean = false,
    val applyToAllPages: Boolean = true,
    val pageNumbers: List<Int> = emptyList(),
    val outputFileName: String? = null
)

/**
 * Response DTO for batch watermarking operations.
 */
data class BatchWatermarkResponse(
    val success: Boolean,
    val message: String,
    val completedJobs: Int,
    val failedJobs: Int,
    val totalProcessingTimeMs: Long,
    val results: List<WatermarkResponse>
)

/**
 * Progress tracking DTO for watermarking operations.
 */
data class WatermarkProgressResponse(
    val operationId: String,
    val status: String,
    val progressPercentage: Int,
    val currentStep: String,
    val estimatedTimeRemainingMs: Long?,
    val processedFiles: Int,
    val totalFiles: Int,
    val currentPage: Int?,
    val totalPages: Int?
)

/**
 * Request DTO for watermark preview operations.
 */
data class WatermarkPreviewRequest(
    val file: MultipartFile,
    val watermarkType: String,
    val text: String? = null,
    val fontFamily: String = "Arial",
    val fontSize: Int = 24,
    val fontColor: String = "#000000",
    val fontBold: Boolean = false,
    val fontItalic: Boolean = false,
    val imageFile: MultipartFile? = null,
    val imageScale: Double = 1.0,
    val position: String = "center",
    val customX: Double? = null,
    val customY: Double? = null,
    val opacity: Double = 0.5,
    val rotation: Double = 0.0,
    val pageNumber: Int = 1
)

/**
 * Response DTO for watermark preview operations.
 */
data class WatermarkPreviewResponse(
    val success: Boolean,
    val message: String,
    val previewImageBase64: String?,
    val watermarkDetails: Map<String, Any>
)

/**
 * Request DTO for watermark template management.
 */
data class WatermarkTemplateRequest(
    @field:NotBlank(message = "Template name is required")
    val templateName: String,
    val watermarkType: String,
    val text: String? = null,
    val fontFamily: String = "Arial",
    val fontSize: Int = 24,
    val fontColor: String = "#000000",
    val fontBold: Boolean = false,
    val fontItalic: Boolean = false,
    val imageFile: MultipartFile? = null,
    val imageScale: Double = 1.0,
    val position: String = "center",
    val customX: Double? = null,
    val customY: Double? = null,
    val opacity: Double = 0.5,
    val rotation: Double = 0.0,
    val horizontalSpacing: Double = 0.0,
    val verticalSpacing: Double = 0.0,
    val repeatWatermark: Boolean = false
)

/**
 * Response DTO for watermark template operations.
 */
data class WatermarkTemplateResponse(
    val success: Boolean,
    val message: String,
    val templateId: String?,
    val templateName: String?,
    val templates: List<WatermarkTemplate>? = null
)

/**
 * Watermark template data structure.
 */
data class WatermarkTemplate(
    val id: String,
    val name: String,
    val type: String,
    val configuration: Map<String, Any>,
    val createdAt: String,
    val lastUsed: String?
)