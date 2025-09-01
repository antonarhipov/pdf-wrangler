package org.example.pdfwrangler.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for PDF merge operations.
 * Supports multiple sorting options and configuration settings.
 */
data class MergeRequest(
    @field:NotEmpty(message = "At least one PDF file is required")
    val files: List<MultipartFile>,
    
    @field:Pattern(
        regexp = "^(filename|dateModified|dateCreated|pdfTitle|orderProvided)$",
        message = "Sort order must be one of: filename, dateModified, dateCreated, pdfTitle, orderProvided"
    )
    val sortOrder: String = "orderProvided",
    
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true,
    val flattenForms: Boolean = false,
    val removeSignatures: Boolean = true,
    val outputFileName: String? = null
)

/**
 * Response DTO for PDF merge operations.
 */
data class MergeResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val totalPages: Int?,
    val processedFiles: Int,
    val processingTimeMs: Long,
    val fileSize: Long?
)

/**
 * Request DTO for batch PDF merge operations.
 */
data class BatchMergeRequest(
    val mergeJobs: List<MergeJobRequest>
)

/**
 * Individual merge job within a batch request.
 */
data class MergeJobRequest(
    @field:NotEmpty(message = "At least one PDF file is required")
    val files: List<MultipartFile>,
    val sortOrder: String = "orderProvided",
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true,
    val flattenForms: Boolean = false,
    val removeSignatures: Boolean = true,
    val outputFileName: String? = null
)

/**
 * Response DTO for batch PDF merge operations.
 */
data class BatchMergeResponse(
    val success: Boolean,
    val message: String,
    val completedJobs: Int,
    val failedJobs: Int,
    val totalProcessingTimeMs: Long,
    val results: List<MergeResponse>
)

/**
 * Progress tracking DTO for merge operations.
 */
data class MergeProgressResponse(
    val operationId: String,
    val status: String,
    val progressPercentage: Int,
    val currentStep: String,
    val estimatedTimeRemainingMs: Long?,
    val processedFiles: Int,
    val totalFiles: Int
)