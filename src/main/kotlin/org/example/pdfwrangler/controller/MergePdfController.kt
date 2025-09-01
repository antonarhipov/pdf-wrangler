package org.example.pdfwrangler.controller

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.service.MergePdfService
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
 * REST controller for PDF merge operations.
 * Provides endpoints for single merge, batch merge, and progress tracking.
 */
@RestController
@RequestMapping("/api/pdf/merge")
@Validated
class MergePdfController(
    private val mergePdfService: MergePdfService
) {
    
    private val logger = LoggerFactory.getLogger(MergePdfController::class.java)
    
    /**
     * Merge multiple PDF files into a single PDF.
     * 
     * @param request The merge request containing files and configuration
     * @return ResponseEntity with the merged PDF file or error response
     */
    @PostMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_PDF_VALUE]
    )
    fun mergePdfs(
        @Valid @ModelAttribute request: MergeRequest
    ): ResponseEntity<Resource> {
        logger.info("Starting PDF merge operation with {} files, sort order: {}", 
                   request.files.size, request.sortOrder)
        
        val result = mergePdfService.mergePdfs(request)
        
        val headers = HttpHeaders().apply {
            contentDisposition = org.springframework.http.ContentDisposition
                .attachment()
                .filename(result.outputFileName ?: "merged.pdf")
                .build()
            contentType = MediaType.APPLICATION_PDF
        }
        
        logger.info("PDF merge operation completed successfully. Output: {}, Pages: {}, Processing time: {}ms",
                   result.outputFileName, result.totalPages, result.processingTimeMs)
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(mergePdfService.getMergedFileResource(result.outputFileName!!))
    }
    
    /**
     * Get merge operation status and response details.
     * 
     * @param request The merge request containing files and configuration
     * @return ResponseEntity with merge response details
     */
    @PostMapping(
        "/status",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getMergeStatus(
        @Valid @ModelAttribute request: MergeRequest
    ): ResponseEntity<MergeResponse> {
        logger.info("Getting merge status for {} files", request.files.size)
        
        val response = mergePdfService.getMergeStatus(request)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Start an asynchronous PDF merge operation with progress tracking.
     * 
     * @param request The merge request containing files and configuration
     * @return ResponseEntity with operation ID for progress tracking
     */
    @PostMapping(
        "/async",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun startAsyncMerge(
        @Valid @ModelAttribute request: MergeRequest
    ): ResponseEntity<Map<String, String>> {
        logger.info("Starting async PDF merge operation with {} files", request.files.size)
        
        val operationId = mergePdfService.startAsyncMerge(request)
        
        return ResponseEntity.ok(mapOf("operationId" to operationId))
    }
    
    /**
     * Get progress status for an asynchronous merge operation.
     * 
     * @param operationId The operation ID returned from startAsyncMerge
     * @return ResponseEntity with progress details
     */
    @GetMapping("/progress/{operationId}")
    fun getMergeProgress(
        @PathVariable operationId: String
    ): ResponseEntity<MergeProgressResponse> {
        logger.debug("Getting merge progress for operation: {}", operationId)
        
        val progress = mergePdfService.getMergeProgress(operationId)
        
        return ResponseEntity.ok(progress)
    }
    
    /**
     * Download the result of an asynchronous merge operation.
     * 
     * @param operationId The operation ID
     * @return ResponseEntity with the merged PDF file
     */
    @GetMapping(
        "/download/{operationId}",
        produces = [MediaType.APPLICATION_PDF_VALUE]
    )
    fun downloadAsyncResult(
        @PathVariable operationId: String
    ): ResponseEntity<Resource> {
        logger.info("Downloading async merge result for operation: {}", operationId)
        
        val resource = mergePdfService.getAsyncMergeResult(operationId)
        
        val headers = HttpHeaders().apply {
            contentDisposition = org.springframework.http.ContentDisposition
                .attachment()
                .filename("merged_${operationId}.pdf")
                .build()
            contentType = MediaType.APPLICATION_PDF
        }
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(resource)
    }
    
    /**
     * Perform batch PDF merge operations.
     * 
     * @param request The batch merge request containing multiple merge jobs
     * @return ResponseEntity with batch processing results
     */
    @PostMapping(
        "/batch",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun batchMergePdfs(
        @Valid @ModelAttribute request: BatchMergeRequest
    ): ResponseEntity<BatchMergeResponse> {
        logger.info("Starting batch PDF merge operation with {} jobs", request.mergeJobs.size)
        
        val response = mergePdfService.batchMergePdfs(request)
        
        logger.info("Batch merge operation completed. Successful: {}, Failed: {}, Total time: {}ms",
                   response.completedJobs, response.failedJobs, response.totalProcessingTimeMs)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Validate merge request without performing the actual merge.
     * 
     * @param request The merge request to validate
     * @return ResponseEntity with validation results
     */
    @PostMapping(
        "/validate",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun validateMergeRequest(
        @Valid @ModelAttribute request: MergeRequest
    ): ResponseEntity<Map<String, Any>> {
        logger.debug("Validating merge request with {} files", request.files.size)
        
        val validationResult = mergePdfService.validateMergeRequest(request)
        
        return ResponseEntity.ok(validationResult)
    }
    
    /**
     * Get merge configuration options and supported features.
     * 
     * @return ResponseEntity with configuration details
     */
    @GetMapping("/config")
    fun getMergeConfiguration(): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting merge configuration")
        
        val config = mergePdfService.getMergeConfiguration()
        
        return ResponseEntity.ok(config)
    }
}