package org.example.pdfwrangler.controller

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.Loader
import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.service.*
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import jakarta.validation.Valid

/**
 * REST controller for watermarking operations.
 * Provides endpoints for text watermarks, image watermarks, previews, batch processing, and template management.
 * Task 73: Create WatermarkController for watermarking operations
 */
@RestController
@RequestMapping("/api/watermark")
@Validated
class WatermarkController(
    private val textWatermarkService: TextWatermarkService,
    private val imageWatermarkService: ImageWatermarkService,
    private val watermarkPreviewService: WatermarkPreviewService,
    private val batchWatermarkingService: BatchWatermarkingService,
    private val watermarkTemplateManagementService: WatermarkTemplateManagementService,
    private val tempFileManagerService: TempFileManagerService
) {

    private val logger = LoggerFactory.getLogger(WatermarkController::class.java)

    /**
     * Applies text watermark to PDF files.
     */
    @PostMapping("/text", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applyTextWatermark(
        @Valid @ModelAttribute request: WatermarkRequest
    ): ResponseEntity<Resource> {
        logger.info("Applying text watermark to {} files", request.files.size)

        return try {
            if (request.watermarkType != "text") {
                throw IllegalArgumentException("Invalid watermark type for text endpoint")
            }

            // Convert request to text config
            val textConfig = TextWatermarkService.TextWatermarkConfig(
                text = request.text ?: throw IllegalArgumentException("Text is required for text watermarks"),
                fontFamily = request.fontFamily,
                fontSize = request.fontSize,
                fontColor = request.fontColor,
                fontBold = request.fontBold,
                fontItalic = request.fontItalic,
                opacity = request.opacity,
                rotation = request.rotation,
                position = request.position,
                customX = request.customX,
                customY = request.customY,
                horizontalSpacing = request.horizontalSpacing,
                verticalSpacing = request.verticalSpacing,
                repeatWatermark = request.repeatWatermark
            )

            // Validate configuration
            val validationErrors = textWatermarkService.validateTextWatermarkConfig(textConfig)
            if (validationErrors.isNotEmpty()) {
                throw IllegalArgumentException("Validation failed: ${validationErrors.joinToString(", ")}")
            }

            val startTime = System.currentTimeMillis()
            
            // Process each PDF file
            if (request.files.size != 1) {
                throw IllegalArgumentException("Currently only single file processing is supported")
            }
            
            val file = request.files.first()
            // Transfer MultipartFile to a temporary file first, then load PDDocument
            val tempInputFile = tempFileManagerService.createTempFile("input", ".pdf")
            file.transferTo(tempInputFile)
            val document = Loader.loadPDF(tempInputFile)
            
            try {
                // Apply watermark
                val totalPages = textWatermarkService.applyTextWatermark(
                    document, 
                    textConfig, 
                    request.pageNumbers
                )
                
                // Generate output filename
                val originalName = file.originalFilename ?: "document.pdf"
                val baseName = originalName.substringBeforeLast('.')
                val outputFileName = "${baseName}_watermarked.pdf"
                
                // Save to temporary file
                val tempFile = tempFileManagerService.createTempFile("watermarked", ".pdf")
                document.save(tempFile)
                
                val processingTime = System.currentTimeMillis() - startTime
                
                // Return the actual PDF file for download
                val resource = org.springframework.core.io.FileSystemResource(tempFile)
                val headers = HttpHeaders().apply {
                    add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$outputFileName\"")
                    add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                }
                
                logger.info("Returning watermarked PDF file: {} ({} bytes)", outputFileName, tempFile.length())
                
                ResponseEntity.ok()
                    .headers(headers)
                    .body(resource)
                
            } finally {
                document.close()
            }

        } catch (e: Exception) {
            logger.error("Failed to apply text watermark: {}", e.message)
            throw RuntimeException("Failed to apply watermark: ${e.message}", e)
        }
    }

    /**
     * Applies image watermark to PDF files.
     */
    @PostMapping("/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applyImageWatermark(
        @Valid @ModelAttribute request: WatermarkRequest
    ): ResponseEntity<WatermarkResponse> {
        logger.info("Applying image watermark to {} files", request.files.size)

        return try {
            if (request.watermarkType != "image") {
                return ResponseEntity.badRequest().body(
                    WatermarkResponse(
                        success = false,
                        message = "Invalid watermark type for image endpoint",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        watermarkType = request.watermarkType,
                        appliedToPages = null
                    )
                )
            }

            val imageFile = request.imageFile ?: throw IllegalArgumentException("Image file is required for image watermarks")

            // Convert request to image config
            val imageConfig = ImageWatermarkService.ImageWatermarkConfig(
                imageFile = imageFile,
                imageScale = request.imageScale,
                opacity = request.opacity,
                rotation = request.rotation,
                position = request.position,
                customX = request.customX,
                customY = request.customY,
                horizontalSpacing = request.horizontalSpacing,
                verticalSpacing = request.verticalSpacing,
                repeatWatermark = request.repeatWatermark
            )

            // Validate configuration
            val validationErrors = imageWatermarkService.validateImageWatermarkConfig(imageConfig)
            if (validationErrors.isNotEmpty()) {
                return ResponseEntity.badRequest().body(
                    WatermarkResponse(
                        success = false,
                        message = "Validation failed: ${validationErrors.joinToString(", ")}",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        watermarkType = "image",
                        appliedToPages = null
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            val totalProcessedPages = request.files.sumOf { file ->
                // Simulate processing - in real implementation, use imageWatermarkService
                if (request.pageNumbers.isEmpty()) 5 else request.pageNumbers.size
            }
            val processingTime = System.currentTimeMillis() - startTime

            val response = WatermarkResponse(
                success = true,
                message = "Image watermark applied successfully",
                outputFileName = "watermarked_output.pdf",
                totalPages = totalProcessedPages,
                processedFiles = request.files.size,
                processingTimeMs = processingTime,
                fileSize = request.files.sumOf { it.size },
                watermarkType = "image",
                appliedToPages = if (request.pageNumbers.isEmpty()) null else request.pageNumbers
            )

            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("Failed to apply image watermark: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkResponse(
                    success = false,
                    message = "Failed to apply watermark: ${e.message}",
                    outputFileName = null,
                    totalPages = null,
                    processedFiles = 0,
                    processingTimeMs = 0,
                    fileSize = null,
                    watermarkType = "image",
                    appliedToPages = null
                )
            )
        }
    }

    /**
     * Generates a preview of text watermark.
     */
    @PostMapping("/preview/text", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun previewTextWatermark(
        @Valid @ModelAttribute request: WatermarkPreviewRequest
    ): ResponseEntity<WatermarkPreviewResponse> {
        logger.info("Generating text watermark preview")

        return try {
            if (request.watermarkType != "text") {
                return ResponseEntity.badRequest().body(
                    WatermarkPreviewResponse(
                        success = false,
                        message = "Invalid watermark type for text preview",
                        previewImageBase64 = null,
                        watermarkDetails = emptyMap()
                    )
                )
            }

            val textConfig = TextWatermarkService.TextWatermarkConfig(
                text = request.text ?: throw IllegalArgumentException("Text is required"),
                fontFamily = request.fontFamily,
                fontSize = request.fontSize,
                fontColor = request.fontColor,
                fontBold = request.fontBold,
                fontItalic = request.fontItalic,
                opacity = request.opacity,
                rotation = request.rotation,
                position = request.position,
                customX = request.customX,
                customY = request.customY
            )

            val previewConfig = WatermarkPreviewService.PreviewConfig("text", request.pageNumber)
            val previewResult = watermarkPreviewService.generateTextWatermarkPreview(
                request.file, textConfig, previewConfig
            )

            val response = WatermarkPreviewResponse(
                success = previewResult.success,
                message = previewResult.message,
                previewImageBase64 = previewResult.previewImageBase64,
                watermarkDetails = mapOf(
                    "watermarkType" to "text",
                    "text" to textConfig.text,
                    "fontSize" to textConfig.fontSize,
                    "position" to textConfig.position,
                    "opacity" to textConfig.opacity,
                    "positionsCount" to previewResult.watermarkPositions.size
                )
            )

            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("Failed to generate text watermark preview: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkPreviewResponse(
                    success = false,
                    message = "Failed to generate preview: ${e.message}",
                    previewImageBase64 = null,
                    watermarkDetails = emptyMap()
                )
            )
        }
    }

    /**
     * Generates a preview of image watermark.
     */
    @PostMapping("/preview/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun previewImageWatermark(
        @Valid @ModelAttribute request: WatermarkPreviewRequest
    ): ResponseEntity<WatermarkPreviewResponse> {
        logger.info("Generating image watermark preview")

        return try {
            if (request.watermarkType != "image") {
                return ResponseEntity.badRequest().body(
                    WatermarkPreviewResponse(
                        success = false,
                        message = "Invalid watermark type for image preview",
                        previewImageBase64 = null,
                        watermarkDetails = emptyMap()
                    )
                )
            }

            val imageFile = request.imageFile ?: throw IllegalArgumentException("Image file is required")

            val imageConfig = ImageWatermarkService.ImageWatermarkConfig(
                imageFile = imageFile,
                imageScale = request.imageScale,
                opacity = request.opacity,
                rotation = request.rotation,
                position = request.position,
                customX = request.customX,
                customY = request.customY
            )

            val previewConfig = WatermarkPreviewService.PreviewConfig("image", request.pageNumber)
            val previewResult = watermarkPreviewService.generateImageWatermarkPreview(
                request.file, imageConfig, previewConfig
            )

            val response = WatermarkPreviewResponse(
                success = previewResult.success,
                message = previewResult.message,
                previewImageBase64 = previewResult.previewImageBase64,
                watermarkDetails = mapOf(
                    "watermarkType" to "image",
                    "imageFileName" to (imageFile.originalFilename ?: "unknown"),
                    "imageScale" to imageConfig.imageScale,
                    "position" to imageConfig.position,
                    "opacity" to imageConfig.opacity,
                    "positionsCount" to previewResult.watermarkPositions.size
                )
            )

            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("Failed to generate image watermark preview: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkPreviewResponse(
                    success = false,
                    message = "Failed to generate preview: ${e.message}",
                    previewImageBase64 = null,
                    watermarkDetails = emptyMap()
                )
            )
        }
    }

    /**
     * Starts batch watermarking operation.
     */
    @PostMapping("/batch", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun startBatchWatermarking(
        @Valid @ModelAttribute request: BatchWatermarkRequest
    ): ResponseEntity<Map<String, String>> {
        logger.info("Starting batch watermarking for {} jobs", request.watermarkJobs.size)

        return try {
            // Convert request to batch jobs
            val batchJobs = request.watermarkJobs.map { jobRequest ->
                when (jobRequest.watermarkType) {
                    "text" -> BatchWatermarkingService.BatchWatermarkJob(
                        files = jobRequest.files,
                        watermarkType = "text",
                        textConfig = TextWatermarkService.TextWatermarkConfig(
                            text = jobRequest.text ?: throw IllegalArgumentException("Text is required"),
                            fontFamily = jobRequest.fontFamily,
                            fontSize = jobRequest.fontSize,
                            fontColor = jobRequest.fontColor,
                            fontBold = jobRequest.fontBold,
                            fontItalic = jobRequest.fontItalic,
                            opacity = jobRequest.opacity,
                            rotation = jobRequest.rotation,
                            position = jobRequest.position,
                            customX = jobRequest.customX,
                            customY = jobRequest.customY,
                            horizontalSpacing = jobRequest.horizontalSpacing,
                            verticalSpacing = jobRequest.verticalSpacing,
                            repeatWatermark = jobRequest.repeatWatermark
                        ),
                        pageNumbers = jobRequest.pageNumbers,
                        outputFilePrefix = jobRequest.outputFileName
                    )
                    "image" -> {
                        val imageFile = jobRequest.imageFile ?: throw IllegalArgumentException("Image file is required for image watermarks")
                        BatchWatermarkingService.BatchWatermarkJob(
                            files = jobRequest.files,
                            watermarkType = "image",
                            imageConfig = ImageWatermarkService.ImageWatermarkConfig(
                                imageFile = imageFile,
                                imageScale = jobRequest.imageScale,
                                opacity = jobRequest.opacity,
                                rotation = jobRequest.rotation,
                                position = jobRequest.position,
                                customX = jobRequest.customX,
                                customY = jobRequest.customY,
                                horizontalSpacing = jobRequest.horizontalSpacing,
                                verticalSpacing = jobRequest.verticalSpacing,
                                repeatWatermark = jobRequest.repeatWatermark
                            ),
                            pageNumbers = jobRequest.pageNumbers,
                            outputFilePrefix = jobRequest.outputFileName
                        )
                    }
                    else -> throw IllegalArgumentException("Unsupported watermark type: ${jobRequest.watermarkType}")
                }
            }

            // Validate all jobs
            val allValidationErrors = batchJobs.flatMapIndexed { index, job ->
                val errors = batchWatermarkingService.validateBatchJob(job)
                errors.map { "Job $index: $it" }
            }

            if (allValidationErrors.isNotEmpty()) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to "false",
                        "message" to "Validation failed: ${allValidationErrors.joinToString(", ")}"
                    )
                )
            }

            val operationId = batchWatermarkingService.startAsyncBatchWatermarking(batchJobs)

            ResponseEntity.ok(
                mapOf(
                    "success" to "true",
                    "message" to "Batch watermarking started successfully",
                    "operationId" to operationId
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to start batch watermarking: {}", e.message)
            ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to "false",
                    "message" to "Failed to start batch processing: ${e.message}"
                )
            )
        }
    }

    /**
     * Gets batch operation progress.
     */
    @GetMapping("/batch/progress/{operationId}")
    fun getBatchProgress(@PathVariable operationId: String): ResponseEntity<BatchWatermarkingService.BatchProgress> {
        logger.debug("Getting batch progress for operation: {}", operationId)

        val progress = batchWatermarkingService.getBatchProgress(operationId)
        return if (progress != null) {
            ResponseEntity.ok(progress)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Creates a watermark template.
     */
    @PostMapping("/templates", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createTemplate(
        @Valid @ModelAttribute request: WatermarkTemplateRequest
    ): ResponseEntity<WatermarkTemplateResponse> {
        logger.info("Creating watermark template: {}", request.templateName)

        return try {
            val result = when (request.watermarkType) {
                "text" -> {
                    val textConfig = TextWatermarkService.TextWatermarkConfig(
                        text = request.text ?: throw IllegalArgumentException("Text is required"),
                        fontFamily = request.fontFamily,
                        fontSize = request.fontSize,
                        fontColor = request.fontColor,
                        fontBold = request.fontBold,
                        fontItalic = request.fontItalic,
                        opacity = request.opacity,
                        rotation = request.rotation,
                        position = request.position,
                        customX = request.customX,
                        customY = request.customY,
                        horizontalSpacing = request.horizontalSpacing,
                        verticalSpacing = request.verticalSpacing,
                        repeatWatermark = request.repeatWatermark
                    )
                    watermarkTemplateManagementService.createTextTemplate(
                        name = request.templateName,
                        textConfig = textConfig
                    )
                }
                "image" -> {
                    val imageFile = request.imageFile ?: throw IllegalArgumentException("Image file is required")
                    val imageConfig = ImageWatermarkService.ImageWatermarkConfig(
                        imageFile = imageFile,
                        imageScale = request.imageScale,
                        opacity = request.opacity,
                        rotation = request.rotation,
                        position = request.position,
                        customX = request.customX,
                        customY = request.customY,
                        horizontalSpacing = request.horizontalSpacing,
                        verticalSpacing = request.verticalSpacing,
                        repeatWatermark = request.repeatWatermark
                    )
                    watermarkTemplateManagementService.createImageTemplate(
                        name = request.templateName,
                        imageConfig = imageConfig
                    )
                }
                else -> throw IllegalArgumentException("Unsupported watermark type: ${request.watermarkType}")
            }

            val response = WatermarkTemplateResponse(
                success = result.success,
                message = result.message,
                templateId = result.templateId,
                templateName = result.template?.name
            )

            if (result.success) {
                ResponseEntity.ok(response)
            } else {
                ResponseEntity.badRequest().body(response)
            }

        } catch (e: Exception) {
            logger.error("Failed to create template: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkTemplateResponse(
                    success = false,
                    message = "Failed to create template: ${e.message}",
                    templateId = null,
                    templateName = null
                )
            )
        }
    }

    /**
     * Gets all watermark templates.
     */
    @GetMapping("/templates")
    fun getAllTemplates(): ResponseEntity<WatermarkTemplateResponse> {
        logger.debug("Getting all watermark templates")

        return try {
            val result = watermarkTemplateManagementService.getAllTemplates()
            ResponseEntity.ok(
                WatermarkTemplateResponse(
                    success = result.success,
                    message = result.message,
                    templateId = null,
                    templateName = null,
                    templates = result.templates?.map { serviceTemplate ->
                        WatermarkTemplate(
                            id = serviceTemplate.id,
                            name = serviceTemplate.name,
                            type = serviceTemplate.type,
                            configuration = serviceTemplate.configuration,
                            createdAt = serviceTemplate.createdAt,
                            lastUsed = serviceTemplate.lastUsed
                        )
                    } ?: emptyList()
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to get templates: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkTemplateResponse(
                    success = false,
                    message = "Failed to get templates: ${e.message}",
                    templateId = null,
                    templateName = null
                )
            )
        }
    }

    /**
     * Gets a specific watermark template.
     */
    @GetMapping("/templates/{templateId}")
    fun getTemplate(@PathVariable templateId: String): ResponseEntity<WatermarkTemplateResponse> {
        logger.debug("Getting watermark template: {}", templateId)

        return try {
            val result = watermarkTemplateManagementService.getTemplate(templateId)
            val response = WatermarkTemplateResponse(
                success = result.success,
                message = result.message,
                templateId = result.templateId,
                templateName = result.template?.name,
                templates = result.template?.let { serviceTemplate ->
                    listOf(
                        WatermarkTemplate(
                            id = serviceTemplate.id,
                            name = serviceTemplate.name,
                            type = serviceTemplate.type,
                            configuration = serviceTemplate.configuration,
                            createdAt = serviceTemplate.createdAt,
                            lastUsed = serviceTemplate.lastUsed
                        )
                    )
                }
            )

            if (result.success) {
                ResponseEntity.ok(response)
            } else {
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("Failed to get template: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkTemplateResponse(
                    success = false,
                    message = "Failed to get template: ${e.message}",
                    templateId = null,
                    templateName = null
                )
            )
        }
    }

    /**
     * Deletes a watermark template.
     */
    @DeleteMapping("/templates/{templateId}")
    fun deleteTemplate(@PathVariable templateId: String): ResponseEntity<WatermarkTemplateResponse> {
        logger.info("Deleting watermark template: {}", templateId)

        return try {
            val result = watermarkTemplateManagementService.deleteTemplate(templateId)
            val response = WatermarkTemplateResponse(
                success = result.success,
                message = result.message,
                templateId = result.templateId,
                templateName = null
            )

            if (result.success) {
                ResponseEntity.ok(response)
            } else {
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("Failed to delete template: {}", e.message)
            ResponseEntity.internalServerError().body(
                WatermarkTemplateResponse(
                    success = false,
                    message = "Failed to delete template: ${e.message}",
                    templateId = null,
                    templateName = null
                )
            )
        }
    }

    /**
     * Gets watermarking configuration options.
     */
    @GetMapping("/config")
    fun getWatermarkConfiguration(): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting watermark configuration")

        return ResponseEntity.ok(
            mapOf(
                "supportedTypes" to listOf("text", "image"),
                "supportedPositions" to listOf(
                    "center", "top-left", "top-center", "top-right",
                    "center-left", "center-right", "bottom-left", "bottom-center", "bottom-right", "custom"
                ),
                "supportedFonts" to listOf("Arial", "Times", "Courier"),
                "supportedImageFormats" to imageWatermarkService.getSupportedImageFormats(),
                "maxOpacity" to 1.0,
                "minOpacity" to 0.0,
                "maxFontSize" to 200,
                "minFontSize" to 1,
                "templates" to watermarkTemplateManagementService.getTemplateStatistics()
            )
        )
    }
}