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
import jakarta.validation.Valid

/**
 * REST controller for page-level PDF manipulation operations.
 * Provides endpoints for rotation, rearrangement, scaling, cropping, and other page operations.
 */
@RestController
@RequestMapping("/api/page-operations")
@Validated
class PageOperationsController(
    private val pageRotationService: PageRotationService,
    private val pageRearrangementService: PageRearrangementService,
    private val pageScalingService: PageScalingService,
    private val multiPageToSinglePageConverter: MultiPageToSinglePageConverter,
    private val multiplePagesPerSheetService: MultiplePagesPerSheetService,
    private val pageCroppingService: PageCroppingService,
    private val blankPageDetectionService: BlankPageDetectionService,
    private val customPageNumberingService: CustomPageNumberingService
) {
    
    private val logger = LoggerFactory.getLogger(PageOperationsController::class.java)

    /**
     * Rotate pages in a PDF document.
     */
    @PostMapping("/rotate", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun rotatePdfPages(@Valid @ModelAttribute request: PageRotationRequest): ResponseEntity<Resource> {
        logger.info("Starting PDF page rotation operation for file: ${request.file.originalFilename}")
        
        return try {
            val response = pageRotationService.rotatePages(request)
            val resource = pageRotationService.getRotatedFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during PDF page rotation", e)
            throw e
        }
    }

    /**
     * Rearrange pages in a PDF document (reorder, duplicate, or remove).
     */
    @PostMapping("/rearrange", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun rearrangePdfPages(@Valid @ModelAttribute request: PageRearrangementRequest): ResponseEntity<Resource> {
        logger.info("Starting PDF page rearrangement operation: ${request.operation} for file: ${request.file.originalFilename}")
        
        return try {
            val response = pageRearrangementService.rearrangePages(request)
            val resource = pageRearrangementService.getRearrangedFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during PDF page rearrangement", e)
            throw e
        }
    }

    /**
     * Scale pages in a PDF document.
     */
    @PostMapping("/scale", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun scalePdfPages(@Valid @ModelAttribute request: PageScalingRequest): ResponseEntity<Resource> {
        logger.info("Starting PDF page scaling operation: ${request.scaleType} for file: ${request.file.originalFilename}")
        
        return try {
            val response = pageScalingService.scalePages(request)
            val resource = pageScalingService.getScaledFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during PDF page scaling", e)
            throw e
        }
    }

    /**
     * Convert multi-page layout to single-page layout.
     */
    @PostMapping("/convert-to-single-page", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun convertToSinglePage(@Valid @ModelAttribute request: MultiPageToSinglePageRequest): ResponseEntity<Resource> {
        logger.info("Starting multi-page to single-page conversion for file: ${request.file.originalFilename}")
        
        return try {
            val response = multiPageToSinglePageConverter.convertToSinglePage(request)
            val resource = multiPageToSinglePageConverter.getConvertedFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during multi-page to single-page conversion", e)
            throw e
        }
    }

    /**
     * Arrange multiple pages per sheet for layout optimization.
     */
    @PostMapping("/multiple-per-sheet", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun arrangeMultiplePagesPerSheet(@Valid @ModelAttribute request: MultiplePagesPerSheetRequest): ResponseEntity<Resource> {
        logger.info("Starting multiple pages per sheet arrangement for file: ${request.file.originalFilename}")
        
        return try {
            val response = multiplePagesPerSheetService.arrangePages(request)
            val resource = multiplePagesPerSheetService.getArrangedFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during multiple pages per sheet arrangement", e)
            throw e
        }
    }

    /**
     * Crop pages in a PDF document.
     */
    @PostMapping("/crop", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun cropPdfPages(@Valid @ModelAttribute request: PageCroppingRequest): ResponseEntity<Resource> {
        logger.info("Starting PDF page cropping operation: ${request.cropType} for file: ${request.file.originalFilename}")
        
        return try {
            val response = pageCroppingService.cropPages(request)
            val resource = pageCroppingService.getCroppedFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during PDF page cropping", e)
            throw e
        }
    }

    /**
     * Detect and optionally remove blank pages.
     */
    @PostMapping("/detect-blank-pages", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun detectBlankPages(@Valid @ModelAttribute request: BlankPageDetectionRequest): ResponseEntity<Any> {
        logger.info("Starting blank page detection for file: ${request.file.originalFilename}")
        
        return try {
            val response = blankPageDetectionService.detectBlankPages(request)
            
            if (request.removeBlankPages && response.outputFileName != null) {
                // Return file if blank pages were removed
                val resource = blankPageDetectionService.getProcessedFileResource(response.outputFileName)
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                    .header("X-Processing-Time", response.processingTimeMs.toString())
                    .header("X-Total-Pages", response.totalPages.toString())
                    .header("X-Blank-Pages", response.blankPages.joinToString(","))
                    .body(resource)
            } else {
                // Return detection results only
                ResponseEntity.ok()
                    .header("X-Processing-Time", response.processingTimeMs.toString())
                    .header("X-Total-Pages", response.totalPages.toString())
                    .header("X-Blank-Pages", response.blankPages.joinToString(","))
                    .body(response)
            }
        } catch (e: Exception) {
            logger.error("Error during blank page detection", e)
            throw e
        }
    }

    /**
     * Add custom page numbering to a PDF document.
     */
    @PostMapping("/add-page-numbers", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addCustomPageNumbers(@Valid @ModelAttribute request: CustomPageNumberingRequest): ResponseEntity<Resource> {
        logger.info("Starting custom page numbering for file: ${request.file.originalFilename}")
        
        return try {
            val response = customPageNumberingService.addPageNumbers(request)
            val resource = customPageNumberingService.getNumberedFileResource(response.outputFileName!!)
            
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${response.outputFileName}\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header("X-Processing-Time", response.processingTimeMs.toString())
                .header("X-Total-Pages", response.totalPages.toString())
                .body(resource)
        } catch (e: Exception) {
            logger.error("Error during custom page numbering", e)
            throw e
        }
    }

    /**
     * Validate page operation request and provide preview information.
     */
    @PostMapping("/validate", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun validatePageOperation(@Valid @ModelAttribute request: PageOperationValidationRequest): ResponseEntity<PageOperationValidationResponse> {
        logger.info("Validating page operation: ${request.operationType} for file: ${request.file.originalFilename}")
        
        return try {
            val response = when (request.operationType) {
                "rotation" -> pageRotationService.validateOperation(request)
                "rearrangement" -> pageRearrangementService.validateOperation(request)
                "scaling" -> pageScalingService.validateOperation(request)
                "conversion" -> multiPageToSinglePageConverter.validateOperation(request)
                "cropping" -> pageCroppingService.validateOperation(request)
                "numbering" -> customPageNumberingService.validateOperation(request)
                else -> throw IllegalArgumentException("Unsupported operation type: ${request.operationType}")
            }
            
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Error during page operation validation", e)
            throw e
        }
    }

    /**
     * Get configuration options for page operations.
     */
    @GetMapping("/configuration")
    fun getPageOperationsConfiguration(): ResponseEntity<Map<String, Any>> {
        logger.info("Retrieving page operations configuration")
        
        return try {
            val config = mapOf(
                "supportedRotationAngles" to listOf(90, 180, 270, -90, -180, -270),
                "supportedRearrangementOperations" to listOf("reorder", "duplicate", "remove"),
                "supportedScaleTypes" to listOf("percentage", "fitToSize", "customDimensions"),
                "supportedPaperSizes" to listOf("A4", "A3", "A5", "LETTER", "LEGAL", "TABLOID"),
                "supportedLayouts" to mapOf(
                    "multiPageToSingle" to listOf("vertical", "horizontal"),
                    "multiplePerSheet" to listOf("grid", "linear")
                ),
                "supportedCropTypes" to listOf("removeMargins", "customCrop", "autoDetect"),
                "supportedNumberFormats" to listOf("arabic", "roman", "ROMAN", "alpha", "ALPHA"),
                "supportedNumberPositions" to listOf("topLeft", "topCenter", "topRight", "bottomLeft", "bottomCenter", "bottomRight"),
                "limits" to mapOf(
                    "maxScalePercentage" to 1000,
                    "minScalePercentage" to 1,
                    "maxPagesPerSheet" to 16,
                    "minPagesPerSheet" to 2,
                    "maxFontSize" to 72,
                    "minFontSize" to 6,
                    "maxBlankDetectionSensitivity" to 100,
                    "minBlankDetectionSensitivity" to 0
                )
            )
            
            ResponseEntity.ok(config)
        } catch (e: Exception) {
            logger.error("Error retrieving page operations configuration", e)
            throw e
        }
    }
}