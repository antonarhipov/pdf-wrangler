package org.example.pdfwrangler.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for visual enhancement operations.
 * Supports image overlays, stamps, color manipulation, and form flattening.
 */
data class VisualEnhancementRequest(
    @field:NotEmpty(message = "At least one PDF file is required")
    val files: List<MultipartFile>,
    
    @field:Pattern(
        regexp = "^(imageOverlay|stamp|colorManipulation|formFlattening)$",
        message = "Enhancement type must be one of: imageOverlay, stamp, colorManipulation, formFlattening"
    )
    val enhancementType: String,
    
    // Image overlay properties
    val overlayImage: MultipartFile? = null,
    val overlayScale: Double = 1.0,
    val overlayOpacity: Double = 1.0,
    
    // Stamp properties
    val stampType: String = "custom", // "custom", "approved", "draft", "confidential", "urgent"
    val stampText: String? = null,
    val stampImage: MultipartFile? = null,
    val stampSize: String = "medium", // "small", "medium", "large", "custom"
    val customStampWidth: Double? = null,
    val customStampHeight: Double? = null,
    
    // Color manipulation properties
    @field:Pattern(
        regexp = "^(invert|grayscale|sepia|brightness|contrast|saturation|hue)$",
        message = "Color operation must be one of: invert, grayscale, sepia, brightness, contrast, saturation, hue"
    )
    val colorOperation: String = "grayscale",
    val colorIntensity: Double = 1.0, // For brightness, contrast, saturation
    val hueShift: Double = 0.0, // For hue adjustment (-180 to 180 degrees)
    
    // Form flattening properties
    val preserveFormData: Boolean = true,
    val flattenSignatures: Boolean = true,
    val flattenAnnotations: Boolean = true,
    
    // Common positioning properties
    @field:Pattern(
        regexp = "^(center|top-left|top-center|top-right|center-left|center-right|bottom-left|bottom-center|bottom-right|custom|all-pages|specific-pages)$",
        message = "Position must be a valid position type"
    )
    val position: String = "center",
    
    val customX: Double? = null,
    val customY: Double? = null,
    
    // Page selection
    val applyToAllPages: Boolean = true,
    val pageNumbers: List<Int> = emptyList(),
    
    // Output settings
    val outputFileName: String? = null,
    val qualityLevel: String = "high" // "low", "medium", "high", "maximum"
)

/**
 * Response DTO for visual enhancement operations.
 */
data class VisualEnhancementResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val totalPages: Int?,
    val processedFiles: Int,
    val processingTimeMs: Long,
    val fileSize: Long?,
    val enhancementType: String,
    val appliedToPages: List<Int>?,
    val qualityMetrics: Map<String, Any>? = null
)

/**
 * Request DTO for batch visual enhancement operations.
 */
data class BatchVisualEnhancementRequest(
    val enhancementJobs: List<VisualEnhancementJobRequest>
)

/**
 * Individual visual enhancement job within a batch request.
 */
data class VisualEnhancementJobRequest(
    @field:NotEmpty(message = "At least one PDF file is required")
    val files: List<MultipartFile>,
    val enhancementType: String,
    val overlayImage: MultipartFile? = null,
    val overlayScale: Double = 1.0,
    val overlayOpacity: Double = 1.0,
    val stampType: String = "custom",
    val stampText: String? = null,
    val stampImage: MultipartFile? = null,
    val stampSize: String = "medium",
    val colorOperation: String = "grayscale",
    val colorIntensity: Double = 1.0,
    val preserveFormData: Boolean = true,
    val position: String = "center",
    val customX: Double? = null,
    val customY: Double? = null,
    val applyToAllPages: Boolean = true,
    val pageNumbers: List<Int> = emptyList(),
    val outputFileName: String? = null,
    val qualityLevel: String = "high"
)

/**
 * Response DTO for batch visual enhancement operations.
 */
data class BatchVisualEnhancementResponse(
    val success: Boolean,
    val message: String,
    val completedJobs: Int,
    val failedJobs: Int,
    val totalProcessingTimeMs: Long,
    val results: List<VisualEnhancementResponse>
)

/**
 * Request DTO for visual enhancement preview.
 */
data class VisualEnhancementPreviewRequest(
    val file: MultipartFile,
    val enhancementType: String,
    val overlayImage: MultipartFile? = null,
    val overlayScale: Double = 1.0,
    val overlayOpacity: Double = 1.0,
    val stampType: String = "custom",
    val stampText: String? = null,
    val stampImage: MultipartFile? = null,
    val colorOperation: String = "grayscale",
    val colorIntensity: Double = 1.0,
    val position: String = "center",
    val customX: Double? = null,
    val customY: Double? = null,
    val pageNumber: Int = 1
)

/**
 * Response DTO for visual enhancement preview.
 */
data class VisualEnhancementPreviewResponse(
    val success: Boolean,
    val message: String,
    val previewImageBase64: String?,
    val enhancementDetails: Map<String, Any>,
    val qualityAssessment: Map<String, Any>? = null
)

/**
 * Request DTO for stamp library management.
 */
data class StampLibraryRequest(
    val stampName: String,
    val stampType: String, // "text", "image", "template"
    val stampText: String? = null,
    val stampImage: MultipartFile? = null,
    val category: String = "general", // "general", "approval", "status", "custom"
    val isPublic: Boolean = false,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Response DTO for stamp library operations.
 */
data class StampLibraryResponse(
    val success: Boolean,
    val message: String,
    val stampId: String? = null,
    val stamps: List<StampInfo>? = null
)

/**
 * Stamp information data structure.
 */
data class StampInfo(
    val id: String,
    val name: String,
    val type: String,
    val category: String,
    val description: String?,
    val isPublic: Boolean,
    val createdAt: String,
    val lastUsed: String?,
    val usageCount: Int = 0,
    val previewBase64: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Request DTO for quality assessment.
 */
data class QualityAssessmentRequest(
    val file: MultipartFile,
    val assessmentTypes: List<String> = listOf("resolution", "clarity", "color", "compression"),
    val detailedAnalysis: Boolean = false
)

/**
 * Response DTO for quality assessment.
 */
data class QualityAssessmentResponse(
    val success: Boolean,
    val message: String,
    val overallScore: Double, // 0.0 to 1.0
    val assessmentResults: Map<String, QualityMetric>,
    val recommendations: List<String> = emptyList(),
    val processingTimeMs: Long
)

/**
 * Quality metric data structure.
 */
data class QualityMetric(
    val name: String,
    val score: Double, // 0.0 to 1.0
    val details: Map<String, Any>,
    val issues: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

/**
 * Request DTO for audit trail operations.
 */
data class AuditTrailRequest(
    val operationType: String,
    val fileName: String,
    val enhancementType: String,
    val parameters: Map<String, Any>,
    val userId: String? = null,
    val sessionId: String? = null
)

/**
 * Audit trail entry data structure.
 */
data class AuditTrailEntry(
    val id: String,
    val timestamp: String,
    val operationType: String,
    val fileName: String,
    val enhancementType: String,
    val parameters: Map<String, Any>,
    val result: String, // "success", "failure", "partial"
    val duration: Long,
    val userId: String?,
    val sessionId: String?,
    val ipAddress: String? = null,
    val fileSize: Long? = null,
    val outputSize: Long? = null
)

/**
 * Progress tracking DTO for visual enhancement operations.
 */
data class VisualEnhancementProgressResponse(
    val operationId: String,
    val status: String,
    val progressPercentage: Int,
    val currentStep: String,
    val estimatedTimeRemainingMs: Long?,
    val processedFiles: Int,
    val totalFiles: Int,
    val currentPage: Int?,
    val totalPages: Int?,
    val enhancementType: String
)