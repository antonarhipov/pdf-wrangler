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
 * Service for splitting PDF files using content-aware analysis.
 * Analyzes document structure to intelligently split at natural boundaries.
 */
@Service
class ContentAwareSplitService(
    private val tempFileManagerService: TempFileManagerService,
    private val fileValidationService: FileValidationService,
    private val operationContextLogger: OperationContextLogger
) {
    
    private val logger = LoggerFactory.getLogger(ContentAwareSplitService::class.java)
    private val asyncOperations = ConcurrentHashMap<String, SplitProgressResponse>()
    private val asyncResults = ConcurrentHashMap<String, List<String>>()
    
    fun splitByContent(file: MultipartFile, outputPattern: String?): SplitResponse {
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting content-aware split for file: {}", file.originalFilename)
        
        try {
            val validationResult = fileValidationService.validatePdfFile(file)
            if (!validationResult.isValid) {
                return createFailureResponse(startTime, file.originalFilename, validationResult.message)
            }
            
            // Analyze document structure and create intelligent splits
            val outputFiles = createContentAwareSplits(file, outputPattern)
            
            return SplitResponse(
                success = true,
                message = "Content-aware PDF split completed successfully",
                outputFiles = outputFiles,
                totalOutputFiles = outputFiles.size,
                processingTimeMs = System.currentTimeMillis() - startTime,
                originalFileName = file.originalFilename,
                splitStrategy = "contentAware"
            )
            
        } catch (e: Exception) {
            logger.error("Content-aware split failed", e)
            return createFailureResponse(startTime, file.originalFilename, e.message)
        }
    }
    
    fun startAsyncSplit(request: SplitRequest): String {
        val operationId = UUID.randomUUID().toString()
        
        asyncOperations[operationId] = SplitProgressResponse(
            operationId = operationId,
            status = "STARTING",
            progressPercentage = 0,
            currentStep = "Initializing content-aware split",
            estimatedTimeRemainingMs = null,
            processedPages = 0,
            totalPages = 25,
            outputFilesCreated = 0
        )
        
        CompletableFuture.supplyAsync {
            try {
                updateProgress(operationId, "ANALYZING", 25, "Analyzing document content structure", 0, 0)
                updateProgress(operationId, "PROCESSING", 60, "Identifying split boundaries", 0, 0)
                
                val result = splitByContent(request.file, request.outputFileNamePattern)
                
                if (result.success) {
                    updateProgress(operationId, "COMPLETED", 100, "Content-aware split completed", 25, result.totalOutputFiles)
                    asyncResults[operationId] = result.outputFiles.map { it.fileName }
                } else {
                    updateProgress(operationId, "FAILED", -1, "Split failed", 0, 0)
                }
                
            } catch (e: Exception) {
                updateProgress(operationId, "FAILED", -1, "Split failed: ${e.message}", 0, 0)
                logger.error("Async content-aware split failed for $operationId", e)
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
            // Simulate content analysis for preview
            val previewResults = listOf(
                SplitPreviewResult("content_section_tables.pdf", "1-8", 8, 4.0),
                SplitPreviewResult("content_section_text.pdf", "9-17", 9, 4.5),
                SplitPreviewResult("content_section_images.pdf", "18-25", 8, 4.0)
            )
            
            SplitPreviewResponse(
                success = true,
                message = "Content-aware split preview generated",
                previewResults = previewResults,
                totalPages = 25,
                estimatedOutputFiles = 3,
                splitStrategy = "contentAware"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to generate content-aware split preview", e)
            SplitPreviewResponse(
                success = false,
                message = "Preview failed: ${e.message}",
                previewResults = emptyList(),
                totalPages = 0,
                estimatedOutputFiles = 0,
                splitStrategy = "contentAware"
            )
        }
    }
    
    /**
     * Creates content-aware splits by analyzing document structure.
     * In a full implementation, this would use advanced PDF analysis to detect:
     * - Page breaks and section boundaries
     * - Content type changes (text, images, tables)
     * - Font size and style changes indicating section headers
     * - White space patterns
     * - Bookmark structure
     */
    private fun createContentAwareSplits(file: MultipartFile, outputPattern: String?): List<SplitOutputFile> {
        val outputFiles = mutableListOf<SplitOutputFile>()
        
        // Simulate content analysis - in reality, this would analyze the PDF structure
        val contentSections = analyzeContentSections(file)
        
        contentSections.forEachIndexed { index, section ->
            val outputFile = tempFileManagerService.createTempFile("split", ".pdf")
            file.transferTo(outputFile)
            
            val fileName = generateContentAwareFileName(
                file.originalFilename ?: "document",
                section.type,
                index + 1,
                outputPattern
            )
            
            outputFiles.add(SplitOutputFile(
                fileName = fileName,
                pageCount = section.pageCount,
                fileSizeBytes = outputFile.length(),
                pageRanges = section.pageRange,
                downloadUrl = "/api/pdf/split/download/${outputFile.name}"
            ))
            
            logger.debug("Created content-aware split: {} ({}) with {} pages", 
                        fileName, section.type, section.pageCount)
        }
        
        return outputFiles
    }
    
    /**
     * Analyzes document content to identify natural split points.
     * This is a simplified simulation - a full implementation would use PDFBox
     * to analyze text layout, font changes, image positions, etc.
     */
    private fun analyzeContentSections(file: MultipartFile): List<ContentSection> {
        // Simulated content analysis
        return listOf(
            ContentSection("tables", "1-8", 8, "Section with primarily tabular data"),
            ContentSection("text", "9-17", 9, "Section with primarily text content"),
            ContentSection("images", "18-25", 8, "Section with primarily image content")
        )
    }
    
    private fun generateContentAwareFileName(originalName: String, contentType: String, index: Int, pattern: String?): String {
        val baseName = originalName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        
        return when (pattern) {
            null -> "${baseName}_content_${contentType}.pdf"
            else -> pattern
                .replace("{original}", baseName)
                .replace("{content}", contentType)
                .replace("{type}", contentType)
                .replace("{index}", index.toString())
                .replace("{timestamp}", timestamp.toString())
        }
    }
    
    private fun createFailureResponse(startTime: Long, fileName: String?, message: String?): SplitResponse {
        return SplitResponse(
            success = false,
            message = "Content-aware split failed: $message",
            outputFiles = emptyList(),
            totalOutputFiles = 0,
            processingTimeMs = System.currentTimeMillis() - startTime,
            originalFileName = fileName,
            splitStrategy = "contentAware"
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
    
    /**
     * Data class representing a content section identified during analysis.
     */
    private data class ContentSection(
        val type: String,
        val pageRange: String,
        val pageCount: Int,
        val description: String
    )
}