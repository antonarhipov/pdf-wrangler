package org.example.pdfwrangler.controller

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
 * REST controller for visual enhancement operations.
 * Provides endpoints for image overlays, stamps, color manipulation, form flattening, and quality assessment.
 * Task 83: Create VisualEnhancementController for image and stamp operations
 */
@RestController
@RequestMapping("/api/visual-enhancement")
@Validated
class VisualEnhancementController(
    private val imageOverlayService: ImageOverlayService,
    private val stampApplicationService: StampApplicationService,
    private val colorManipulationService: ColorManipulationService,
    private val formFlatteningService: FormFlatteningService,
    private val visualEnhancementPreviewService: VisualEnhancementPreviewService,
    private val stampLibraryManagementService: StampLibraryManagementService,
    private val visualEnhancementBatchService: VisualEnhancementBatchService,
    private val documentQualityAssessmentService: DocumentQualityAssessmentService,
    private val visualEnhancementAuditService: VisualEnhancementAuditService
) {

    private val logger = LoggerFactory.getLogger(VisualEnhancementController::class.java)

    /**
     * Applies image overlay to PDF files.
     */
    @PostMapping("/image-overlay", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applyImageOverlay(
        @Valid @ModelAttribute request: VisualEnhancementRequest
    ): ResponseEntity<VisualEnhancementResponse> {
        logger.info("Applying image overlay to {} files", request.files.size)

        return try {
            if (request.enhancementType != "imageOverlay") {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Invalid enhancement type for image overlay endpoint",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = request.enhancementType,
                        appliedToPages = null
                    )
                )
            }

            val overlayImage = request.overlayImage 
                ?: throw IllegalArgumentException("Overlay image is required for image overlay operations")

            // Validate and process image overlay
            val config = ImageOverlayService.ImageOverlayConfig(
                overlayImage = overlayImage,
                scale = request.overlayScale,
                opacity = request.overlayOpacity,
                position = request.position,
                customX = request.customX,
                customY = request.customY
            )

            val validationErrors = imageOverlayService.validateImageOverlayConfig(config)
            if (validationErrors.isNotEmpty()) {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Validation failed: ${validationErrors.joinToString(", ")}",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = "imageOverlay",
                        appliedToPages = null
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            val result = imageOverlayService.applyImageOverlay(
                request.files, config, request.pageNumbers
            )
            val processingTime = System.currentTimeMillis() - startTime

            // Log audit trail
            visualEnhancementAuditService.logOperation(
                operationType = "IMAGE_OVERLAY",
                fileName = request.files.joinToString(", ") { it.originalFilename ?: "unknown" },
                enhancementType = "imageOverlay",
                parameters = mapOf(
                    "scale" to request.overlayScale,
                    "opacity" to request.overlayOpacity,
                    "position" to request.position
                ),
                result = if (result.success) "SUCCESS" else "FAILURE",
                duration = processingTime
            )

            ResponseEntity.ok(
                VisualEnhancementResponse(
                    success = result.success,
                    message = result.message,
                    outputFileName = result.outputFileName,
                    totalPages = result.totalPages,
                    processedFiles = result.processedFiles,
                    processingTimeMs = processingTime,
                    fileSize = result.fileSize,
                    enhancementType = "imageOverlay",
                    appliedToPages = if (request.pageNumbers.isEmpty()) null else request.pageNumbers
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to apply image overlay: {}", e.message)
            ResponseEntity.internalServerError().body(
                VisualEnhancementResponse(
                    success = false,
                    message = "Failed to apply image overlay: ${e.message}",
                    outputFileName = null,
                    totalPages = null,
                    processedFiles = 0,
                    processingTimeMs = 0,
                    fileSize = null,
                    enhancementType = "imageOverlay",
                    appliedToPages = null
                )
            )
        }
    }

    /**
     * Applies stamps to PDF files.
     */
    @PostMapping("/stamp", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applyStamp(
        @Valid @ModelAttribute request: VisualEnhancementRequest
    ): ResponseEntity<VisualEnhancementResponse> {
        logger.info("Applying stamp to {} files", request.files.size)

        return try {
            if (request.enhancementType != "stamp") {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Invalid enhancement type for stamp endpoint",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = request.enhancementType,
                        appliedToPages = null
                    )
                )
            }

            // Create stamp configuration
            val config = StampApplicationService.StampConfig(
                stampType = request.stampType,
                stampText = request.stampText,
                stampImage = request.stampImage,
                stampSize = request.stampSize,
                customWidth = request.customStampWidth,
                customHeight = request.customStampHeight,
                position = request.position,
                customX = request.customX,
                customY = request.customY
            )

            val validationErrors = stampApplicationService.validateStampConfig(config)
            if (validationErrors.isNotEmpty()) {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Validation failed: ${validationErrors.joinToString(", ")}",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = "stamp",
                        appliedToPages = null
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            val result = stampApplicationService.applyStamp(
                request.files, config, request.pageNumbers
            )
            val processingTime = System.currentTimeMillis() - startTime

            // Log audit trail
            visualEnhancementAuditService.logOperation(
                operationType = "STAMP_APPLICATION",
                fileName = request.files.joinToString(", ") { it.originalFilename ?: "unknown" },
                enhancementType = "stamp",
                parameters = mapOf(
                    "stampType" to request.stampType,
                    "stampSize" to request.stampSize,
                    "position" to request.position
                ),
                result = if (result.success) "SUCCESS" else "FAILURE",
                duration = processingTime
            )

            ResponseEntity.ok(
                VisualEnhancementResponse(
                    success = result.success,
                    message = result.message,
                    outputFileName = result.outputFileName,
                    totalPages = result.totalPages,
                    processedFiles = result.processedFiles,
                    processingTimeMs = processingTime,
                    fileSize = result.fileSize,
                    enhancementType = "stamp",
                    appliedToPages = if (request.pageNumbers.isEmpty()) null else request.pageNumbers
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to apply stamp: {}", e.message)
            ResponseEntity.internalServerError().body(
                VisualEnhancementResponse(
                    success = false,
                    message = "Failed to apply stamp: ${e.message}",
                    outputFileName = null,
                    totalPages = null,
                    processedFiles = 0,
                    processingTimeMs = 0,
                    fileSize = null,
                    enhancementType = "stamp",
                    appliedToPages = null
                )
            )
        }
    }

    /**
     * Applies color manipulation to PDF files.
     */
    @PostMapping("/color", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applyColorManipulation(
        @Valid @ModelAttribute request: VisualEnhancementRequest
    ): ResponseEntity<VisualEnhancementResponse> {
        logger.info("Applying color manipulation to {} files", request.files.size)

        return try {
            if (request.enhancementType != "colorManipulation") {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Invalid enhancement type for color manipulation endpoint",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = request.enhancementType,
                        appliedToPages = null
                    )
                )
            }

            val config = ColorManipulationService.ColorConfig(
                operation = request.colorOperation,
                intensity = request.colorIntensity,
                hueShift = request.hueShift
            )

            val validationErrors = colorManipulationService.validateColorConfig(config)
            if (validationErrors.isNotEmpty()) {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Validation failed: ${validationErrors.joinToString(", ")}",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = "colorManipulation",
                        appliedToPages = null
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            val result = colorManipulationService.applyColorManipulation(
                request.files, config, request.pageNumbers
            )
            val processingTime = System.currentTimeMillis() - startTime

            // Log audit trail
            visualEnhancementAuditService.logOperation(
                operationType = "COLOR_MANIPULATION",
                fileName = request.files.joinToString(", ") { it.originalFilename ?: "unknown" },
                enhancementType = "colorManipulation",
                parameters = mapOf(
                    "operation" to request.colorOperation,
                    "intensity" to request.colorIntensity,
                    "hueShift" to request.hueShift
                ),
                result = if (result.success) "SUCCESS" else "FAILURE",
                duration = processingTime
            )

            ResponseEntity.ok(
                VisualEnhancementResponse(
                    success = result.success,
                    message = result.message,
                    outputFileName = result.outputFileName,
                    totalPages = result.totalPages,
                    processedFiles = result.processedFiles,
                    processingTimeMs = processingTime,
                    fileSize = result.fileSize,
                    enhancementType = "colorManipulation",
                    appliedToPages = if (request.pageNumbers.isEmpty()) null else request.pageNumbers
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to apply color manipulation: {}", e.message)
            ResponseEntity.internalServerError().body(
                VisualEnhancementResponse(
                    success = false,
                    message = "Failed to apply color manipulation: ${e.message}",
                    outputFileName = null,
                    totalPages = null,
                    processedFiles = 0,
                    processingTimeMs = 0,
                    fileSize = null,
                    enhancementType = "colorManipulation",
                    appliedToPages = null
                )
            )
        }
    }

    /**
     * Applies form flattening to PDF files.
     */
    @PostMapping("/form-flatten", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applyFormFlattening(
        @Valid @ModelAttribute request: VisualEnhancementRequest
    ): ResponseEntity<VisualEnhancementResponse> {
        logger.info("Applying form flattening to {} files", request.files.size)

        return try {
            if (request.enhancementType != "formFlattening") {
                return ResponseEntity.badRequest().body(
                    VisualEnhancementResponse(
                        success = false,
                        message = "Invalid enhancement type for form flattening endpoint",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = 0,
                        fileSize = null,
                        enhancementType = request.enhancementType,
                        appliedToPages = null
                    )
                )
            }

            val config = FormFlatteningService.FlattenConfig(
                preserveFormData = request.preserveFormData,
                flattenSignatures = request.flattenSignatures,
                flattenAnnotations = request.flattenAnnotations
            )

            val startTime = System.currentTimeMillis()
            val result = formFlatteningService.flattenForms(request.files, config)
            val processingTime = System.currentTimeMillis() - startTime

            // Log audit trail
            visualEnhancementAuditService.logOperation(
                operationType = "FORM_FLATTENING",
                fileName = request.files.joinToString(", ") { it.originalFilename ?: "unknown" },
                enhancementType = "formFlattening",
                parameters = mapOf(
                    "preserveFormData" to request.preserveFormData,
                    "flattenSignatures" to request.flattenSignatures,
                    "flattenAnnotations" to request.flattenAnnotations
                ),
                result = if (result.success) "SUCCESS" else "FAILURE",
                duration = processingTime
            )

            ResponseEntity.ok(
                VisualEnhancementResponse(
                    success = result.success,
                    message = result.message,
                    outputFileName = result.outputFileName,
                    totalPages = result.totalPages,
                    processedFiles = result.processedFiles,
                    processingTimeMs = processingTime,
                    fileSize = result.fileSize,
                    enhancementType = "formFlattening",
                    appliedToPages = null // Form flattening applies to entire document
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to apply form flattening: {}", e.message)
            ResponseEntity.internalServerError().body(
                VisualEnhancementResponse(
                    success = false,
                    message = "Failed to apply form flattening: ${e.message}",
                    outputFileName = null,
                    totalPages = null,
                    processedFiles = 0,
                    processingTimeMs = 0,
                    fileSize = null,
                    enhancementType = "formFlattening",
                    appliedToPages = null
                )
            )
        }
    }

    /**
     * Generates a preview of visual enhancement.
     */
    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun generatePreview(
        @Valid @ModelAttribute request: VisualEnhancementPreviewRequest
    ): ResponseEntity<VisualEnhancementPreviewResponse> {
        logger.info("Generating visual enhancement preview")

        return try {
            val previewResult = visualEnhancementPreviewService.generatePreview(request)

            ResponseEntity.ok(previewResult)

        } catch (e: Exception) {
            logger.error("Failed to generate preview: {}", e.message)
            ResponseEntity.internalServerError().body(
                VisualEnhancementPreviewResponse(
                    success = false,
                    message = "Failed to generate preview: ${e.message}",
                    previewImageBase64 = null,
                    enhancementDetails = emptyMap()
                )
            )
        }
    }

    /**
     * Starts batch visual enhancement operation.
     */
    @PostMapping("/batch", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun startBatchVisualEnhancement(
        @Valid @ModelAttribute request: BatchVisualEnhancementRequest
    ): ResponseEntity<Map<String, String>> {
        logger.info("Starting batch visual enhancement for {} jobs", request.enhancementJobs.size)

        return try {
            val operationId = visualEnhancementBatchService.startBatchProcessing(request)

            ResponseEntity.ok(
                mapOf(
                    "success" to "true",
                    "message" to "Batch visual enhancement started successfully",
                    "operationId" to operationId
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to start batch visual enhancement: {}", e.message)
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
    fun getBatchProgress(@PathVariable operationId: String): ResponseEntity<VisualEnhancementProgressResponse> {
        logger.debug("Getting batch progress for operation: {}", operationId)

        val progress = visualEnhancementBatchService.getBatchProgress(operationId)
        return if (progress != null) {
            ResponseEntity.ok(progress)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Performs quality assessment on a document.
     */
    @PostMapping("/quality-assessment", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun assessDocumentQuality(
        @Valid @ModelAttribute request: QualityAssessmentRequest
    ): ResponseEntity<QualityAssessmentResponse> {
        logger.info("Performing quality assessment on document")

        return try {
            val assessmentResult = documentQualityAssessmentService.assessDocument(request)
            ResponseEntity.ok(assessmentResult)

        } catch (e: Exception) {
            logger.error("Failed to perform quality assessment: {}", e.message)
            ResponseEntity.internalServerError().body(
                QualityAssessmentResponse(
                    success = false,
                    message = "Failed to perform quality assessment: ${e.message}",
                    overallScore = 0.0,
                    assessmentResults = emptyMap(),
                    processingTimeMs = 0
                )
            )
        }
    }

    /**
     * Gets audit trail for operations.
     */
    @GetMapping("/audit")
    fun getAuditTrail(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) operationType: String?,
        @RequestParam(required = false) enhancementType: String?,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<List<AuditTrailEntry>> {
        logger.debug("Getting audit trail with limit: {}", limit)

        return try {
            val auditEntries = visualEnhancementAuditService.getAuditTrail(
                limit = limit,
                operationType = operationType,
                enhancementType = enhancementType,
                userId = userId
            )
            ResponseEntity.ok(auditEntries)

        } catch (e: Exception) {
            logger.error("Failed to get audit trail: {}", e.message)
            ResponseEntity.internalServerError().body(emptyList())
        }
    }

    /**
     * Manages stamp library operations.
     */
    @PostMapping("/stamps", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createStamp(
        @Valid @ModelAttribute request: StampLibraryRequest
    ): ResponseEntity<StampLibraryResponse> {
        logger.info("Creating stamp: {}", request.stampName)

        return try {
            val result = stampLibraryManagementService.createStamp(request)
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.badRequest().body(result)
            }

        } catch (e: Exception) {
            logger.error("Failed to create stamp: {}", e.message)
            ResponseEntity.internalServerError().body(
                StampLibraryResponse(
                    success = false,
                    message = "Failed to create stamp: ${e.message}",
                    stampId = null
                )
            )
        }
    }

    /**
     * Gets all stamps from library.
     */
    @GetMapping("/stamps")
    fun getAllStamps(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<StampLibraryResponse> {
        logger.debug("Getting stamps from library")

        return try {
            val result = stampLibraryManagementService.getAllStamps(category, type)
            ResponseEntity.ok(result)

        } catch (e: Exception) {
            logger.error("Failed to get stamps: {}", e.message)
            ResponseEntity.internalServerError().body(
                StampLibraryResponse(
                    success = false,
                    message = "Failed to get stamps: ${e.message}",
                    stampId = null
                )
            )
        }
    }

    /**
     * Deletes a stamp from library.
     */
    @DeleteMapping("/stamps/{stampId}")
    fun deleteStamp(@PathVariable stampId: String): ResponseEntity<StampLibraryResponse> {
        logger.info("Deleting stamp: {}", stampId)

        return try {
            val result = stampLibraryManagementService.deleteStamp(stampId)
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("Failed to delete stamp: {}", e.message)
            ResponseEntity.internalServerError().body(
                StampLibraryResponse(
                    success = false,
                    message = "Failed to delete stamp: ${e.message}",
                    stampId = null
                )
            )
        }
    }

    /**
     * Gets visual enhancement configuration options.
     */
    @GetMapping("/config")
    fun getVisualEnhancementConfiguration(): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting visual enhancement configuration")

        return ResponseEntity.ok(
            mapOf(
                "supportedEnhancementTypes" to listOf("imageOverlay", "stamp", "colorManipulation", "formFlattening"),
                "supportedColorOperations" to listOf("invert", "grayscale", "sepia", "brightness", "contrast", "saturation", "hue"),
                "supportedStampTypes" to listOf("custom", "approved", "draft", "confidential", "urgent"),
                "supportedStampSizes" to listOf("small", "medium", "large", "custom"),
                "supportedPositions" to listOf(
                    "center", "top-left", "top-center", "top-right",
                    "center-left", "center-right", "bottom-left", "bottom-center", "bottom-right", "custom"
                ),
                "qualityLevels" to listOf("low", "medium", "high", "maximum"),
                "maxOverlayOpacity" to 1.0,
                "minOverlayOpacity" to 0.0,
                "maxColorIntensity" to 2.0,
                "minColorIntensity" to 0.0,
                "hueShiftRange" to mapOf("min" to -180.0, "max" to 180.0),
                "stampCategories" to listOf("general", "approval", "status", "custom")
            )
        )
    }
}