package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Service
class VisualEnhancementBatchService(
    private val imageOverlayService: ImageOverlayService,
    private val stampApplicationService: StampApplicationService,
    private val colorManipulationService: ColorManipulationService,
    private val formFlatteningService: FormFlatteningService,
    private val visualEnhancementAuditService: VisualEnhancementAuditService
) {
    
    private val logger = LoggerFactory.getLogger(VisualEnhancementBatchService::class.java)
    private val executor = Executors.newFixedThreadPool(4)
    private val activeOperations = ConcurrentHashMap<String, BatchOperation>()
    
    data class BatchOperation(
        val operationId: String,
        val totalJobs: Int,
        val processedJobs: AtomicInteger = AtomicInteger(0),
        val failedJobs: AtomicInteger = AtomicInteger(0),
        val startTime: LocalDateTime = LocalDateTime.now(),
        var endTime: LocalDateTime? = null,
        val status: AtomicInteger = AtomicInteger(0), // 0=running, 1=completed, 2=failed, 3=cancelled
        val results: MutableList<VisualEnhancementResponse> = mutableListOf(),
        val errorMessages: MutableList<String> = mutableListOf(),
        var currentStep: String = "Starting batch operation",
        var estimatedTimeRemainingMs: Long? = null
    )
    
    fun startBatchProcessing(request: BatchVisualEnhancementRequest): String {
        val operationId = UUID.randomUUID().toString()
        
        logger.info("Starting batch visual enhancement operation: $operationId with ${request.enhancementJobs.size} jobs")
        
        val operation = BatchOperation(
            operationId = operationId,
            totalJobs = request.enhancementJobs.size
        )
        
        activeOperations[operationId] = operation
        
        // Process jobs asynchronously
        CompletableFuture.runAsync({
            try {
                processBatchJobs(request, operation)
            } catch (e: Exception) {
                logger.error("Batch operation failed: $operationId", e)
                operation.status.set(2)
                operation.errorMessages.add("Batch operation failed: ${e.message}")
                operation.endTime = LocalDateTime.now()
                operation.currentStep = "Failed"
            }
        }, executor)
        
        return operationId
    }
    
    private fun processBatchJobs(request: BatchVisualEnhancementRequest, operation: BatchOperation) {
        val startTime = System.currentTimeMillis()
        
        for ((jobIndex, job) in request.enhancementJobs.withIndex()) {
            if (operation.status.get() == 3) { // Cancelled
                logger.info("Batch operation cancelled: ${operation.operationId}")
                operation.currentStep = "Cancelled"
                break
            }
            
            try {
                operation.currentStep = "Processing job ${jobIndex + 1}/${operation.totalJobs}"
                val jobStartTime = System.currentTimeMillis()
                
                val result = processEnhancementJob(job)
                val processingTime = System.currentTimeMillis() - jobStartTime
                
                operation.results.add(result)
                
                if (result.success) {
                    operation.processedJobs.incrementAndGet()
                } else {
                    operation.failedJobs.incrementAndGet()
                    operation.errorMessages.add("Job ${jobIndex + 1}: ${result.message}")
                }
                
                // Update estimated time remaining
                val avgTimePerJob = (System.currentTimeMillis() - startTime).toDouble() / (jobIndex + 1)
                val remainingJobs = operation.totalJobs - (jobIndex + 1)
                operation.estimatedTimeRemainingMs = (avgTimePerJob * remainingJobs).toLong()
                
                logger.debug("Processed job ${jobIndex + 1}/${operation.totalJobs} in ${processingTime}ms")
                
            } catch (e: Exception) {
                logger.error("Error processing job ${jobIndex + 1}", e)
                operation.failedJobs.incrementAndGet()
                operation.errorMessages.add("Job ${jobIndex + 1}: ${e.message}")
                
                val errorResult = VisualEnhancementResponse(
                    success = false,
                    message = "Processing failed: ${e.message}",
                    outputFileName = null,
                    totalPages = null,
                    processedFiles = 0,
                    processingTimeMs = 0,
                    fileSize = null,
                    enhancementType = job.enhancementType,
                    appliedToPages = null,
                    qualityMetrics = null
                )
                operation.results.add(errorResult)
            }
        }
        
        // Mark operation as completed
        operation.status.set(if (operation.failedJobs.get() == 0) 1 else 2)
        operation.endTime = LocalDateTime.now()
        operation.currentStep = if (operation.failedJobs.get() == 0) "Completed successfully" else "Completed with errors"
        operation.estimatedTimeRemainingMs = 0
        
        val totalTime = System.currentTimeMillis() - startTime
        logger.info("Batch operation completed: ${operation.operationId}, " +
                   "Total: ${operation.totalJobs}, " +
                   "Success: ${operation.processedJobs.get()}, " +
                   "Failed: ${operation.failedJobs.get()}, " +
                   "Time: ${totalTime}ms")
        
        // Log to audit service
        visualEnhancementAuditService.logBatchOperation(
            operationId = operation.operationId,
            operationType = "BATCH_VISUAL_ENHANCEMENT",
            totalFiles = operation.totalJobs,
            successCount = operation.processedJobs.get(),
            failedCount = operation.failedJobs.get(),
            processingTimeMs = totalTime
        )
    }
    
    private fun processEnhancementJob(job: VisualEnhancementJobRequest): VisualEnhancementResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            when (job.enhancementType.lowercase()) {
                "imageoverlay" -> {
                    if (job.overlayImage == null) {
                        return VisualEnhancementResponse(
                            success = false,
                            message = "Overlay image is required for image overlay operation",
                            outputFileName = null,
                            totalPages = null,
                            processedFiles = 0,
                            processingTimeMs = System.currentTimeMillis() - startTime,
                            fileSize = null,
                            enhancementType = job.enhancementType,
                            appliedToPages = null
                        )
                    }
                    
                    val config = ImageOverlayService.ImageOverlayConfig(
                        overlayImage = job.overlayImage,
                        scale = job.overlayScale,
                        opacity = job.overlayOpacity,
                        position = job.position,
                        customX = job.customX,
                        customY = job.customY
                    )
                    val result = imageOverlayService.applyImageOverlay(job.files, config, job.pageNumbers)
                    
                    VisualEnhancementResponse(
                        success = result.success,
                        message = result.message,
                        outputFileName = result.outputFileName,
                        totalPages = result.totalPages,
                        processedFiles = result.processedFiles,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        fileSize = result.fileSize,
                        enhancementType = job.enhancementType,
                        appliedToPages = if (job.applyToAllPages) null else job.pageNumbers
                    )
                }
                
                "stamp" -> {
                    val config = StampApplicationService.StampConfig(
                        stampType = job.stampType ?: "custom",
                        stampText = job.stampText,
                        stampImage = job.stampImage,
                        stampSize = job.stampSize,
                        position = job.position,
                        opacity = job.overlayOpacity // Reuse opacity field for stamp
                    )
                    val result = stampApplicationService.applyStamp(job.files, config, job.pageNumbers)
                    
                    VisualEnhancementResponse(
                        success = result.success,
                        message = result.message,
                        outputFileName = result.outputFileName,
                        totalPages = result.totalPages,
                        processedFiles = result.processedFiles,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        fileSize = result.fileSize,
                        enhancementType = job.enhancementType,
                        appliedToPages = if (job.applyToAllPages) null else job.pageNumbers
                    )
                }
                
                "colormanipulation" -> {
                    val config = ColorManipulationService.ColorConfig(
                        operation = job.colorOperation,
                        intensity = job.colorIntensity,
                        quality = job.qualityLevel
                    )
                    val result = colorManipulationService.applyColorManipulation(job.files, config, job.pageNumbers)
                    
                    VisualEnhancementResponse(
                        success = result.success,
                        message = result.message,
                        outputFileName = result.outputFileName,
                        totalPages = result.totalPages,
                        processedFiles = result.processedFiles,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        fileSize = result.fileSize,
                        enhancementType = job.enhancementType,
                        appliedToPages = if (job.applyToAllPages) null else job.pageNumbers
                    )
                }
                
                "formflattening" -> {
                    val config = FormFlatteningService.FlattenConfig(
                        preserveFormData = job.preserveFormData
                    )
                    val result = formFlatteningService.flattenForms(job.files, config)
                    
                    VisualEnhancementResponse(
                        success = result.success,
                        message = result.message,
                        outputFileName = result.outputFileName,
                        totalPages = result.totalPages,
                        processedFiles = result.processedFiles,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        fileSize = result.fileSize,
                        enhancementType = job.enhancementType,
                        appliedToPages = null // Form flattening applies to entire document
                    )
                }
                
                else -> {
                    VisualEnhancementResponse(
                        success = false,
                        message = "Unsupported enhancement type: ${job.enhancementType}",
                        outputFileName = null,
                        totalPages = null,
                        processedFiles = 0,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        fileSize = null,
                        enhancementType = job.enhancementType,
                        appliedToPages = null
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing enhancement job", e)
            VisualEnhancementResponse(
                success = false,
                message = "Enhancement failed: ${e.message}",
                outputFileName = null,
                totalPages = null,
                processedFiles = 0,
                processingTimeMs = System.currentTimeMillis() - startTime,
                fileSize = null,
                enhancementType = job.enhancementType,
                appliedToPages = null
            )
        }
    }
    
    fun getBatchProgress(operationId: String): VisualEnhancementProgressResponse {
        val operation = activeOperations[operationId]
            ?: return VisualEnhancementProgressResponse(
                operationId = operationId,
                status = "not_found",
                progressPercentage = 0,
                currentStep = "Operation not found",
                estimatedTimeRemainingMs = null,
                processedFiles = 0,
                totalFiles = 0,
                currentPage = null,
                totalPages = null,
                enhancementType = "unknown"
            )
        
        val status = when (operation.status.get()) {
            0 -> "running"
            1 -> "completed"
            2 -> "failed"
            3 -> "cancelled"
            else -> "unknown"
        }
        
        val progressPercentage = if (operation.totalJobs > 0) {
            ((operation.processedJobs.get() + operation.failedJobs.get()).toDouble() / operation.totalJobs * 100).toInt()
        } else {
            0
        }
        
        return VisualEnhancementProgressResponse(
            operationId = operationId,
            status = status,
            progressPercentage = progressPercentage,
            currentStep = operation.currentStep,
            estimatedTimeRemainingMs = operation.estimatedTimeRemainingMs,
            processedFiles = operation.processedJobs.get() + operation.failedJobs.get(),
            totalFiles = operation.totalJobs,
            currentPage = null, // Not applicable for batch operations
            totalPages = null,  // Not applicable for batch operations
            enhancementType = "batch"
        )
    }
    
    fun cancelBatchOperation(operationId: String): Boolean {
        val operation = activeOperations[operationId] ?: return false
        
        operation.status.set(3) // Set to cancelled
        operation.endTime = LocalDateTime.now()
        operation.currentStep = "Cancelled by user"
        operation.estimatedTimeRemainingMs = 0
        
        logger.info("Batch operation cancelled: $operationId")
        return true
    }
    
    fun getActiveOperations(): List<String> {
        return activeOperations.keys.toList()
    }
    
    fun cleanupCompletedOperations(olderThanHours: Long = 24) {
        val cutoffTime = LocalDateTime.now().minusHours(olderThanHours)
        
        val toRemove = activeOperations.entries.filter { (_, operation) ->
            operation.status.get() != 0 && // Not running
            operation.endTime?.isBefore(cutoffTime) == true
        }.map { it.key }
        
        toRemove.forEach { operationId ->
            activeOperations.remove(operationId)
            logger.debug("Cleaned up completed operation: $operationId")
        }
        
        if (toRemove.isNotEmpty()) {
            logger.info("Cleaned up ${toRemove.size} completed batch operations")
        }
    }
    
    fun getBatchStatistics(): Map<String, Any> {
        val runningCount = activeOperations.values.count { it.status.get() == 0 }
        val completedCount = activeOperations.values.count { it.status.get() == 1 }
        val failedCount = activeOperations.values.count { it.status.get() == 2 }
        val cancelledCount = activeOperations.values.count { it.status.get() == 3 }
        
        return mapOf(
            "total_operations" to activeOperations.size,
            "running" to runningCount,
            "completed" to completedCount,
            "failed" to failedCount,
            "cancelled" to cancelledCount,
            "total_jobs_processed" to activeOperations.values.sumOf { it.processedJobs.get() },
            "total_jobs_failed" to activeOperations.values.sumOf { it.failedJobs.get() }
        )
    }
}