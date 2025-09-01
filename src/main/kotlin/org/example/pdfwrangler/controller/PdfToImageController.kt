package org.example.pdfwrangler.controller

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.service.PdfToImageService
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
 * REST Controller for PDF to image conversion operations.
 * Provides endpoints for converting PDFs to various image formats.
 */
@RestController
@RequestMapping("/api/pdf-to-image")
@Validated
class PdfToImageController(
    private val pdfToImageService: PdfToImageService
) {

    private val logger = LoggerFactory.getLogger(PdfToImageController::class.java)

    /**
     * Convert PDF to images synchronously.
     */
    @PostMapping("/convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun convertPdfToImages(@Valid @ModelAttribute request: PdfToImageRequest): ResponseEntity<Resource> {
        logger.info("Starting PDF to image conversion for file: ${request.file.originalFilename}")
        
        return try {
            val startTime = System.currentTimeMillis()
            val result = pdfToImageService.convertPdfToImages(request)
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.info("PDF to image conversion completed in ${processingTime}ms")
            
            result.body?.let { resource ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${result.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)?.substringAfter("filename=\"")?.dropLast(1) ?: "converted_images.zip"}\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource)
            } ?: ResponseEntity.internalServerError().build()
        } catch (ex: Exception) {
            logger.error("Error during PDF to image conversion: ${ex.message}", ex)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Get conversion status and metadata.
     */
    @PostMapping("/status", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun getConversionStatus(@Valid @ModelAttribute request: PdfToImageRequest): ResponseEntity<PdfToImageResponse> {
        logger.info("Getting conversion status for file: ${request.file.originalFilename}")
        
        return try {
            val response = pdfToImageService.getConversionStatus(request)
            ResponseEntity.ok(response)
        } catch (ex: Exception) {
            logger.error("Error getting conversion status: ${ex.message}", ex)
            ResponseEntity.ok(PdfToImageResponse(
                success = false,
                message = ex.message ?: "Conversion status check failed",
                outputFiles = emptyList(),
                totalPages = 0,
                convertedPages = 0,
                processingTimeMs = 0,
                totalFileSize = 0
            ))
        }
    }

    /**
     * Start asynchronous PDF to image conversion.
     */
    @PostMapping("/convert-async", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun startAsyncConversion(@Valid @ModelAttribute request: PdfToImageRequest): ResponseEntity<Map<String, String>> {
        logger.info("Starting async PDF to image conversion for file: ${request.file.originalFilename}")
        
        return try {
            val operationId = pdfToImageService.startAsyncConversion(request)
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
            val progress = pdfToImageService.getConversionProgress(operationId)
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
            val result = pdfToImageService.downloadAsyncResult(operationId)
            
            result.body?.let { resource ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${result.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)?.substringAfter("filename=\"")?.dropLast(1) ?: "converted_images.zip"}\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource)
            } ?: ResponseEntity.notFound().build()
        } catch (ex: Exception) {
            logger.error("Error downloading async result: ${ex.message}", ex)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Convert multiple PDFs to images in batch.
     */
    @PostMapping("/batch-convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun batchConvertPdfs(@Valid @ModelAttribute request: BatchConversionRequest): ResponseEntity<BatchConversionResponse> {
        logger.info("Starting batch PDF to image conversion for ${request.conversionJobs.size} files")
        
        return try {
            val response = pdfToImageService.batchConvertPdfs(request)
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
    fun validateConversionRequest(@Valid @ModelAttribute request: PdfToImageRequest): ResponseEntity<Map<String, Any>> {
        logger.info("Validating conversion request for file: ${request.file.originalFilename}")
        
        return try {
            val validation = pdfToImageService.validateConversionRequest(request)
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
        logger.info("Getting conversion configuration")
        
        return ResponseEntity.ok(mapOf(
            "supportedOutputFormats" to listOf("PNG", "JPG", "JPEG", "GIF", "BMP", "WEBP", "TIFF"),
            "supportedColorModes" to listOf("RGB", "GRAYSCALE", "BLACK_WHITE"),
            "dpiRange" to mapOf("min" to 72, "max" to 600, "default" to 150),
            "qualityRange" to mapOf("min" to 0.1, "max" to 1.0, "default" to 0.95),
            "maxFileSize" to "100MB",
            "maxPages" to 1000,
            "batchSizeLimit" to 50
        ))
    }

    /**
     * Preview conversion settings without actual conversion.
     */
    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun previewConversion(@Valid @ModelAttribute request: PdfToImageRequest): ResponseEntity<Map<String, Any>> {
        logger.info("Previewing conversion for file: ${request.file.originalFilename}")
        
        return try {
            val preview = pdfToImageService.previewConversion(request)
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
}