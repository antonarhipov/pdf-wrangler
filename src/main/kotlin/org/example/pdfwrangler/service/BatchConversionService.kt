package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.example.pdfwrangler.exception.PdfOperationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Service for coordinating batch conversion operations across all conversion services.
 * Provides unified batch processing capabilities for multiple document types and formats.
 */
@Service
class BatchConversionService(
    private val pdfToImageService: PdfToImageService,
    private val imageToPdfService: ImageToPdfService,
    private val officeDocumentConverter: OfficeDocumentConverter,
    private val libreOfficeIntegrationService: LibreOfficeIntegrationService,
    private val rtfAndTextConverter: RtfAndTextConverter,
    private val operationContextLogger: OperationContextLogger
) {

    private val logger = LoggerFactory.getLogger(BatchConversionService::class.java)
    private val batchOperations = ConcurrentHashMap<String, BatchOperationStatus>()
    private val executorService = Executors.newFixedThreadPool(5) // Configurable thread pool

    companion object {
        const val MAX_BATCH_SIZE = 100
        const val MAX_CONCURRENT_JOBS = 5
        const val BATCH_TIMEOUT_MINUTES = 30L
        
        val CONVERSION_TYPE_MAPPING = mapOf(
            "PDF_TO_IMAGE" to "PdfToImageService",
            "IMAGE_TO_PDF" to "ImageToPdfService",
            "OFFICE_TO_PDF" to "OfficeDocumentConverter",
            "PDF_TO_OFFICE" to "OfficeDocumentConverter",
            "LIBREOFFICE_CONVERSION" to "LibreOfficeIntegrationService",
            "TEXT_CONVERSION" to "RtfAndTextConverter"
        )
    }

    /**
     * Process batch conversion request with all conversion types.
     */
    fun processBatchConversion(request: BatchConversionRequest): BatchConversionResponse {
        logger.info("Starting unified batch conversion of ${request.conversionJobs.size} jobs")
        val startTime = System.currentTimeMillis()
        val operationId = operationContextLogger.logOperationStart("BATCH_CONVERSION", request.conversionJobs.size)
        
        return try {
            validateBatchRequest(request)
            
            val batchId = UUID.randomUUID().toString()
            val batchStatus = createBatchStatus(batchId, request)
            batchOperations[batchId] = batchStatus
            
            // Group jobs by conversion type for optimization
            val groupedJobs = groupJobsByType(request.conversionJobs)
            val results = mutableListOf<ConversionJobResult>()
            var completedJobs = 0
            var failedJobs = 0
            
            // Process each group
            groupedJobs.forEach { (conversionType, jobs) ->
                try {
                    val groupResults = processConversionGroup(conversionType, jobs, batchId)
                    results.addAll(groupResults.results)
                    completedJobs += groupResults.completedJobs
                    failedJobs += groupResults.failedJobs
                    
                    updateBatchProgress(batchId, completedJobs, request.conversionJobs.size)
                    
                } catch (ex: Exception) {
                    logger.error("Error processing conversion group $conversionType: ${ex.message}", ex)
                    // Add failed results for this group
                    jobs.forEach { job ->
                        results.add(ConversionJobResult(
                            success = false,
                            message = "Group processing failed: ${ex.message}",
                            outputFileName = null,
                            conversionType = job.conversionType,
                            processingTimeMs = 0,
                            fileSize = null
                        ))
                        failedJobs++
                    }
                }
            }
            
            // Finalize batch operation
            val totalTime = System.currentTimeMillis() - startTime
            completeBatchOperation(batchId, completedJobs, failedJobs)
            operationContextLogger.logOperationSuccess("BATCH_CONVERSION", totalTime, operationId)
            
            BatchConversionResponse(
                success = failedJobs == 0,
                message = "Unified batch conversion completed: $completedJobs successful, $failedJobs failed",
                completedJobs = completedJobs,
                failedJobs = failedJobs,
                totalProcessingTimeMs = totalTime,
                results = results
            )
            
        } catch (ex: Exception) {
            logger.error("Error in batch conversion: ${ex.message}", ex)
            operationContextLogger.logOperationError(ex, operationId)
            throw PdfOperationException("Batch conversion failed: ${ex.message}")
        }
    }

    /**
     * Start asynchronous batch conversion.
     */
    fun startAsyncBatchConversion(request: BatchConversionRequest): String {
        val batchId = UUID.randomUUID().toString()
        logger.info("Starting async batch conversion with ID: $batchId")
        
        val future = CompletableFuture.supplyAsync({
            processBatchConversion(request)
        }, executorService)
        
        val batchStatus = createBatchStatus(batchId, request)
        batchStatus.future = future
        batchOperations[batchId] = batchStatus
        
        return batchId
    }

    /**
     * Get batch conversion progress.
     */
    fun getBatchProgress(batchId: String): Map<String, Any> {
        val batchStatus = batchOperations[batchId]
            ?: throw PdfOperationException("Batch operation not found: $batchId")
        
        return mapOf(
            "batchId" to batchId,
            "status" to batchStatus.status,
            "totalJobs" to batchStatus.totalJobs,
            "completedJobs" to batchStatus.completedJobs,
            "failedJobs" to batchStatus.failedJobs,
            "progressPercentage" to if (batchStatus.totalJobs > 0) {
                (batchStatus.completedJobs * 100) / batchStatus.totalJobs
            } else 0,
            "startTime" to batchStatus.startTime,
            "estimatedTimeRemainingMs" to estimateRemainingTime(batchStatus),
            "currentStep" to batchStatus.currentStep
        )
    }

    /**
     * Get batch conversion result.
     */
    fun getBatchResult(batchId: String): BatchConversionResponse {
        val batchStatus = batchOperations[batchId]
            ?: throw PdfOperationException("Batch operation not found: $batchId")
        
        val future = batchStatus.future
            ?: throw PdfOperationException("Batch operation was not started asynchronously")
        
        return if (future.isDone) {
            try {
                val result = future.get()
                batchOperations.remove(batchId) // Cleanup completed operation
                result
            } catch (ex: Exception) {
                logger.error("Error getting batch result: ${ex.message}", ex)
                throw PdfOperationException("Failed to get batch result: ${ex.message}")
            }
        } else {
            throw PdfOperationException("Batch conversion not yet completed")
        }
    }

    /**
     * Cancel batch conversion.
     */
    fun cancelBatchConversion(batchId: String): Map<String, Any> {
        val batchStatus = batchOperations[batchId]
            ?: throw PdfOperationException("Batch operation not found: $batchId")
        
        try {
            batchStatus.future?.cancel(true)
            batchStatus.status = "CANCELLED"
            
            return mapOf(
                "success" to true,
                "message" to "Batch conversion cancelled successfully",
                "batchId" to batchId,
                "completedJobs" to batchStatus.completedJobs,
                "cancelledJobs" to (batchStatus.totalJobs - batchStatus.completedJobs - batchStatus.failedJobs)
            )
        } catch (ex: Exception) {
            logger.error("Error cancelling batch: ${ex.message}", ex)
            return mapOf(
                "success" to false,
                "message" to "Failed to cancel batch conversion: ${ex.message}",
                "batchId" to batchId
            )
        }
    }

    /**
     * Get batch conversion statistics.
     */
    fun getBatchStatistics(): Map<String, Any> {
        val activeBatches = batchOperations.values
        val completedBatches = activeBatches.filter { it.status == "COMPLETED" }
        val failedBatches = activeBatches.filter { it.status == "FAILED" }
        val inProgressBatches = activeBatches.filter { it.status in listOf("RUNNING", "PROCESSING") }
        
        return mapOf(
            "totalBatches" to activeBatches.size,
            "completedBatches" to completedBatches.size,
            "failedBatches" to failedBatches.size,
            "inProgressBatches" to inProgressBatches.size,
            "totalJobsProcessed" to activeBatches.sumOf { it.completedJobs },
            "totalJobsFailed" to activeBatches.sumOf { it.failedJobs },
            "averageJobsPerBatch" to if (activeBatches.isNotEmpty()) {
                activeBatches.map { it.totalJobs }.average()
            } else 0.0,
            "conversionTypeDistribution" to getConversionTypeDistribution(activeBatches),
            "performanceMetrics" to getPerformanceMetrics(activeBatches)
        )
    }

    /**
     * Optimize batch processing configuration.
     */
    fun optimizeBatchProcessing(request: BatchConversionRequest): Map<String, Any> {
        val analysis = analyzeBatchRequest(request)
        
        return mapOf(
            "recommendedBatchSize" to calculateOptimalBatchSize(request),
            "estimatedProcessingTime" to estimateTotalProcessingTime(request),
            "recommendedConcurrency" to calculateOptimalConcurrency(request),
            "memoryEstimate" to estimateMemoryUsage(request),
            "conversionTypeAnalysis" to analysis,
            "recommendations" to getBatchOptimizationRecommendations(request, analysis),
            "riskFactors" to identifyRiskFactors(request)
        )
    }

    // Private helper methods

    private fun validateBatchRequest(request: BatchConversionRequest) {
        if (request.conversionJobs.isEmpty()) {
            throw PdfOperationException("Batch request must contain at least one conversion job")
        }
        
        if (request.conversionJobs.size > MAX_BATCH_SIZE) {
            throw PdfOperationException("Batch size cannot exceed $MAX_BATCH_SIZE jobs")
        }
        
        // Validate each job
        request.conversionJobs.forEachIndexed { index, job ->
            if (job.conversionType !in CONVERSION_TYPE_MAPPING.keys) {
                throw PdfOperationException("Unsupported conversion type at job $index: ${job.conversionType}")
            }
            
            if (job.file.isEmpty) {
                throw PdfOperationException("Empty file at job $index")
            }
        }
    }

    private fun groupJobsByType(jobs: List<ConversionJobRequest>): Map<String, List<ConversionJobRequest>> {
        return jobs.groupBy { it.conversionType }
    }

    private fun processConversionGroup(
        conversionType: String,
        jobs: List<ConversionJobRequest>,
        batchId: String
    ): BatchConversionResponse {
        logger.info("Processing $conversionType group with ${jobs.size} jobs")
        updateBatchStep(batchId, "Processing $conversionType conversions")
        
        val batchRequest = BatchConversionRequest(jobs)
        
        return when (conversionType) {
            "PDF_TO_IMAGE" -> pdfToImageService.batchConvertPdfs(batchRequest)
            "IMAGE_TO_PDF" -> imageToPdfService.batchConvertImages(batchRequest)
            "OFFICE_TO_PDF", "PDF_TO_OFFICE" -> officeDocumentConverter.batchConvertDocuments(batchRequest)
            "LIBREOFFICE_CONVERSION" -> libreOfficeIntegrationService.batchConvertWithLibreOffice(batchRequest)
            "TEXT_CONVERSION" -> rtfAndTextConverter.batchConvertTextDocuments(batchRequest)
            else -> throw PdfOperationException("Unsupported conversion type: $conversionType")
        }
    }

    private fun createBatchStatus(batchId: String, request: BatchConversionRequest): BatchOperationStatus {
        return BatchOperationStatus(
            batchId = batchId,
            status = "STARTED",
            totalJobs = request.conversionJobs.size,
            completedJobs = 0,
            failedJobs = 0,
            startTime = System.currentTimeMillis(),
            currentStep = "Initializing batch processing",
            conversionTypes = request.conversionJobs.map { it.conversionType }.distinct(),
            future = null
        )
    }

    private fun updateBatchProgress(batchId: String, completedJobs: Int, totalJobs: Int) {
        val batchStatus = batchOperations[batchId]
        if (batchStatus != null) {
            batchStatus.completedJobs = completedJobs
            batchStatus.status = if (completedJobs < totalJobs) "PROCESSING" else "COMPLETING"
        }
    }

    private fun updateBatchStep(batchId: String, step: String) {
        val batchStatus = batchOperations[batchId]
        if (batchStatus != null) {
            batchStatus.currentStep = step
        }
    }

    private fun completeBatchOperation(batchId: String, completedJobs: Int, failedJobs: Int) {
        val batchStatus = batchOperations[batchId]
        if (batchStatus != null) {
            batchStatus.completedJobs = completedJobs
            batchStatus.failedJobs = failedJobs
            batchStatus.status = if (failedJobs == 0) "COMPLETED" else "COMPLETED_WITH_ERRORS"
            batchStatus.currentStep = "Batch processing completed"
        }
    }

    private fun estimateRemainingTime(batchStatus: BatchOperationStatus): Long {
        if (batchStatus.completedJobs == 0) return -1
        
        val elapsedTime = System.currentTimeMillis() - batchStatus.startTime
        val averageTimePerJob = elapsedTime / batchStatus.completedJobs
        val remainingJobs = batchStatus.totalJobs - batchStatus.completedJobs - batchStatus.failedJobs
        
        return averageTimePerJob * remainingJobs
    }

    private fun analyzeBatchRequest(request: BatchConversionRequest): Map<String, Any> {
        val jobsByType = groupJobsByType(request.conversionJobs)
        val fileSizes = request.conversionJobs.map { it.file.size }
        
        return mapOf(
            "totalJobs" to request.conversionJobs.size,
            "conversionTypes" to jobsByType.keys.toList(),
            "typeDistribution" to jobsByType.mapValues { it.value.size },
            "fileSizeStats" to mapOf(
                "totalSize" to fileSizes.sum(),
                "averageSize" to fileSizes.average(),
                "minSize" to fileSizes.minOrNull(),
                "maxSize" to fileSizes.maxOrNull()
            ),
            "estimatedComplexity" to calculateBatchComplexity(request)
        )
    }

    private fun calculateOptimalBatchSize(request: BatchConversionRequest): Int {
        val totalSize = request.conversionJobs.sumOf { it.file.size }
        val averageSize = totalSize / request.conversionJobs.size
        
        return when {
            averageSize > 50 * 1024 * 1024 -> min(10, request.conversionJobs.size) // Large files: smaller batches
            averageSize > 10 * 1024 * 1024 -> min(25, request.conversionJobs.size) // Medium files
            else -> min(50, request.conversionJobs.size) // Small files: larger batches
        }
    }

    private fun estimateTotalProcessingTime(request: BatchConversionRequest): Long {
        val baseTimePerJob = mapOf(
            "PDF_TO_IMAGE" to 3000L,
            "IMAGE_TO_PDF" to 2000L,
            "OFFICE_TO_PDF" to 5000L,
            "PDF_TO_OFFICE" to 7000L,
            "LIBREOFFICE_CONVERSION" to 6000L,
            "TEXT_CONVERSION" to 1000L
        )
        
        val totalTime = request.conversionJobs.sumOf { job ->
            val baseTime = baseTimePerJob[job.conversionType] ?: 3000L
            val sizeMultiplier = (job.file.size / (1024 * 1024)).coerceAtLeast(1)
            baseTime * sizeMultiplier
        }
        
        // Account for parallelization
        return totalTime / MAX_CONCURRENT_JOBS
    }

    private fun calculateOptimalConcurrency(request: BatchConversionRequest): Int {
        val memoryIntensiveTypes = setOf("PDF_TO_IMAGE", "OFFICE_TO_PDF", "LIBREOFFICE_CONVERSION")
        val hasMemoryIntensiveJobs = request.conversionJobs.any { it.conversionType in memoryIntensiveTypes }
        
        return if (hasMemoryIntensiveJobs) {
            min(3, MAX_CONCURRENT_JOBS)
        } else {
            MAX_CONCURRENT_JOBS
        }
    }

    private fun estimateMemoryUsage(request: BatchConversionRequest): Map<String, Any> {
        val memoryPerType = mapOf(
            "PDF_TO_IMAGE" to 50L,
            "IMAGE_TO_PDF" to 30L,
            "OFFICE_TO_PDF" to 100L,
            "PDF_TO_OFFICE" to 120L,
            "LIBREOFFICE_CONVERSION" to 80L,
            "TEXT_CONVERSION" to 10L
        )
        
        val totalMemoryMB = request.conversionJobs.sumOf { job ->
            val baseMemory = memoryPerType[job.conversionType] ?: 50L
            val sizeMultiplier = (job.file.size / (1024 * 1024)).coerceAtLeast(1)
            baseMemory + (sizeMultiplier * 5)
        }
        
        return mapOf(
            "estimatedPeakMemoryMB" to totalMemoryMB / MAX_CONCURRENT_JOBS,
            "estimatedAverageMemoryMB" to totalMemoryMB / (MAX_CONCURRENT_JOBS * 2),
            "recommendedHeapSize" to "${(totalMemoryMB / MAX_CONCURRENT_JOBS * 1.5).toInt()}MB"
        )
    }

    private fun calculateBatchComplexity(request: BatchConversionRequest): String {
        val typeCount = request.conversionJobs.map { it.conversionType }.distinct().size
        val avgFileSize = request.conversionJobs.map { it.file.size }.average()
        
        return when {
            typeCount > 3 || avgFileSize > 50 * 1024 * 1024 -> "HIGH"
            typeCount > 1 || avgFileSize > 10 * 1024 * 1024 -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun getBatchOptimizationRecommendations(
        request: BatchConversionRequest,
        analysis: Map<String, Any>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        val complexity = analysis["estimatedComplexity"] as String
        if (complexity == "HIGH") {
            recommendations.add("Consider breaking large batch into smaller chunks")
        }
        
        val typeDistribution = analysis["typeDistribution"] as Map<*, *>
        if (typeDistribution.size > 3) {
            recommendations.add("Multiple conversion types detected - processing will be grouped for efficiency")
        }
        
        val fileSizeStats = analysis["fileSizeStats"] as Map<*, *>
        val maxSize = fileSizeStats["maxSize"] as Long
        if (maxSize > 100 * 1024 * 1024) {
            recommendations.add("Large files detected - increase memory allocation for optimal performance")
        }
        
        return recommendations.ifEmpty { listOf("Batch configuration looks optimal") }
    }

    private fun identifyRiskFactors(request: BatchConversionRequest): List<String> {
        val riskFactors = mutableListOf<String>()
        
        if (request.conversionJobs.size > 50) {
            riskFactors.add("Large batch size may cause memory pressure")
        }
        
        val largeFiles = request.conversionJobs.count { it.file.size > 50 * 1024 * 1024 }
        if (largeFiles > 5) {
            riskFactors.add("Multiple large files may cause processing delays")
        }
        
        val memoryIntensiveJobs = request.conversionJobs.count { 
            it.conversionType in setOf("PDF_TO_IMAGE", "OFFICE_TO_PDF", "LIBREOFFICE_CONVERSION") 
        }
        if (memoryIntensiveJobs > 20) {
            riskFactors.add("High number of memory-intensive conversions")
        }
        
        return riskFactors
    }

    private fun getConversionTypeDistribution(batches: Collection<BatchOperationStatus>): Map<String, Int> {
        val distribution = mutableMapOf<String, Int>()
        batches.forEach { batch ->
            batch.conversionTypes.forEach { type ->
                distribution[type] = distribution.getOrDefault(type, 0) + 1
            }
        }
        return distribution
    }

    private fun getPerformanceMetrics(batches: Collection<BatchOperationStatus>): Map<String, Any> {
        val completedBatches = batches.filter { it.status == "COMPLETED" }
        
        if (completedBatches.isEmpty()) {
            return mapOf("message" to "No completed batches available for metrics")
        }
        
        val processingTimes = completedBatches.map { 
            System.currentTimeMillis() - it.startTime 
        }
        
        return mapOf(
            "averageProcessingTimeMs" to processingTimes.average(),
            "minProcessingTimeMs" to (processingTimes.minOrNull() ?: 0L),
            "maxProcessingTimeMs" to (processingTimes.maxOrNull() ?: 0L),
            "successRate" to completedBatches.map { 
                it.completedJobs.toDouble() / it.totalJobs.toDouble() 
            }.average()
        )
    }

    // Data classes for batch processing
    private data class BatchOperationStatus(
        val batchId: String,
        var status: String,
        val totalJobs: Int,
        var completedJobs: Int,
        var failedJobs: Int,
        val startTime: Long,
        var currentStep: String,
        val conversionTypes: List<String>,
        var future: CompletableFuture<BatchConversionResponse>?
    )

    // Cleanup old batch operations periodically
    fun cleanupOldBatchOperations() {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        val toRemove = batchOperations.filterValues { 
            it.startTime < cutoffTime && it.status in listOf("COMPLETED", "FAILED", "CANCELLED")
        }.keys
        
        toRemove.forEach { batchOperations.remove(it) }
        logger.info("Cleaned up ${toRemove.size} old batch operations")
    }
}