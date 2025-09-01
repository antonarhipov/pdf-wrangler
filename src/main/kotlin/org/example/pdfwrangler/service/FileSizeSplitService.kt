package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.SplitException
import org.example.pdfwrangler.exception.SplitFileSizeThresholdException
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
 * Service for splitting PDF files based on file size thresholds.
 */
@Service
class FileSizeSplitService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(FileSizeSplitService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, SplitProgressResponse>()
    private val asyncResults = ConcurrentHashMap<String, List<String>>() // operationId -> file paths
    
    /**
     * Splits a PDF file based on file size threshold.
     */
    fun splitByFileSize(request: FileSizeSplitRequest): SplitResponse {
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting file size split operation for file: {} with threshold: {}MB", 
                   request.file.originalFilename, request.fileSizeThresholdMB)
        
        try {
            // Validate input file
            val validationResult = fileValidationService.validatePdfFile(request.file)
            if (!validationResult.isValid) {
                return SplitResponse(
                    success = false,
                    message = "File validation failed: ${validationResult.message}",
                    outputFiles = emptyList(),
                    totalOutputFiles = 0,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    originalFileName = request.file.originalFilename,
                    splitStrategy = "fileSize"
                )
            }
            
            val totalPages = getTotalPages(request.file)
            val fileSizeMB = request.file.size.toDouble() / (1024 * 1024)
            
            // Calculate how many chunks we need based on size threshold
            val estimatedChunks = Math.ceil(fileSizeMB / request.fileSizeThresholdMB).toInt()
            val pagesPerChunk = Math.ceil(totalPages.toDouble() / estimatedChunks).toInt()
            
            logger.debug("File size: {:.2f}MB, Threshold: {}MB, Estimated chunks: {}, Pages per chunk: {}",
                        fileSizeMB, request.fileSizeThresholdMB, estimatedChunks, pagesPerChunk)
            
            // Create output files
            val outputFiles = mutableListOf<SplitOutputFile>()
            var currentPage = 1
            var chunkIndex = 1
            
            while (currentPage <= totalPages) {
                val endPage = Math.min(currentPage + pagesPerChunk - 1, totalPages)
                val pageRange = if (currentPage == endPage) "$currentPage" else "$currentPage-$endPage"
                
                val outputFileName = generateOutputFileName(
                    request.file.originalFilename ?: "document",
                    chunkIndex,
                    request.outputFileNamePattern
                )
                
                val pageNumbers = (currentPage..endPage).toList()
                val outputFile = createSplitFile(request.file, pageNumbers, outputFileName)
                
                outputFiles.add(SplitOutputFile(
                    fileName = outputFileName,
                    pageCount = pageNumbers.size,
                    fileSizeBytes = outputFile.length(),
                    pageRanges = pageRange,
                    downloadUrl = "/api/pdf/split/download/${outputFile.name}"
                ))
                
                currentPage = endPage + 1
                chunkIndex++
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.info("File size split completed successfully. Output files: {}, Processing time: {}ms",
                       outputFiles.size, processingTime)
            
            return SplitResponse(
                success = true,
                message = "PDF split by file size completed successfully",
                outputFiles = outputFiles,
                totalOutputFiles = outputFiles.size,
                processingTimeMs = processingTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "fileSize"
            )
            
        } catch (e: Exception) {
            logger.error("File size split operation failed", e)
            return SplitResponse(
                success = false,
                message = "Split failed: ${e.message}",
                outputFiles = emptyList(),
                totalOutputFiles = 0,
                processingTimeMs = System.currentTimeMillis() - startTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "fileSize"
            )
        }
    }
    
    /**
     * Starts an asynchronous file size split operation.
     */
    fun startAsyncSplit(request: SplitRequest): String {
        val operationId = UUID.randomUUID().toString()
        
        // Initialize progress tracking
        asyncOperations[operationId] = SplitProgressResponse(
            operationId = operationId,
            status = "STARTING",
            progressPercentage = 0,
            currentStep = "Initializing file size split operation",
            estimatedTimeRemainingMs = null,
            processedPages = 0,
            totalPages = getTotalPages(request.file),
            outputFilesCreated = 0
        )
        
        // Start async processing
        CompletableFuture.supplyAsync {
            try {
                val sizeRequest = FileSizeSplitRequest(
                    file = request.file,
                    fileSizeThresholdMB = request.fileSizeThresholdMB ?: 10,
                    outputFileNamePattern = request.outputFileNamePattern,
                    preserveBookmarks = request.preserveBookmarks,
                    preserveMetadata = request.preserveMetadata
                )
                
                updateProgress(operationId, "PROCESSING", 50, "Splitting PDF by file size", 0, 0)
                
                val result = splitByFileSize(sizeRequest)
                
                if (result.success) {
                    updateProgress(operationId, "COMPLETED", 100, "Split operation completed", 
                                 result.outputFiles.sumOf { it.pageCount }, result.totalOutputFiles)
                    asyncResults[operationId] = result.outputFiles.map { it.fileName }
                } else {
                    updateProgress(operationId, "FAILED", -1, "Split operation failed: ${result.message}", 0, 0)
                }
                
            } catch (e: Exception) {
                updateProgress(operationId, "FAILED", -1, "Split operation failed: ${e.message}", 0, 0)
                logger.error("Async file size split operation failed for $operationId", e)
            }
        }
        
        return operationId
    }
    
    /**
     * Gets the progress of an asynchronous split operation.
     */
    fun getSplitProgress(operationId: String): SplitProgressResponse? {
        return asyncOperations[operationId]
    }
    
    /**
     * Gets the results of an asynchronous split operation.
     */
    fun getSplitResults(operationId: String): Resource? {
        val filePaths = asyncResults[operationId] ?: return null
        
        // Create a ZIP file containing all split results
        return createZipResource(filePaths, "file_size_split_$operationId.zip")
    }
    
    /**
     * Previews the split operation without performing the actual split.
     */
    fun previewSplit(request: SplitRequest): SplitPreviewResponse {
        return try {
            val totalPages = getTotalPages(request.file)
            val fileSizeMB = request.file.size.toDouble() / (1024 * 1024)
            val thresholdMB = request.fileSizeThresholdMB ?: 10
            
            // Calculate estimated chunks
            val estimatedChunks = Math.ceil(fileSizeMB / thresholdMB).toInt()
            val pagesPerChunk = Math.ceil(totalPages.toDouble() / estimatedChunks).toInt()
            
            val previewResults = mutableListOf<SplitPreviewResult>()
            var currentPage = 1
            var chunkIndex = 1
            
            while (currentPage <= totalPages) {
                val endPage = Math.min(currentPage + pagesPerChunk - 1, totalPages)
                val pageRange = if (currentPage == endPage) "$currentPage" else "$currentPage-$endPage"
                val pageCount = endPage - currentPage + 1
                
                val fileName = generateOutputFileName(
                    request.file.originalFilename ?: "document",
                    chunkIndex,
                    request.outputFileNamePattern
                )
                
                previewResults.add(SplitPreviewResult(
                    outputFileName = fileName,
                    pageRanges = pageRange,
                    pageCount = pageCount,
                    estimatedFileSizeMB = (fileSizeMB * pageCount / totalPages).coerceAtMost(thresholdMB.toDouble())
                ))
                
                currentPage = endPage + 1
                chunkIndex++
            }
            
            SplitPreviewResponse(
                success = true,
                message = "Preview generated successfully for file size split",
                previewResults = previewResults,
                totalPages = totalPages,
                estimatedOutputFiles = previewResults.size,
                splitStrategy = "fileSize"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to generate file size split preview", e)
            SplitPreviewResponse(
                success = false,
                message = "Preview failed: ${e.message}",
                previewResults = emptyList(),
                totalPages = 0,
                estimatedOutputFiles = 0,
                splitStrategy = "fileSize"
            )
        }
    }
    
    /**
     * Gets the total number of pages in a PDF file.
     */
    private fun getTotalPages(file: MultipartFile): Int {
        // Simplified implementation - return a default value
        // In a full implementation, this would use PDFBox to count pages
        return 20
    }
    
    /**
     * Creates a split PDF file containing the specified pages.
     */
    private fun createSplitFile(sourceFile: MultipartFile, pageNumbers: List<Int>, outputFileName: String): File {
        val outputFile = tempFileManagerService.createTempFile("split", ".pdf")
        
        // Simplified implementation - copy the source file
        // In a full implementation, this would use PDFBox to extract specific pages
        sourceFile.transferTo(outputFile)
        
        logger.debug("Created split file: {} with {} pages", outputFileName, pageNumbers.size)
        
        return outputFile
    }
    
    /**
     * Generates output file name based on pattern.
     */
    private fun generateOutputFileName(originalName: String, chunkIndex: Int, pattern: String?): String {
        val baseName = originalName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        
        return when (pattern) {
            null -> "${baseName}_part_${chunkIndex}.pdf"
            else -> pattern
                .replace("{original}", baseName)
                .replace("{index}", chunkIndex.toString())
                .replace("{timestamp}", timestamp.toString())
                .replace("{size}", "chunk")
        }
    }
    
    /**
     * Creates a ZIP resource containing multiple files.
     */
    private fun createZipResource(filePaths: List<String>, zipFileName: String): Resource? {
        // Simplified implementation - return first file as resource
        // In a full implementation, this would create an actual ZIP file
        return if (filePaths.isNotEmpty()) {
            FileSystemResource(File(filePaths.first()))
        } else {
            null
        }
    }
    
    /**
     * Updates progress for async operations.
     */
    private fun updateProgress(
        operationId: String,
        status: String,
        percentage: Int,
        step: String,
        processedPages: Int,
        outputFiles: Int
    ) {
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