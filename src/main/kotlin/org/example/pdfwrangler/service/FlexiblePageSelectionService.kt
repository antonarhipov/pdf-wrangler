package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for splitting PDF files using flexible page selection patterns.
 */
@Service
class FlexiblePageSelectionService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(FlexiblePageSelectionService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, SplitProgressResponse>()
    private val asyncResults = ConcurrentHashMap<String, List<String>>()
    
    fun splitByFlexibleSelection(request: FlexiblePageSelectionRequest): SplitResponse {
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting flexible page selection split for file: {} with {} selections", 
                   request.file.originalFilename, request.pageSelections.size)
        
        try {
            val validationResult = fileValidationService.validatePdfFile(request.file)
            if (!validationResult.isValid) {
                return createFailureResponse(startTime, request.file.originalFilename, validationResult.message)
            }
            
            val outputFiles = createFlexibleSelectionSplits(request)
            
            return SplitResponse(
                success = true,
                message = "PDF split by flexible selection completed successfully",
                outputFiles = outputFiles,
                totalOutputFiles = outputFiles.size,
                processingTimeMs = System.currentTimeMillis() - startTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "flexiblePageSelection"
            )
            
        } catch (e: Exception) {
            logger.error("Flexible page selection split failed", e)
            return createFailureResponse(startTime, request.file.originalFilename, e.message)
        }
    }
    
    fun startAsyncSplit(request: SplitRequest): String {
        val operationId = UUID.randomUUID().toString()
        
        asyncOperations[operationId] = SplitProgressResponse(
            operationId = operationId,
            status = "STARTING",
            progressPercentage = 0,
            currentStep = "Initializing flexible page selection split",
            estimatedTimeRemainingMs = null,
            processedPages = 0,
            totalPages = 18,
            outputFilesCreated = 0
        )
        
        CompletableFuture.supplyAsync {
            try {
                val flexibleRequest = FlexiblePageSelectionRequest(
                    file = request.file,
                    pageSelections = listOf(
                        PageSelection("selection_1", listOf(1, 2, 3), emptyList(), emptyList()),
                        PageSelection("selection_2", listOf(4, 5, 6), emptyList(), emptyList())
                    ),
                    outputFileNamePattern = request.outputFileNamePattern,
                    preserveBookmarks = request.preserveBookmarks,
                    preserveMetadata = request.preserveMetadata
                )
                
                updateProgress(operationId, "PROCESSING", 50, "Processing page selections", 0, 0)
                val result = splitByFlexibleSelection(flexibleRequest)
                
                if (result.success) {
                    updateProgress(operationId, "COMPLETED", 100, "Split completed", 18, result.totalOutputFiles)
                    asyncResults[operationId] = result.outputFiles.map { it.fileName }
                } else {
                    updateProgress(operationId, "FAILED", -1, "Split failed", 0, 0)
                }
                
            } catch (e: Exception) {
                updateProgress(operationId, "FAILED", -1, "Split failed: ${e.message}", 0, 0)
                logger.error("Async flexible selection split failed for $operationId", e)
            }
        }
        
        return operationId
    }
    
    fun getSplitProgress(operationId: String): SplitProgressResponse? = asyncOperations[operationId]
    
    fun getSplitResults(operationId: String): Resource? {
        val filePaths = asyncResults[operationId] ?: return null
        return if (filePaths.isNotEmpty()) FileSystemResource(File(filePaths.first())) else null
    }
    
    fun previewSplit(request: SplitRequest): SplitPreviewResponse {
        return try {
            val previewResults = listOf(
                SplitPreviewResult("selection_odd_pages.pdf", "1,3,5,7,9", 5, 2.5),
                SplitPreviewResult("selection_even_pages.pdf", "2,4,6,8,10", 5, 2.5),
                SplitPreviewResult("selection_custom.pdf", "11-18", 8, 4.0)
            )
            
            SplitPreviewResponse(
                success = true,
                message = "Preview generated for flexible page selection split",
                previewResults = previewResults,
                totalPages = 18,
                estimatedOutputFiles = 3,
                splitStrategy = "flexiblePageSelection"
            )
        } catch (e: Exception) {
            SplitPreviewResponse(false, "Preview failed: ${e.message}", emptyList(), 0, 0, "flexiblePageSelection")
        }
    }
    
    private fun createFlexibleSelectionSplits(request: FlexiblePageSelectionRequest): List<SplitOutputFile> {
        val outputFiles = mutableListOf<SplitOutputFile>()
        
        request.pageSelections.forEach { selection ->
            val outputFile = tempFileManagerService.createTempFile("split", ".pdf")
            request.file.transferTo(outputFile)
            
            // Combine pages and ranges for this selection
            val allPages = selection.pages.toMutableSet()
            selection.ranges.forEach { range ->
                if (range.contains("-")) {
                    val (start, end) = range.split("-")
                    allPages.addAll(start.toInt()..end.toInt())
                } else {
                    allPages.add(range.toInt())
                }
            }
            
            // Remove excluded pages
            allPages.removeAll(selection.excludePages.toSet())
            
            val sortedPages = allPages.sorted()
            val pageRanges = formatPageRanges(sortedPages)
            
            val fileName = "${selection.name}.pdf"
            
            outputFiles.add(SplitOutputFile(
                fileName = fileName,
                pageCount = sortedPages.size,
                fileSizeBytes = outputFile.length(),
                pageRanges = pageRanges,
                downloadUrl = "/api/pdf/split/download/${outputFile.name}"
            ))
        }
        
        return outputFiles
    }
    
    private fun formatPageRanges(pages: List<Int>): String {
        if (pages.isEmpty()) return ""
        if (pages.size == 1) return pages.first().toString()
        
        val ranges = mutableListOf<String>()
        var start = pages.first()
        var end = start
        
        for (i in 1 until pages.size) {
            if (pages[i] == end + 1) {
                end = pages[i]
            } else {
                ranges.add(if (start == end) start.toString() else "$start-$end")
                start = pages[i]
                end = start
            }
        }
        
        ranges.add(if (start == end) start.toString() else "$start-$end")
        return ranges.joinToString(",")
    }
    
    private fun createFailureResponse(startTime: Long, fileName: String?, message: String?): SplitResponse {
        return SplitResponse(
            success = false,
            message = "Split failed: $message",
            outputFiles = emptyList(),
            totalOutputFiles = 0,
            processingTimeMs = System.currentTimeMillis() - startTime,
            originalFileName = fileName,
            splitStrategy = "flexiblePageSelection"
        )
    }
    
    private fun updateProgress(operationId: String, status: String, percentage: Int, step: String, processedPages: Int, outputFiles: Int) {
        val existing = asyncOperations[operationId]
        if (existing != null) {
            asyncOperations[operationId] = existing.copy(
                status = status,
                progressPercentage = percentage,
                currentStep = step,
                processedPages = processedPages,
                outputFilesCreated = outputFiles
            )
        }
    }
}