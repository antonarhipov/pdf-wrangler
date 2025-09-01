package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class VisualEnhancementAuditService {
    
    private val logger = LoggerFactory.getLogger(VisualEnhancementAuditService::class.java)
    private val auditLog = ConcurrentLinkedQueue<AuditTrailEntry>()
    private val operationStats = ConcurrentHashMap<String, OperationStatistics>()
    
    data class OperationStatistics(
        val operationType: String,
        var totalCount: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var totalProcessingTimeMs: Long = 0,
        var averageProcessingTimeMs: Double = 0.0,
        var lastExecuted: LocalDateTime? = null,
        val filesProcessed: MutableMap<String, Long> = mutableMapOf()
    )
    
    fun logOperation(
        operationType: String,
        fileName: String,
        enhancementType: String,
        parameters: Map<String, Any>,
        result: String,
        duration: Long,
        userId: String? = null,
        sessionId: String? = null,
        ipAddress: String? = null,
        fileSize: Long? = null,
        outputSize: Long? = null
    ) {
        val timestamp = LocalDateTime.now()
        val auditEntry = AuditTrailEntry(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            operationType = operationType,
            fileName = fileName,
            enhancementType = enhancementType,
            parameters = parameters,
            result = result,
            duration = duration,
            userId = userId,
            sessionId = sessionId,
            ipAddress = ipAddress,
            fileSize = fileSize,
            outputSize = outputSize
        )
        
        auditLog.offer(auditEntry)
        updateOperationStatistics(operationType, result == "SUCCESS", duration, fileName)
        
        logger.info("Audit log entry created: {} - {} - {} - {} - {}ms", 
                   operationType, enhancementType, fileName, result, duration)
        
        // Keep only the last 10000 entries to prevent memory issues
        while (auditLog.size > 10000) {
            auditLog.poll()
        }
    }
    
    fun logBatchOperation(
        operationId: String,
        operationType: String,
        totalFiles: Int,
        successCount: Int,
        failedCount: Int,
        processingTimeMs: Long
    ) {
        val parameters = mapOf(
            "operation_id" to operationId,
            "total_files" to totalFiles,
            "success_count" to successCount,
            "failed_count" to failedCount
        )
        
        logOperation(
            operationType = "BATCH_OPERATION",
            fileName = "batch_${operationType}_${totalFiles}_files",
            enhancementType = operationType,
            parameters = parameters,
            result = if (failedCount == 0) "SUCCESS" else if (successCount > 0) "PARTIAL_SUCCESS" else "FAILURE",
            duration = processingTimeMs
        )
    }
    
    private fun updateOperationStatistics(
        operationType: String, 
        success: Boolean, 
        duration: Long, 
        fileName: String
    ) {
        val stats = operationStats.getOrPut(operationType) { 
            OperationStatistics(operationType) 
        }
        
        synchronized(stats) {
            stats.totalCount++
            if (success) {
                stats.successCount++
            } else {
                stats.failureCount++
            }
            stats.totalProcessingTimeMs += duration
            stats.averageProcessingTimeMs = stats.totalProcessingTimeMs.toDouble() / stats.totalCount
            stats.lastExecuted = LocalDateTime.now()
            
            // Track files processed
            val fileExtension = fileName.substringAfterLast('.', "unknown")
            stats.filesProcessed[fileExtension] = stats.filesProcessed.getOrDefault(fileExtension, 0) + 1
        }
    }
    
    fun getAuditTrail(
        limit: Int = 50,
        operationType: String? = null,
        enhancementType: String? = null,
        userId: String? = null,
        sinceTimestamp: String? = null
    ): List<AuditTrailEntry> {
        var filteredEntries = auditLog.toList()
        
        // Apply filters
        operationType?.let { type ->
            filteredEntries = filteredEntries.filter { it.operationType.equals(type, ignoreCase = true) }
        }
        
        enhancementType?.let { type ->
            filteredEntries = filteredEntries.filter { it.enhancementType.equals(type, ignoreCase = true) }
        }
        
        userId?.let { id ->
            filteredEntries = filteredEntries.filter { it.userId == id }
        }
        
        sinceTimestamp?.let { timestamp ->
            try {
                val sinceDateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                filteredEntries = filteredEntries.filter { 
                    LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).isAfter(sinceDateTime)
                }
            } catch (e: Exception) {
                logger.warn("Invalid timestamp format for audit trail filter: $timestamp", e)
            }
        }
        
        // Sort by timestamp descending (most recent first) and limit
        return filteredEntries
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    fun getOperationStatistics(operationType: String? = null): Map<String, Any> {
        return if (operationType != null) {
            val stats = operationStats[operationType]
            if (stats != null) {
                mapOf(
                    "operation_type" to stats.operationType,
                    "total_operations" to stats.totalCount,
                    "success_count" to stats.successCount,
                    "failure_count" to stats.failureCount,
                    "success_rate" to if (stats.totalCount > 0) (stats.successCount.toDouble() / stats.totalCount * 100) else 0.0,
                    "average_processing_time_ms" to stats.averageProcessingTimeMs,
                    "total_processing_time_ms" to stats.totalProcessingTimeMs,
                    "last_executed" to (stats.lastExecuted?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "never"),
                    "files_by_extension" to stats.filesProcessed.toMap()
                )
            } else {
                emptyMap()
            }
        } else {
            // Return summary statistics for all operations
            val totalOperations = operationStats.values.sumOf { it.totalCount }
            val totalSuccess = operationStats.values.sumOf { it.successCount }
            val totalFailures = operationStats.values.sumOf { it.failureCount }
            val totalProcessingTime = operationStats.values.sumOf { it.totalProcessingTimeMs }
            
            mapOf(
                "total_operations" to totalOperations,
                "total_success" to totalSuccess,
                "total_failures" to totalFailures,
                "overall_success_rate" to if (totalOperations > 0) (totalSuccess.toDouble() / totalOperations * 100) else 0.0,
                "total_processing_time_ms" to totalProcessingTime,
                "average_processing_time_ms" to if (totalOperations > 0) (totalProcessingTime.toDouble() / totalOperations) else 0.0,
                "operations_by_type" to operationStats.keys.toList(),
                "detailed_stats" to operationStats.mapValues { (_, stats) ->
                    mapOf(
                        "count" to stats.totalCount,
                        "success_rate" to if (stats.totalCount > 0) (stats.successCount.toDouble() / stats.totalCount * 100) else 0.0,
                        "avg_time_ms" to stats.averageProcessingTimeMs
                    )
                }
            )
        }
    }
    
    fun getRecentActivity(hours: Int = 24): List<AuditTrailEntry> {
        val cutoffTime = LocalDateTime.now().minusHours(hours.toLong())
        
        return auditLog.toList().filter { entry ->
            try {
                val entryTime = LocalDateTime.parse(entry.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                entryTime.isAfter(cutoffTime)
            } catch (e: Exception) {
                logger.warn("Invalid timestamp in audit entry: ${entry.timestamp}", e)
                false
            }
        }.sortedByDescending { it.timestamp }
    }
    
    fun getTopFailedOperations(limit: Int = 10): List<Map<String, Any>> {
        return operationStats.values
            .filter { it.failureCount > 0 }
            .sortedByDescending { it.failureCount }
            .take(limit)
            .map { stats ->
                mapOf(
                    "operation_type" to stats.operationType,
                    "failure_count" to stats.failureCount,
                    "total_count" to stats.totalCount,
                    "failure_rate" to (stats.failureCount.toDouble() / stats.totalCount * 100),
                    "last_executed" to (stats.lastExecuted?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "never")
                )
            }
    }
    
    fun getUserActivity(userId: String, limit: Int = 50): List<AuditTrailEntry> {
        return auditLog.toList()
            .filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    fun getSessionActivity(sessionId: String, limit: Int = 50): List<AuditTrailEntry> {
        return auditLog.toList()
            .filter { it.sessionId == sessionId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    fun exportAuditLog(
        format: String = "JSON",
        operationType: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): String {
        var entries = auditLog.toList()
        
        // Apply date filters
        startDate?.let { start ->
            try {
                val startDateTime = LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                entries = entries.filter { 
                    LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).isAfter(startDateTime)
                }
            } catch (e: Exception) {
                logger.warn("Invalid start date format: $start", e)
            }
        }
        
        endDate?.let { end ->
            try {
                val endDateTime = LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                entries = entries.filter { 
                    LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).isBefore(endDateTime)
                }
            } catch (e: Exception) {
                logger.warn("Invalid end date format: $end", e)
            }
        }
        
        operationType?.let { type ->
            entries = entries.filter { it.operationType.equals(type, ignoreCase = true) }
        }
        
        // For now, return JSON format (could be extended to support CSV, XML, etc.)
        return entries.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",\n"
        ) { entry ->
            """
            {
                "id": "${entry.id}",
                "timestamp": "${entry.timestamp}",
                "operationType": "${entry.operationType}",
                "fileName": "${entry.fileName}",
                "enhancementType": "${entry.enhancementType}",
                "result": "${entry.result}",
                "duration": ${entry.duration},
                "userId": ${if (entry.userId != null) "\"${entry.userId}\"" else "null"},
                "sessionId": ${if (entry.sessionId != null) "\"${entry.sessionId}\"" else "null"},
                "fileSize": ${entry.fileSize ?: "null"},
                "outputSize": ${entry.outputSize ?: "null"}
            }
            """.trimIndent()
        }
    }
    
    fun clearOldEntries(olderThanDays: Int = 30) {
        val cutoffTime = LocalDateTime.now().minusDays(olderThanDays.toLong())
        val sizeBefore = auditLog.size
        
        auditLog.removeAll { entry ->
            try {
                val entryTime = LocalDateTime.parse(entry.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                entryTime.isBefore(cutoffTime)
            } catch (e: Exception) {
                logger.warn("Invalid timestamp in audit entry during cleanup: ${entry.timestamp}", e)
                true // Remove entries with invalid timestamps
            }
        }
        
        val removedCount = sizeBefore - auditLog.size
        if (removedCount > 0) {
            logger.info("Cleared $removedCount old audit entries (older than $olderThanDays days)")
        }
    }
    
    fun getAuditSummary(): Map<String, Any> {
        val totalEntries = auditLog.size
        val recentEntries = getRecentActivity(24).size
        val successfulOperations = auditLog.count { it.result == "SUCCESS" }
        val failedOperations = auditLog.count { it.result == "FAILURE" }
        
        return mapOf(
            "total_audit_entries" to totalEntries,
            "recent_entries_24h" to recentEntries,
            "successful_operations" to successfulOperations,
            "failed_operations" to failedOperations,
            "success_rate" to if (totalEntries > 0) (successfulOperations.toDouble() / totalEntries * 100) else 0.0,
            "operation_types" to operationStats.keys.toList(),
            "oldest_entry" to (auditLog.minByOrNull { it.timestamp }?.timestamp ?: "none"),
            "newest_entry" to (auditLog.maxByOrNull { it.timestamp }?.timestamp ?: "none")
        )
    }
}