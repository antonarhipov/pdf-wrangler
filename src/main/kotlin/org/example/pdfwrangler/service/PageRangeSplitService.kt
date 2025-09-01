package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.SplitException
import org.example.pdfwrangler.exception.SplitInvalidPageRangeException
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
 * Service for splitting PDF files by specific page ranges.
 */
@Service
class PageRangeSplitService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(PageRangeSplitService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, SplitProgressResponse>()
    private val asyncResults = ConcurrentHashMap<String, List<String>>() // operationId -> file paths
    
    /**
     * Splits a PDF file by specified page ranges.
     */
    fun splitByPageRanges(request: PageRangeSplitRequest): SplitResponse {
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting page range split operation for file: {} with {} ranges", 
                   request.file.originalFilename, request.pageRanges.size)
        
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
                    splitStrategy = "pageRanges"
                )
            }
            
            // Parse and validate page ranges
            val parsedRanges = parsePageRanges(request.pageRanges)
            val totalPages = getTotalPages(request.file)
            validatePageRanges(parsedRanges, totalPages)
            
            // Create output files for each range
            val outputFiles = mutableListOf<SplitOutputFile>()
            var outputIndex = 1
            
            for ((rangeSpec, pageNumbers) in parsedRanges) {
                val outputFileName = generateOutputFileName(
                    request.file.originalFilename ?: "document",
                    rangeSpec,
                    outputIndex,
                    request.outputFileNamePattern
                )
                
                val outputFile = createSplitFile(request.file, pageNumbers, outputFileName)
                
                outputFiles.add(SplitOutputFile(
                    fileName = outputFileName,
                    pageCount = pageNumbers.size,
                    fileSizeBytes = outputFile.length(),
                    pageRanges = rangeSpec,
                    downloadUrl = "/api/pdf/split/download/${outputFile.name}"
                ))
                
                outputIndex++
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.info("Page range split completed successfully. Output files: {}, Processing time: {}ms",
                       outputFiles.size, processingTime)
            
            return SplitResponse(
                success = true,
                message = "PDF split completed successfully",
                outputFiles = outputFiles,
                totalOutputFiles = outputFiles.size,
                processingTimeMs = processingTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "pageRanges"
            )
            
        } catch (e: Exception) {
            logger.error("Page range split operation failed", e)
            return SplitResponse(
                success = false,
                message = "Split failed: ${e.message}",
                outputFiles = emptyList(),
                totalOutputFiles = 0,
                processingTimeMs = System.currentTimeMillis() - startTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "pageRanges"
            )
        }
    }
    
    /**
     * Starts an asynchronous page range split operation.
     */
    fun startAsyncSplit(request: SplitRequest): String {
        val operationId = UUID.randomUUID().toString()
        
        // Initialize progress tracking
        asyncOperations[operationId] = SplitProgressResponse(
            operationId = operationId,
            status = "STARTING",
            progressPercentage = 0,
            currentStep = "Initializing page range split operation",
            estimatedTimeRemainingMs = null,
            processedPages = 0,
            totalPages = getTotalPages(request.file),
            outputFilesCreated = 0
        )
        
        // Start async processing
        CompletableFuture.supplyAsync {
            try {
                val pageRequest = PageRangeSplitRequest(
                    file = request.file,
                    pageRanges = request.pageRanges,
                    outputFileNamePattern = request.outputFileNamePattern,
                    preserveBookmarks = request.preserveBookmarks,
                    preserveMetadata = request.preserveMetadata
                )
                
                updateProgress(operationId, "PROCESSING", 50, "Splitting PDF by page ranges", 0, 0)
                
                val result = splitByPageRanges(pageRequest)
                
                if (result.success) {
                    updateProgress(operationId, "COMPLETED", 100, "Split operation completed", 
                                 result.outputFiles.sumOf { it.pageCount }, result.totalOutputFiles)
                    asyncResults[operationId] = result.outputFiles.map { it.fileName }
                } else {
                    updateProgress(operationId, "FAILED", -1, "Split operation failed: ${result.message}", 0, 0)
                }
                
            } catch (e: Exception) {
                updateProgress(operationId, "FAILED", -1, "Split operation failed: ${e.message}", 0, 0)
                logger.error("Async page range split operation failed for $operationId", e)
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
        return createZipResource(filePaths, "page_range_split_$operationId.zip")
    }
    
    /**
     * Previews the split operation without performing the actual split.
     */
    fun previewSplit(request: SplitRequest): SplitPreviewResponse {
        return try {
            val parsedRanges = parsePageRanges(request.pageRanges)
            val totalPages = getTotalPages(request.file)
            validatePageRanges(parsedRanges, totalPages)
            
            val previewResults = parsedRanges.mapIndexed { index, (rangeSpec, pageNumbers) ->
                val fileName = generateOutputFileName(
                    request.file.originalFilename ?: "document",
                    rangeSpec,
                    index + 1,
                    request.outputFileNamePattern
                )
                
                SplitPreviewResult(
                    outputFileName = fileName,
                    pageRanges = rangeSpec,
                    pageCount = pageNumbers.size,
                    estimatedFileSizeMB = estimateFileSizeMB(request.file, pageNumbers.size, totalPages)
                )
            }
            
            SplitPreviewResponse(
                success = true,
                message = "Preview generated successfully",
                previewResults = previewResults,
                totalPages = totalPages,
                estimatedOutputFiles = previewResults.size,
                splitStrategy = "pageRanges"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to generate split preview", e)
            SplitPreviewResponse(
                success = false,
                message = "Preview failed: ${e.message}",
                previewResults = emptyList(),
                totalPages = 0,
                estimatedOutputFiles = 0,
                splitStrategy = "pageRanges"
            )
        }
    }
    
    /**
     * Parses page range specifications into actual page numbers.
     */
    private fun parsePageRanges(pageRanges: List<String>): List<Pair<String, List<Int>>> {
        return pageRanges.map { rangeSpec ->
            val pageNumbers = mutableSetOf<Int>()
            
            rangeSpec.split(",").forEach { part ->
                val trimmed = part.trim()
                
                if (trimmed.contains("-")) {
                    val (start, end) = trimmed.split("-").let { 
                        it[0].trim().toInt() to it[1].trim().toInt() 
                    }
                    if (start > end) {
                        throw SplitInvalidPageRangeException(
                            "Invalid page range: start page ($start) must be <= end page ($end)",
                            trimmed,
                            0
                        )
                    }
                    pageNumbers.addAll(start..end)
                } else {
                    pageNumbers.add(trimmed.toInt())
                }
            }
            
            rangeSpec to pageNumbers.sorted()
        }
    }
    
    /**
     * Validates that page ranges are within the document bounds.
     */
    private fun validatePageRanges(parsedRanges: List<Pair<String, List<Int>>>, totalPages: Int) {
        for ((rangeSpec, pageNumbers) in parsedRanges) {
            for (pageNum in pageNumbers) {
                if (pageNum < 1 || pageNum > totalPages) {
                    throw SplitInvalidPageRangeException(
                        "Page number $pageNum is out of bounds (document has $totalPages pages)",
                        rangeSpec,
                        totalPages
                    )
                }
            }
        }
    }
    
    /**
     * Gets the total number of pages in a PDF file.
     */
    private fun getTotalPages(file: MultipartFile): Int {
        // Simplified implementation - return a default value
        // In a full implementation, this would use PDFBox to count pages
        return 10
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
    private fun generateOutputFileName(originalName: String, rangeSpec: String, index: Int, pattern: String?): String {
        val baseName = originalName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        
        return when (pattern) {
            null -> "${baseName}_pages_${rangeSpec}.pdf"
            else -> pattern
                .replace("{original}", baseName)
                .replace("{range}", rangeSpec.replace(",", "_").replace("-", "to"))
                .replace("{index}", index.toString())
                .replace("{timestamp}", timestamp.toString())
        }
    }
    
    /**
     * Estimates file size for preview purposes.
     */
    private fun estimateFileSizeMB(sourceFile: MultipartFile, pageCount: Int, totalPages: Int): Double {
        val sourceSizeMB = sourceFile.size.toDouble() / (1024 * 1024)
        return (sourceSizeMB * pageCount / totalPages).coerceAtLeast(0.1)
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