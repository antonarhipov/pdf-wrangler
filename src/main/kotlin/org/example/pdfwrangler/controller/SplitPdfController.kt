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
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

/**
 * REST controller for PDF split operations.
 * Provides endpoints for multiple split strategies, batch processing, and preview functionality.
 */
@RestController
@RequestMapping("/api/pdf/split")
@Validated
class SplitPdfController(
    private val pageRangeSplitService: PageRangeSplitService,
    private val fileSizeSplitService: FileSizeSplitService,
    private val documentSectionSplitService: DocumentSectionSplitService,
    private val chapterBasedSplitService: ChapterBasedSplitService,
    private val flexiblePageSelectionService: FlexiblePageSelectionService,
    private val contentAwareSplitService: ContentAwareSplitService
) {
    
    private val logger = LoggerFactory.getLogger(SplitPdfController::class.java)
    
    /**
     * Split PDF using general split request with strategy selection.
     * Returns a ZIP file containing all split PDF files for immediate download.
     */
    @PostMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    fun splitPdf(
        @Valid @ModelAttribute request: SplitRequest
    ): ResponseEntity<Resource> {
        logger.info("Starting PDF split operation with strategy: {}", request.splitStrategy)
        
        val response = when (request.splitStrategy) {
            "pageRanges" -> {
                val pageRequest = PageRangeSplitRequest(
                    file = request.file,
                    pageRanges = request.pageRanges,
                    outputFileNamePattern = request.outputFileNamePattern,
                    preserveBookmarks = request.preserveBookmarks,
                    preserveMetadata = request.preserveMetadata
                )
                pageRangeSplitService.splitByPageRanges(pageRequest)
            }
            "fileSize" -> {
                val sizeRequest = FileSizeSplitRequest(
                    file = request.file,
                    fileSizeThresholdMB = request.fileSizeThresholdMB ?: 10,
                    outputFileNamePattern = request.outputFileNamePattern,
                    preserveBookmarks = request.preserveBookmarks,
                    preserveMetadata = request.preserveMetadata
                )
                fileSizeSplitService.splitByFileSize(sizeRequest)
            }
            "documentSection" -> {
                val sectionRequest = DocumentSectionSplitRequest(
                    file = request.file,
                    sectionType = "chapters",
                    outputFileNamePattern = request.outputFileNamePattern,
                    preserveBookmarks = request.preserveBookmarks,
                    preserveMetadata = request.preserveMetadata
                )
                documentSectionSplitService.splitByDocumentSection(sectionRequest)
            }
            "chapterBased" -> {
                chapterBasedSplitService.splitByChapters(request.file, request.outputFileNamePattern)
            }
            "contentAware" -> {
                contentAwareSplitService.splitByContent(request.file, request.outputFileNamePattern)
            }
            else -> {
                SplitResponse(
                    success = false,
                    message = "Unsupported split strategy: ${request.splitStrategy}",
                    outputFiles = emptyList(),
                    totalOutputFiles = 0,
                    processingTimeMs = 0,
                    originalFileName = request.file.originalFilename,
                    splitStrategy = request.splitStrategy
                )
            }
        }
        
        logger.info("Split operation completed. Strategy: {}, Output files: {}, Success: {}", 
                   request.splitStrategy, response.totalOutputFiles, response.success)
        
        // Check if split was successful before creating ZIP
        if (!response.success) {
            logger.error("Split operation failed: {}", response.message)
            throw org.example.pdfwrangler.exception.SplitException(response.message)
        }
        
        // Create ZIP archive from split results
        val zipResource = pageRangeSplitService.createZipFromSplitResponse(response)
        
        // Prepare download headers
        val originalFileName = request.file.originalFilename?.substringBeforeLast(".") ?: "document"
        val zipFileName = "${originalFileName}_split.zip"
        
        val headers = HttpHeaders().apply {
            contentDisposition = org.springframework.http.ContentDisposition
                .attachment()
                .filename(zipFileName)
                .build()
            contentType = MediaType.APPLICATION_OCTET_STREAM
        }
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(zipResource)
    }
    
    /**
     * Split PDF by specific page ranges.
     */
    @PostMapping(
        "/page-ranges",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun splitByPageRanges(
        @Valid @ModelAttribute request: PageRangeSplitRequest
    ): ResponseEntity<SplitResponse> {
        logger.info("Starting page range split for {} ranges", request.pageRanges.size)
        
        val response = pageRangeSplitService.splitByPageRanges(request)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Split PDF by file size threshold.
     */
    @PostMapping(
        "/file-size",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun splitByFileSize(
        @Valid @ModelAttribute request: FileSizeSplitRequest
    ): ResponseEntity<SplitResponse> {
        logger.info("Starting file size split with threshold: {}MB", request.fileSizeThresholdMB)
        
        val response = fileSizeSplitService.splitByFileSize(request)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Split PDF by document sections.
     */
    @PostMapping(
        "/document-section",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun splitByDocumentSection(
        @Valid @ModelAttribute request: DocumentSectionSplitRequest
    ): ResponseEntity<SplitResponse> {
        logger.info("Starting document section split by: {}", request.sectionType)
        
        val response = documentSectionSplitService.splitByDocumentSection(request)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Split PDF using flexible page selection.
     */
    @PostMapping(
        "/flexible-selection",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun splitByFlexibleSelection(
        @Valid @ModelAttribute request: FlexiblePageSelectionRequest
    ): ResponseEntity<SplitResponse> {
        logger.info("Starting flexible page selection split with {} selections", request.pageSelections.size)
        
        val response = flexiblePageSelectionService.splitByFlexibleSelection(request)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Start an asynchronous split operation with progress tracking.
     */
    @PostMapping(
        "/async",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun startAsyncSplit(
        @Valid @ModelAttribute request: SplitRequest
    ): ResponseEntity<Map<String, String>> {
        logger.info("Starting async split operation with strategy: {}", request.splitStrategy)
        
        val operationId = when (request.splitStrategy) {
            "pageRanges" -> pageRangeSplitService.startAsyncSplit(request)
            "fileSize" -> fileSizeSplitService.startAsyncSplit(request)
            "documentSection" -> documentSectionSplitService.startAsyncSplit(request)
            "chapterBased" -> chapterBasedSplitService.startAsyncSplit(request)
            "contentAware" -> contentAwareSplitService.startAsyncSplit(request)
            else -> throw IllegalArgumentException("Unsupported split strategy: ${request.splitStrategy}")
        }
        
        return ResponseEntity.ok(mapOf("operationId" to operationId))
    }
    
    /**
     * Get progress status for an asynchronous split operation.
     */
    @GetMapping("/progress/{operationId}")
    fun getSplitProgress(
        @PathVariable operationId: String
    ): ResponseEntity<SplitProgressResponse> {
        logger.debug("Getting split progress for operation: {}", operationId)
        
        // Try each service to find the operation
        val progress = pageRangeSplitService.getSplitProgress(operationId)
            ?: fileSizeSplitService.getSplitProgress(operationId)
            ?: documentSectionSplitService.getSplitProgress(operationId)
            ?: chapterBasedSplitService.getSplitProgress(operationId)
            ?: contentAwareSplitService.getSplitProgress(operationId)
            ?: throw IllegalArgumentException("Operation not found: $operationId")
        
        return ResponseEntity.ok(progress)
    }
    
    /**
     * Download split results as a ZIP file.
     */
    @GetMapping(
        "/download/{operationId}",
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    fun downloadSplitResults(
        @PathVariable operationId: String
    ): ResponseEntity<Resource> {
        logger.info("Downloading split results for operation: {}", operationId)
        
        // Try each service to find the operation results
        val resource = pageRangeSplitService.getSplitResults(operationId)
            ?: fileSizeSplitService.getSplitResults(operationId)
            ?: documentSectionSplitService.getSplitResults(operationId)
            ?: chapterBasedSplitService.getSplitResults(operationId)
            ?: contentAwareSplitService.getSplitResults(operationId)
            ?: throw IllegalArgumentException("Results not found for operation: $operationId")
        
        val headers = HttpHeaders().apply {
            contentDisposition = org.springframework.http.ContentDisposition
                .attachment()
                .filename("split_results_${operationId}.zip")
                .build()
            contentType = MediaType.APPLICATION_OCTET_STREAM
        }
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(resource)
    }
    
    /**
     * Preview split operation results without performing the actual split.
     */
    @PostMapping(
        "/preview",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun previewSplit(
        @Valid @ModelAttribute request: SplitRequest
    ): ResponseEntity<SplitPreviewResponse> {
        logger.debug("Previewing split operation with strategy: {}", request.splitStrategy)
        
        val preview = when (request.splitStrategy) {
            "pageRanges" -> pageRangeSplitService.previewSplit(request)
            "fileSize" -> fileSizeSplitService.previewSplit(request)
            "documentSection" -> documentSectionSplitService.previewSplit(request)
            "chapterBased" -> chapterBasedSplitService.previewSplit(request)
            "contentAware" -> contentAwareSplitService.previewSplit(request)
            else -> SplitPreviewResponse(
                success = false,
                message = "Unsupported split strategy: ${request.splitStrategy}",
                previewResults = emptyList(),
                totalPages = 0,
                estimatedOutputFiles = 0,
                splitStrategy = request.splitStrategy
            )
        }
        
        return ResponseEntity.ok(preview)
    }
    
    /**
     * Perform batch split operations.
     */
    @PostMapping(
        "/batch",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun batchSplitPdfs(
        @Valid @ModelAttribute request: BatchSplitRequest
    ): ResponseEntity<BatchSplitResponse> {
        logger.info("Starting batch split operation with {} jobs", request.splitJobs.size)
        
        val startTime = System.currentTimeMillis()
        var completedJobs = 0
        var failedJobs = 0
        val results = mutableListOf<SplitResponse>()
        
        for (job in request.splitJobs) {
            try {
                // Call service methods directly to get SplitResponse (not ZIP)
                val result = when (job.splitStrategy) {
                    "pageRanges" -> {
                        val pageRequest = PageRangeSplitRequest(
                            file = job.file,
                            pageRanges = job.pageRanges,
                            outputFileNamePattern = job.outputFileNamePattern,
                            preserveBookmarks = job.preserveBookmarks,
                            preserveMetadata = job.preserveMetadata
                        )
                        pageRangeSplitService.splitByPageRanges(pageRequest)
                    }
                    "fileSize" -> {
                        val sizeRequest = FileSizeSplitRequest(
                            file = job.file,
                            fileSizeThresholdMB = job.fileSizeThresholdMB ?: 10,
                            outputFileNamePattern = job.outputFileNamePattern,
                            preserveBookmarks = job.preserveBookmarks,
                            preserveMetadata = job.preserveMetadata
                        )
                        fileSizeSplitService.splitByFileSize(sizeRequest)
                    }
                    "documentSection" -> {
                        val sectionRequest = DocumentSectionSplitRequest(
                            file = job.file,
                            sectionType = "chapters",
                            outputFileNamePattern = job.outputFileNamePattern,
                            preserveBookmarks = job.preserveBookmarks,
                            preserveMetadata = job.preserveMetadata
                        )
                        documentSectionSplitService.splitByDocumentSection(sectionRequest)
                    }
                    "chapterBased" -> {
                        chapterBasedSplitService.splitByChapters(job.file, job.outputFileNamePattern)
                    }
                    "contentAware" -> {
                        contentAwareSplitService.splitByContent(job.file, job.outputFileNamePattern)
                    }
                    else -> {
                        SplitResponse(
                            success = false,
                            message = "Unsupported split strategy: ${job.splitStrategy}",
                            outputFiles = emptyList(),
                            totalOutputFiles = 0,
                            processingTimeMs = 0,
                            originalFileName = job.file.originalFilename,
                            splitStrategy = job.splitStrategy
                        )
                    }
                }
                
                results.add(result)
                
                if (result.success) {
                    completedJobs++
                } else {
                    failedJobs++
                }
                
            } catch (e: Exception) {
                logger.error("Batch split job failed", e)
                results.add(SplitResponse(
                    success = false,
                    message = "Split failed: ${e.message}",
                    outputFiles = emptyList(),
                    totalOutputFiles = 0,
                    processingTimeMs = 0,
                    originalFileName = job.file.originalFilename,
                    splitStrategy = job.splitStrategy
                ))
                failedJobs++
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        val response = BatchSplitResponse(
            success = failedJobs == 0,
            message = "Batch split completed: $completedJobs successful, $failedJobs failed",
            completedJobs = completedJobs,
            failedJobs = failedJobs,
            totalProcessingTimeMs = totalTime,
            results = results
        )
        
        logger.info("Batch split operation completed. Successful: {}, Failed: {}, Total time: {}ms",
                   completedJobs, failedJobs, totalTime)
        
        return ResponseEntity.ok(response)
    }
    
    /**
     * Get split configuration options and supported strategies.
     */
    @GetMapping("/config")
    fun getSplitConfiguration(): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting split configuration")
        
        val config = mapOf(
            "supportedStrategies" to listOf(
                "pageRanges", "fileSize", "documentSection", 
                "chapterBased", "flexiblePageSelection", "contentAware"
            ),
            "maxFileSizeMB" to 100,
            "supportedSectionTypes" to listOf("chapters", "sections", "bookmarks", "annotations"),
            "supportedFeatures" to mapOf(
                "preserveBookmarks" to true,
                "preserveMetadata" to true,
                "batchProcessing" to true,
                "asyncProcessing" to true,
                "progressTracking" to true,
                "previewMode" to true
            ),
            "pageRangeFormat" to "Examples: '1-5', '1,3,5', '1-5,8-10'",
            "outputFilePatterns" to listOf(
                "{original}_{range}.pdf",
                "{original}_part_{index}.pdf", 
                "split_{timestamp}_{index}.pdf"
            )
        )
        
        return ResponseEntity.ok(config)
    }
}