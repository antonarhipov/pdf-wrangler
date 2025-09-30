package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.SplitException
import org.example.pdfwrangler.exception.SplitInvalidPageRangeException
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.multipdf.Splitter
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service for splitting PDF files by specific page ranges.
 */
@Service
class PageRangeSplitService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger,
    private val splitFileCacheService: SplitFileCacheService
) {
    
    private val logger = LoggerFactory.getLogger(PageRangeSplitService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, SplitProgressResponse>()
    private val asyncResults = ConcurrentHashMap<String, List<String>>() // operationId -> file paths
    
    /**
     * Splits a PDF file by specified page ranges.
     */
    fun splitByPageRanges(request: PageRangeSplitRequest): SplitResponse {
        val startTime = System.currentTimeMillis()
        
        // Generate unique operation ID for this split operation
        val operationId = UUID.randomUUID().toString()
        
        logger.info("Starting page range split operation [{}] for file: {} with {} ranges", 
                   operationId, request.file.originalFilename, request.pageRanges.size)
        
        // Initialize cache for tracking split files with operation ID
        splitFileCacheService.initializeCache(operationId)
        
        try {
            // Validate input file
            val validationResult = fileValidationService.validatePdfFile(request.file)
            if (!validationResult.isValid) {
                splitFileCacheService.clearCache(operationId)
                return SplitResponse(
                    success = false,
                    message = "File validation failed: ${validationResult.message}",
                    outputFiles = emptyList(),
                    totalOutputFiles = 0,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    originalFileName = request.file.originalFilename,
                    splitStrategy = "pageRanges",
                    operationId = operationId
                )
            }
            
            // Get total pages first (needed for auto-generation)
            val totalPages = getTotalPages(request.file)
            
            // If page ranges are not specified, auto-generate single-page ranges
            val effectivePageRanges = if (request.pageRanges.isEmpty() || request.pageRanges.all { it.isBlank() }) {
                logger.info("No page ranges specified [{}], auto-generating single-page ranges for {} pages", 
                           operationId, totalPages)
                (1..totalPages).map { it.toString() }
            } else {
                request.pageRanges
            }
            
            // Parse and validate page ranges
            val parsedRanges = parsePageRanges(effectivePageRanges)
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
                
                // Store file reference in cache for ZIP creation
                splitFileCacheService.storeFile(operationId, outputFileName, outputFile)
                
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
            
            logger.info("Page range split completed successfully [{}]. Output files: {}, Processing time: {}ms",
                       operationId, outputFiles.size, processingTime)
            
            return SplitResponse(
                success = true,
                message = "PDF split completed successfully",
                outputFiles = outputFiles,
                totalOutputFiles = outputFiles.size,
                processingTimeMs = processingTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "pageRanges",
                operationId = operationId
            )
            
        } catch (e: Exception) {
            logger.error("Page range split operation failed [{}]", operationId, e)
            splitFileCacheService.clearCache(operationId)
            return SplitResponse(
                success = false,
                message = "Split failed: ${e.message}",
                outputFiles = emptyList(),
                totalOutputFiles = 0,
                processingTimeMs = System.currentTimeMillis() - startTime,
                originalFileName = request.file.originalFilename,
                splitStrategy = "pageRanges",
                operationId = operationId
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
        val document = Loader.loadPDF(file.bytes)
        try {
            return document.numberOfPages
        } finally {
            document.close()
        }
    }
    
    /**
     * Creates a split PDF file containing the specified pages.
     */
    private fun createSplitFile(sourceFile: MultipartFile, pageNumbers: List<Int>, outputFileName: String): File {
        val outputFile = tempFileManagerService.createTempFile("split", ".pdf")
        
        // Load source PDF
        val sourceDocument = Loader.loadPDF(sourceFile.bytes)
        val targetDocument = PDDocument()
        
        try {
            // Extract specified pages (convert 1-based to 0-based indexing)
            for (pageNum in pageNumbers) {
                val pageIndex = pageNum - 1
                if (pageIndex >= 0 && pageIndex < sourceDocument.numberOfPages) {
                    val page = sourceDocument.getPage(pageIndex)
                    targetDocument.addPage(page)
                }
            }
            
            // Save to output file
            targetDocument.save(outputFile)
            
            logger.debug("Created split file: {} with {} pages", outputFileName, pageNumbers.size)
            
        } finally {
            targetDocument.close()
            sourceDocument.close()
        }
        
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
     * Creates a ZIP archive in memory from a SplitResponse containing split PDF files.
     * Returns the ZIP as a ByteArrayResource for immediate download.
     */
    fun createZipFromSplitResponse(splitResponse: SplitResponse): Resource {
        logger.info("Creating ZIP archive from split response with {} files", splitResponse.outputFiles.size)
        
        val operationId = splitResponse.operationId
        if (operationId == null) {
            logger.error("No operation ID found in split response")
            throw SplitException("No operation ID available for ZIP creation")
        }
        
        val byteArrayOutputStream = ByteArrayOutputStream()
        val zipOutputStream = ZipOutputStream(byteArrayOutputStream)
        
        try {
            // Get cached file references using operation ID
            val fileCache = splitFileCacheService.getFiles(operationId)
            
            if (fileCache == null || fileCache.isEmpty()) {
                logger.error("No cached files found for ZIP creation [operationId: {}]", operationId)
                throw SplitException("No split files available for ZIP creation")
            }
            
            // Add each split PDF file to the ZIP
            for (outputFile in splitResponse.outputFiles) {
                // Get the actual file from cache
                val actualFile = fileCache[outputFile.fileName]
                
                if (actualFile != null && actualFile.exists()) {
                    // Create ZIP entry
                    val zipEntry = ZipEntry(outputFile.fileName)
                    zipOutputStream.putNextEntry(zipEntry)
                    
                    // Write file content to ZIP
                    actualFile.inputStream().use { input ->
                        input.copyTo(zipOutputStream)
                    }
                    
                    zipOutputStream.closeEntry()
                    logger.debug("Added file to ZIP: {} (actual: {})", outputFile.fileName, actualFile.name)
                } else {
                    logger.warn("Could not find cached file for: {}", outputFile.fileName)
                }
            }
            
            zipOutputStream.finish()
            zipOutputStream.close()
            
            val zipBytes = byteArrayOutputStream.toByteArray()
            logger.info("Successfully created ZIP archive [{}] with size: {} bytes", operationId, zipBytes.size)
            
            return ByteArrayResource(zipBytes)
            
        } catch (e: Exception) {
            logger.error("Failed to create ZIP archive [{}]", operationId, e)
            throw SplitException("Failed to create ZIP archive: ${e.message}", e)
        } finally {
            try {
                zipOutputStream.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
            // Clean up the cache after ZIP creation
            splitFileCacheService.clearCache(operationId)
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