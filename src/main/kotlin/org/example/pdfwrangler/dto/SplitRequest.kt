package org.example.pdfwrangler.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for PDF split operations.
 * Supports multiple split strategies and configuration settings.
 */
data class SplitRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(pageRanges|fileSize|documentSection|chapterBased|flexiblePageSelection|contentAware)$",
        message = "Split strategy must be one of: pageRanges, fileSize, documentSection, chapterBased, flexiblePageSelection, contentAware"
    )
    val splitStrategy: String,
    
    val pageRanges: List<String> = emptyList(),
    val fileSizeThresholdMB: Long? = null,
    val outputFileNamePattern: String? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true
)

/**
 * Request DTO for page range split operations.
 */
data class PageRangeSplitRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:NotEmpty(message = "At least one page range is required")
    val pageRanges: List<String>,
    
    val outputFileNamePattern: String? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true
)

/**
 * Request DTO for file size threshold split operations.
 */
data class FileSizeSplitRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Positive(message = "File size threshold must be positive")
    val fileSizeThresholdMB: Long,
    
    val outputFileNamePattern: String? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true
)

/**
 * Request DTO for document section split operations.
 */
data class DocumentSectionSplitRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(chapters|sections|bookmarks|annotations)$",
        message = "Section type must be one of: chapters, sections, bookmarks, annotations"
    )
    val sectionType: String = "chapters",
    
    val outputFileNamePattern: String? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true
)

/**
 * Request DTO for flexible page selection split operations.
 */
data class FlexiblePageSelectionRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    val pageSelections: List<PageSelection>,
    
    val outputFileNamePattern: String? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true
)

/**
 * Individual page selection configuration.
 */
data class PageSelection(
    val name: String,
    val pages: List<Int> = emptyList(),
    val ranges: List<String> = emptyList(),
    val excludePages: List<Int> = emptyList()
)

/**
 * Response DTO for PDF split operations.
 */
data class SplitResponse(
    val success: Boolean,
    val message: String,
    val outputFiles: List<SplitOutputFile>,
    val totalOutputFiles: Int,
    val processingTimeMs: Long,
    val originalFileName: String?,
    val splitStrategy: String
)

/**
 * Information about a split output file.
 */
data class SplitOutputFile(
    val fileName: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val pageRanges: String,
    val downloadUrl: String?
)

/**
 * Request DTO for batch split operations.
 */
data class BatchSplitRequest(
    val splitJobs: List<SplitJobRequest>
)

/**
 * Individual split job within a batch request.
 */
data class SplitJobRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    val splitStrategy: String,
    val pageRanges: List<String> = emptyList(),
    val fileSizeThresholdMB: Long? = null,
    val outputFileNamePattern: String? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true
)

/**
 * Response DTO for batch split operations.
 */
data class BatchSplitResponse(
    val success: Boolean,
    val message: String,
    val completedJobs: Int,
    val failedJobs: Int,
    val totalProcessingTimeMs: Long,
    val results: List<SplitResponse>
)

/**
 * Progress tracking DTO for split operations.
 */
data class SplitProgressResponse(
    val operationId: String,
    val status: String,
    val progressPercentage: Int,
    val currentStep: String,
    val estimatedTimeRemainingMs: Long?,
    val processedPages: Int,
    val totalPages: Int,
    val outputFilesCreated: Int
)

/**
 * Preview response for split operations.
 */
data class SplitPreviewResponse(
    val success: Boolean,
    val message: String,
    val previewResults: List<SplitPreviewResult>,
    val totalPages: Int,
    val estimatedOutputFiles: Int,
    val splitStrategy: String
)

/**
 * Individual preview result for split operations.
 */
data class SplitPreviewResult(
    val outputFileName: String,
    val pageRanges: String,
    val pageCount: Int,
    val estimatedFileSizeMB: Double
)