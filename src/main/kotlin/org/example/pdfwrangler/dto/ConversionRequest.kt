package org.example.pdfwrangler.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max
import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for PDF to image conversion operations.
 */
data class PdfToImageRequest(
    @field:NotNull(message = "PDF file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(PNG|JPG|JPEG|GIF|BMP|WEBP|TIFF)$",
        message = "Output format must be one of: PNG, JPG, JPEG, GIF, BMP, WEBP, TIFF"
    )
    val outputFormat: String = "PNG",
    
    @field:Pattern(
        regexp = "^(RGB|GRAYSCALE|BLACK_WHITE)$",
        message = "Color mode must be one of: RGB, GRAYSCALE, BLACK_WHITE"
    )
    val colorMode: String = "RGB",
    
    @field:Min(value = 72, message = "DPI must be at least 72")
    @field:Max(value = 600, message = "DPI must not exceed 600")
    val dpi: Int = 150,
    
    val pageRanges: String? = null, // e.g., "1-3,5,7-10"
    val quality: Float = 0.95f,
    val outputFileName: String? = null
)

/**
 * Response DTO for PDF to image conversion operations.
 */
data class PdfToImageResponse(
    val success: Boolean,
    val message: String,
    val outputFiles: List<String>,
    val totalPages: Int,
    val convertedPages: Int,
    val processingTimeMs: Long,
    val totalFileSize: Long
)

/**
 * Request DTO for image to PDF conversion operations.
 */
data class ImageToPdfRequest(
    @field:NotNull(message = "At least one image file is required")
    val files: List<MultipartFile>,
    
    @field:Pattern(
        regexp = "^(A4|LETTER|LEGAL|A3|A5|CUSTOM)$",
        message = "Page size must be one of: A4, LETTER, LEGAL, A3, A5, CUSTOM"
    )
    val pageSize: String = "A4",
    
    @field:Pattern(
        regexp = "^(PORTRAIT|LANDSCAPE)$",
        message = "Orientation must be one of: PORTRAIT, LANDSCAPE"
    )
    val orientation: String = "PORTRAIT",
    
    val quality: Float = 0.95f,
    val maintainAspectRatio: Boolean = true,
    val outputFileName: String? = null,
    val customWidth: Float? = null,
    val customHeight: Float? = null
)

/**
 * Response DTO for image to PDF conversion operations.
 */
data class ImageToPdfResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val totalPages: Int,
    val processedImages: Int,
    val processingTimeMs: Long,
    val fileSize: Long?
)

/**
 * Request DTO for office document conversion operations.
 */
data class OfficeDocumentConversionRequest(
    @field:NotNull(message = "Document file is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(PDF|DOC|DOCX|XLS|XLSX|PPT|PPTX|ODT|ODS|ODP|RTF|TXT)$",
        message = "Output format must be supported office format"
    )
    val outputFormat: String = "PDF",
    
    val preserveFormatting: Boolean = true,
    val includeComments: Boolean = false,
    val includeMetadata: Boolean = true,
    val outputFileName: String? = null
)

/**
 * Response DTO for office document conversion operations.
 */
data class OfficeDocumentConversionResponse(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val originalFormat: String,
    val outputFormat: String,
    val processingTimeMs: Long,
    val fileSize: Long?
)

/**
 * Request DTO for batch conversion operations.
 */
data class BatchConversionRequest(
    val conversionJobs: List<ConversionJobRequest>
)

/**
 * Individual conversion job within a batch request.
 */
data class ConversionJobRequest(
    @field:NotNull(message = "File is required")
    val file: MultipartFile,
    
    @field:Pattern(
        regexp = "^(PDF_TO_IMAGE|IMAGE_TO_PDF|OFFICE_TO_PDF|PDF_TO_OFFICE)$",
        message = "Conversion type must be one of supported types"
    )
    val conversionType: String,
    
    val parameters: Map<String, Any> = emptyMap(),
    val outputFileName: String? = null
)

/**
 * Response DTO for batch conversion operations.
 */
data class BatchConversionResponse(
    val success: Boolean,
    val message: String,
    val completedJobs: Int,
    val failedJobs: Int,
    val totalProcessingTimeMs: Long,
    val results: List<ConversionJobResult>
)

/**
 * Result for individual conversion job.
 */
data class ConversionJobResult(
    val success: Boolean,
    val message: String,
    val outputFileName: String?,
    val conversionType: String,
    val processingTimeMs: Long,
    val fileSize: Long?
)

/**
 * Progress tracking DTO for conversion operations.
 */
data class ConversionProgressResponse(
    val operationId: String,
    val status: String,
    val progressPercentage: Int,
    val currentStep: String,
    val estimatedTimeRemainingMs: Long?,
    val processedFiles: Int,
    val totalFiles: Int,
    val conversionType: String
)