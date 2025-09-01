package org.example.pdfwrangler.controller

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.service.ImageToPdfService
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import java.util.*

/**
 * REST Controller for image to PDF conversion operations.
 * Provides endpoints for converting various image formats to PDF.
 */
@RestController
@RequestMapping("/api/image-to-pdf")
@Validated
class ImageToPdfController(
    private val imageToPdfService: ImageToPdfService
) {

    private val logger = LoggerFactory.getLogger(ImageToPdfController::class.java)

    /**
     * Convert images to PDF synchronously.
     */
    @PostMapping("/convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun convertImagesToPdf(@Valid @ModelAttribute request: ImageToPdfRequest): ResponseEntity<Resource> {
        logger.info("Starting image to PDF conversion for ${request.files.size} files")
        
        return try {
            val startTime = System.currentTimeMillis()
            val result = imageToPdfService.convertImagesToPdf(request)
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.info("Image to PDF conversion completed in ${processingTime}ms")
            
            result.body?.let { resource ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${request.outputFileName ?: "converted_document.pdf"}\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource)
            } ?: ResponseEntity.internalServerError().build()
        } catch (ex: Exception) {
            logger.error("Error during image to PDF conversion: ${ex.message}", ex)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Get conversion status and metadata.
     */
    @PostMapping("/status", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun getConversionStatus(@Valid @ModelAttribute request: ImageToPdfRequest): ResponseEntity<ImageToPdfResponse> {
        logger.info("Getting conversion status for ${request.files.size} image files")
        
        return try {
            val response = imageToPdfService.getConversionStatus(request)
            ResponseEntity.ok(response)
        } catch (ex: Exception) {
            logger.error("Error getting conversion status: ${ex.message}", ex)
            ResponseEntity.ok(ImageToPdfResponse(
                success = false,
                message = ex.message ?: "Conversion status check failed",
                outputFileName = null,
                totalPages = 0,
                processedImages = 0,
                processingTimeMs = 0,
                fileSize = null
            ))
        }
    }

    /**
     * Start asynchronous image to PDF conversion.
     */
    @PostMapping("/convert-async", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun startAsyncConversion(@Valid @ModelAttribute request: ImageToPdfRequest): ResponseEntity<Map<String, String>> {
        logger.info("Starting async image to PDF conversion for ${request.files.size} files")
        
        return try {
            val operationId = imageToPdfService.startAsyncConversion(request)
            ResponseEntity.ok(mapOf(
                "operationId" to operationId,
                "status" to "STARTED",
                "message" to "Conversion started successfully"
            ))
        } catch (ex: Exception) {
            logger.error("Error starting async conversion: ${ex.message}", ex)
            ResponseEntity.internalServerError()
                .body(mapOf("error" to (ex.message ?: "Failed to start conversion")))
        }
    }

    /**
     * Get progress of asynchronous conversion.
     */
    @GetMapping("/progress/{operationId}")
    fun getConversionProgress(@PathVariable operationId: String): ResponseEntity<ConversionProgressResponse> {
        logger.info("Getting conversion progress for operation: $operationId")
        
        return try {
            val progress = imageToPdfService.getConversionProgress(operationId)
            ResponseEntity.ok(progress)
        } catch (ex: Exception) {
            logger.error("Error getting conversion progress: ${ex.message}", ex)
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Download result of asynchronous conversion.
     */
    @GetMapping("/download/{operationId}")
    fun downloadAsyncResult(@PathVariable operationId: String): ResponseEntity<Resource> {
        logger.info("Downloading async conversion result for operation: $operationId")
        
        return try {
            val result = imageToPdfService.downloadAsyncResult(operationId)
            
            result.body?.let { resource ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${result.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)?.substringAfter("filename=\"")?.dropLast(1) ?: "converted_document.pdf"}\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource)
            } ?: ResponseEntity.notFound().build()
        } catch (ex: Exception) {
            logger.error("Error downloading async result: ${ex.message}", ex)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Convert multiple image sets to PDFs in batch.
     */
    @PostMapping("/batch-convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun batchConvertImages(@Valid @ModelAttribute request: BatchConversionRequest): ResponseEntity<BatchConversionResponse> {
        logger.info("Starting batch image to PDF conversion for ${request.conversionJobs.size} jobs")
        
        return try {
            val response = imageToPdfService.batchConvertImages(request)
            ResponseEntity.ok(response)
        } catch (ex: Exception) {
            logger.error("Error during batch conversion: ${ex.message}", ex)
            ResponseEntity.ok(BatchConversionResponse(
                success = false,
                message = ex.message ?: "Batch conversion failed",
                completedJobs = 0,
                failedJobs = request.conversionJobs.size,
                totalProcessingTimeMs = 0,
                results = emptyList()
            ))
        }
    }

    /**
     * Validate conversion request without processing.
     */
    @PostMapping("/validate", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun validateConversionRequest(@Valid @ModelAttribute request: ImageToPdfRequest): ResponseEntity<Map<String, Any>> {
        logger.info("Validating conversion request for ${request.files.size} image files")
        
        return try {
            val validation = imageToPdfService.validateConversionRequest(request)
            ResponseEntity.ok(validation)
        } catch (ex: Exception) {
            logger.error("Error validating conversion request: ${ex.message}", ex)
            ResponseEntity.ok(mapOf(
                "valid" to false,
                "errors" to listOf(ex.message ?: "Validation failed"),
                "warnings" to emptyList<String>()
            ))
        }
    }

    /**
     * Get supported conversion formats and configuration options.
     */
    @GetMapping("/configuration")
    fun getConversionConfiguration(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting image to PDF conversion configuration")
        
        return ResponseEntity.ok(mapOf(
            "supportedImageFormats" to listOf("PNG", "JPG", "JPEG", "GIF", "BMP", "WEBP", "TIFF"),
            "supportedPageSizes" to listOf("A4", "LETTER", "LEGAL", "A3", "A5", "CUSTOM"),
            "supportedOrientations" to listOf("PORTRAIT", "LANDSCAPE"),
            "qualityRange" to mapOf("min" to 0.1, "max" to 1.0, "default" to 0.95),
            "maxFileSize" to "50MB",
            "maxImagesPerPdf" to 500,
            "batchSizeLimit" to 20
        ))
    }

    /**
     * Preview conversion settings without actual conversion.
     */
    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun previewConversion(@Valid @ModelAttribute request: ImageToPdfRequest): ResponseEntity<Map<String, Any>> {
        logger.info("Previewing conversion for ${request.files.size} image files")
        
        return try {
            val preview = imageToPdfService.previewConversion(request)
            ResponseEntity.ok(preview)
        } catch (ex: Exception) {
            logger.error("Error previewing conversion: ${ex.message}", ex)
            ResponseEntity.ok(mapOf(
                "success" to false,
                "message" to (ex.message ?: "Preview generation failed"),
                "estimatedOutputSize" to 0,
                "estimatedProcessingTime" to 0
            ))
        }
    }

    /**
     * Get page layout options and recommendations.
     */
    @GetMapping("/layout-options")
    fun getLayoutOptions(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting page layout options")
        
        return ResponseEntity.ok(mapOf(
            "pageSizes" to mapOf(
                "A4" to mapOf("width" to 8.27, "height" to 11.69, "units" to "inches"),
                "LETTER" to mapOf("width" to 8.5, "height" to 11.0, "units" to "inches"),
                "LEGAL" to mapOf("width" to 8.5, "height" to 14.0, "units" to "inches"),
                "A3" to mapOf("width" to 11.69, "height" to 16.54, "units" to "inches"),
                "A5" to mapOf("width" to 5.83, "height" to 8.27, "units" to "inches")
            ),
            "layoutRecommendations" to mapOf(
                "portrait" to "Best for documents and text-heavy content",
                "landscape" to "Best for wide images and presentations",
                "maintainAspectRatio" to "Prevents image distortion",
                "fitToPage" to "Ensures images fit within page boundaries"
            ),
            "qualityGuidelines" to mapOf(
                "print" to "Use quality 0.95 for print documents",
                "web" to "Use quality 0.8 for web distribution",
                "archive" to "Use quality 1.0 for archival purposes"
            )
        ))
    }

    /**
     * Get image format compatibility information.
     */
    @GetMapping("/format-compatibility")
    fun getFormatCompatibility(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting image format compatibility information")
        
        return ResponseEntity.ok(mapOf(
            "formats" to mapOf(
                "PNG" to mapOf(
                    "transparency" to true,
                    "compression" to "lossless",
                    "recommended" to "Best for images with transparency or text"
                ),
                "JPEG" to mapOf(
                    "transparency" to false,
                    "compression" to "lossy",
                    "recommended" to "Best for photographs and complex images"
                ),
                "GIF" to mapOf(
                    "transparency" to true,
                    "compression" to "lossless",
                    "recommended" to "Best for simple graphics and logos"
                ),
                "BMP" to mapOf(
                    "transparency" to false,
                    "compression" to "none",
                    "recommended" to "Uncompressed format, large file sizes"
                ),
                "WEBP" to mapOf(
                    "transparency" to true,
                    "compression" to "both",
                    "recommended" to "Modern format with excellent compression"
                ),
                "TIFF" to mapOf(
                    "transparency" to true,
                    "compression" to "various",
                    "recommended" to "Professional format for high-quality images"
                )
            ),
            "conversionNotes" to listOf(
                "Transparent backgrounds will be preserved where possible",
                "Images will be automatically oriented based on EXIF data",
                "Color profiles will be maintained during conversion",
                "Large images will be optimized for PDF inclusion"
            )
        ))
    }
}