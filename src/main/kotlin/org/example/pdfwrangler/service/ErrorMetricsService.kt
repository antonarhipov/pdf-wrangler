package org.example.pdfwrangler.service

import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import kotlin.math.max
import kotlin.math.min

/**
 * Service responsible for collecting and managing error metrics for monitoring purposes.
 * Provides comprehensive error analytics, trend analysis, and monitoring integration.
 */
@Service
class ErrorMetricsService(
    private val errorCategorizationService: ErrorCategorizationService
) {
    private val logger = LoggerFactory.getLogger(ErrorMetricsService::class.java)

    // Metrics storage
    private val errorCounts = ConcurrentHashMap<String, LongAdder>()
    private val errorRates = ConcurrentHashMap<String, ErrorRateTracker>()
    private val operationMetrics = ConcurrentHashMap<String, OperationMetrics>()
    private val errorPatterns = ConcurrentHashMap<String, ErrorPattern>()
    private val timeSeriesData = ConcurrentHashMap<String, MutableList<TimeSeriesPoint>>()
    
    // System-wide metrics
    private val totalErrors = LongAdder()
    private val totalOperations = LongAdder()
    private var startTime = Instant.now()

    /**
     * Error rate tracker for calculating rates over time windows
     */
    data class ErrorRateTracker(
        private val windowSize: Duration = Duration.ofMinutes(5)
    ) {
        private val events = mutableListOf<Instant>()
        
        @Synchronized
        fun recordError() {
            val now = Instant.now()
            events.add(now)
            
            // Clean old events outside the window
            val cutoff = now.minus(windowSize)
            events.removeIf { it.isBefore(cutoff) }
        }
        
        @Synchronized
        fun getErrorRate(): Double {
            val now = Instant.now()
            val cutoff = now.minus(windowSize)
            events.removeIf { it.isBefore(cutoff) }
            
            return events.size.toDouble() / windowSize.toMinutes()
        }
        
        @Synchronized
        fun getErrorCount(): Int = events.size
    }

    /**
     * Operation-specific metrics
     */
    data class OperationMetrics(
        val operationType: String,
        val totalCount: LongAdder = LongAdder(),
        val errorCount: LongAdder = LongAdder(),
        val successCount: LongAdder = LongAdder(),
        var averageExecutionTime: Double = 0.0,
        var maxExecutionTime: Long = 0L,
        var minExecutionTime: Long = Long.MAX_VALUE,
        val lastError: AtomicLong = AtomicLong(0),
        val executionTimes: MutableList<Long> = mutableListOf()
    ) {
        fun getSuccessRate(): Double {
            val total = totalCount.sum()
            return if (total > 0) successCount.sum().toDouble() / total else 0.0
        }
        
        fun getErrorRate(): Double {
            val total = totalCount.sum()
            return if (total > 0) errorCount.sum().toDouble() / total else 0.0
        }

        @Synchronized
        fun updateExecutionTime(executionTime: Long) {
            executionTimes.add(executionTime)
            maxExecutionTime = max(maxExecutionTime, executionTime)
            minExecutionTime = min(minExecutionTime, executionTime)
            averageExecutionTime = executionTimes.average()
            
            // Keep only recent execution times to prevent memory issues
            if (executionTimes.size > 1000) {
                executionTimes.removeAt(0)
            }
        }
    }

    /**
     * Error pattern tracking
     */
    data class ErrorPattern(
        val errorType: String,
        val firstSeen: Instant = Instant.now(),
        var lastSeen: Instant = Instant.now(),
        val occurrenceCount: LongAdder = LongAdder(),
        val affectedOperations: MutableSet<String> = mutableSetOf(),
        var severity: String = "UNKNOWN"
    ) {
        fun updateOccurrence(operation: String, severity: String) {
            lastSeen = Instant.now()
            occurrenceCount.increment()
            affectedOperations.add(operation)
            this.severity = severity
        }
    }

    /**
     * Time series data point
     */
    data class TimeSeriesPoint(
        val timestamp: Instant,
        val value: Double,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * Record an error occurrence
     */
    fun recordError(
        exception: Throwable,
        operationType: String? = null,
        executionTime: Long? = null,
        additionalContext: Map<String, Any> = emptyMap()
    ) {
        val errorType = exception.javaClass.simpleName
        val operationKey = operationType ?: "unknown"
        
        // Update counters
        totalErrors.increment()
        errorCounts.computeIfAbsent(errorType) { LongAdder() }.increment()
        errorRates.computeIfAbsent(errorType) { ErrorRateTracker() }.recordError()
        
        // Update operation metrics
        val opMetrics = operationMetrics.computeIfAbsent(operationKey) { 
            OperationMetrics(operationKey) 
        }
        opMetrics.totalCount.increment()
        opMetrics.errorCount.increment()
        opMetrics.lastError.set(System.currentTimeMillis())
        
        executionTime?.let { opMetrics.updateExecutionTime(it) }
        
        // Update error patterns
        val pattern = errorPatterns.computeIfAbsent(errorType) {
            ErrorPattern(errorType)
        }
        
        val severity = when (exception) {
            is PdfOperationException -> exception.severity.name
            is SecurityException -> exception.severity.name
            else -> "MEDIUM"
        }
        
        pattern.updateOccurrence(operationKey, severity)
        
        // Record time series data
        recordTimeSeriesPoint("error_count", 1.0, mapOf(
            "error_type" to errorType,
            "operation" to operationKey,
            "severity" to severity
        ))
        
        logger.debug("Recorded error metrics for {} in operation {}", errorType, operationKey)
    }

    /**
     * Record a successful operation
     */
    fun recordSuccess(
        operationType: String,
        executionTime: Long? = null,
        additionalContext: Map<String, Any> = emptyMap()
    ) {
        totalOperations.increment()
        
        val opMetrics = operationMetrics.computeIfAbsent(operationType) {
            OperationMetrics(operationType)
        }
        opMetrics.totalCount.increment()
        opMetrics.successCount.increment()
        
        executionTime?.let { opMetrics.updateExecutionTime(it) }
        
        // Record time series data
        recordTimeSeriesPoint("success_count", 1.0, mapOf(
            "operation" to operationType
        ))
    }

    /**
     * Get comprehensive error metrics
     */
    fun getErrorMetrics(): Map<String, Any> {
        val uptime = Duration.between(startTime, Instant.now())
        val totalOps = totalOperations.sum()
        val totalErrs = totalErrors.sum()
        
        return mapOf(
            "overview" to mapOf(
                "total_operations" to totalOps,
                "total_errors" to totalErrs,
                "overall_success_rate" to if (totalOps > 0) (totalOps - totalErrs).toDouble() / totalOps else 0.0,
                "overall_error_rate" to if (totalOps > 0) totalErrs.toDouble() / totalOps else 0.0,
                "uptime_seconds" to uptime.seconds
            ),
            "error_counts_by_type" to errorCounts.mapValues { it.value.sum() },
            "error_rates_by_type" to errorRates.mapValues { it.value.getErrorRate() },
            "operation_metrics" to operationMetrics.mapValues { (_, metrics) ->
                mapOf(
                    "total_count" to metrics.totalCount.sum(),
                    "success_count" to metrics.successCount.sum(),
                    "error_count" to metrics.errorCount.sum(),
                    "success_rate" to metrics.getSuccessRate(),
                    "error_rate" to metrics.getErrorRate(),
                    "avg_execution_time_ms" to metrics.averageExecutionTime,
                    "max_execution_time_ms" to metrics.maxExecutionTime,
                    "min_execution_time_ms" to if (metrics.minExecutionTime == Long.MAX_VALUE) 0 else metrics.minExecutionTime,
                    "last_error_timestamp" to metrics.lastError.get()
                )
            },
            "error_patterns" to errorPatterns.mapValues { (_, pattern) ->
                mapOf(
                    "first_seen" to pattern.firstSeen.epochSecond,
                    "last_seen" to pattern.lastSeen.epochSecond,
                    "occurrence_count" to pattern.occurrenceCount.sum(),
                    "affected_operations" to pattern.affectedOperations.toList(),
                    "severity" to pattern.severity
                )
            }
        )
    }

    /**
     * Get metrics for a specific operation type
     */
    fun getOperationMetrics(operationType: String): Map<String, Any>? {
        return operationMetrics[operationType]?.let { metrics ->
            mapOf(
                "operation_type" to operationType,
                "total_count" to metrics.totalCount.sum(),
                "success_count" to metrics.successCount.sum(),
                "error_count" to metrics.errorCount.sum(),
                "success_rate" to metrics.getSuccessRate(),
                "error_rate" to metrics.getErrorRate(),
                "performance" to mapOf(
                    "avg_execution_time_ms" to metrics.averageExecutionTime,
                    "max_execution_time_ms" to metrics.maxExecutionTime,
                    "min_execution_time_ms" to if (metrics.minExecutionTime == Long.MAX_VALUE) 0 else metrics.minExecutionTime
                ),
                "last_error_timestamp" to metrics.lastError.get()
            )
        }
    }

    /**
     * Get error trend analysis
     */
    fun getErrorTrends(timeWindow: Duration = Duration.ofHours(24)): Map<String, Any> {
        val cutoff = Instant.now().minus(timeWindow)
        
        val trendData = timeSeriesData.mapValues { (_, points) ->
            points.filter { it.timestamp.isAfter(cutoff) }
        }.filterValues { it.isNotEmpty() }
        
        return mapOf(
            "time_window_hours" to timeWindow.toHours(),
            "trends" to trendData.mapValues { (metric, points) ->
                val values = points.map { it.value }
                mapOf(
                    "total_occurrences" to values.sum(),
                    "average_per_hour" to values.sum() / timeWindow.toHours(),
                    "peak_value" to (values.maxOrNull() ?: 0.0),
                    "trend_direction" to calculateTrend(values)
                )
            }
        )
    }

    /**
     * Get health status based on error metrics
     */
    fun getHealthStatus(): Map<String, Any> {
        val metrics = getErrorMetrics()
        val overview = metrics["overview"] as Map<String, Any>
        val errorRate = overview["overall_error_rate"] as Double
        
        val status = when {
            errorRate < 0.01 -> "HEALTHY"
            errorRate < 0.05 -> "WARNING"
            errorRate < 0.10 -> "DEGRADED"
            else -> "CRITICAL"
        }
        
        val criticalPatterns = errorPatterns.values.filter { 
            it.severity == "CRITICAL" && it.occurrenceCount.sum() > 5 
        }
        
        return mapOf(
            "status" to status,
            "overall_error_rate" to errorRate,
            "critical_patterns_count" to criticalPatterns.size,
            "critical_patterns" to criticalPatterns.map { pattern ->
                mapOf(
                    "type" to pattern.errorType,
                    "count" to pattern.occurrenceCount.sum(),
                    "affected_operations" to pattern.affectedOperations.size
                )
            },
            "recommendations" to generateHealthRecommendations(status, errorRate, criticalPatterns)
        )
    }

    /**
     * Export metrics for external monitoring systems
     */
    fun exportMetricsForMonitoring(): Map<String, Any> {
        val metrics = getErrorMetrics()
        
        // Flatten metrics for Prometheus/Grafana style format
        val flatMetrics = mutableMapOf<String, Any>()
        
        // System-wide metrics
        val overview = metrics["overview"] as Map<String, Any>
        flatMetrics["pdf_wrangler_total_operations"] = overview["total_operations"] ?: 0
        flatMetrics["pdf_wrangler_total_errors"] = overview["total_errors"] ?: 0
        flatMetrics["pdf_wrangler_success_rate"] = overview["overall_success_rate"] ?: 0.0
        flatMetrics["pdf_wrangler_uptime_seconds"] = overview["uptime_seconds"] ?: 0
        
        // Per-operation metrics
        val operationMetrics = metrics["operation_metrics"] as Map<String, Map<String, Any>>
        operationMetrics.forEach { (operation, opMetrics) ->
            val sanitizedOp = operation.replace(" ", "_").toLowerCase()
            flatMetrics["pdf_wrangler_operation_total{operation=\"$operation\"}"] = opMetrics["total_count"] ?: 0
            flatMetrics["pdf_wrangler_operation_errors{operation=\"$operation\"}"] = opMetrics["error_count"] ?: 0
            flatMetrics["pdf_wrangler_operation_success_rate{operation=\"$operation\"}"] = opMetrics["success_rate"] ?: 0.0
            flatMetrics["pdf_wrangler_operation_avg_duration_ms{operation=\"$operation\"}"] = opMetrics["avg_execution_time_ms"] ?: 0.0
        }
        
        // Error type metrics
        val errorCounts = metrics["error_counts_by_type"] as Map<String, Long>
        errorCounts.forEach { (errorType, count) ->
            flatMetrics["pdf_wrangler_error_count{type=\"$errorType\"}"] = count
        }
        
        return flatMetrics
    }

    private fun recordTimeSeriesPoint(metric: String, value: Double, metadata: Map<String, Any>) {
        val points = timeSeriesData.computeIfAbsent(metric) { mutableListOf() }
        
        synchronized(points) {
            points.add(TimeSeriesPoint(Instant.now(), value, metadata))
            
            // Keep only last 24 hours of data
            val cutoff = Instant.now().minus(Duration.ofHours(24))
            points.removeIf { it.timestamp.isBefore(cutoff) }
        }
    }

    private fun calculateTrend(values: List<Double>): String {
        if (values.size < 2) return "INSUFFICIENT_DATA"
        
        val firstHalf = values.take(values.size / 2).average()
        val secondHalf = values.drop(values.size / 2).average()
        
        return when {
            secondHalf > firstHalf * 1.1 -> "INCREASING"
            secondHalf < firstHalf * 0.9 -> "DECREASING"
            else -> "STABLE"
        }
    }

    private fun generateHealthRecommendations(
        status: String,
        errorRate: Double,
        criticalPatterns: List<ErrorPattern>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (status) {
            "CRITICAL" -> {
                recommendations.add("Immediate investigation required - error rate exceeds 10%")
                recommendations.add("Check system resources and external dependencies")
                recommendations.add("Consider enabling circuit breakers for failing operations")
            }
            "DEGRADED" -> {
                recommendations.add("Monitor error trends closely - error rate is elevated")
                recommendations.add("Review recent deployments or configuration changes")
            }
            "WARNING" -> {
                recommendations.add("Error rate is slightly elevated - continue monitoring")
            }
        }
        
        if (criticalPatterns.isNotEmpty()) {
            recommendations.add("Critical error patterns detected in: ${criticalPatterns.map { it.errorType }}")
        }
        
        return recommendations
    }

    /**
     * Reset all metrics (useful for testing or system reset)
     */
    fun resetMetrics() {
        errorCounts.clear()
        errorRates.clear()
        operationMetrics.clear()
        errorPatterns.clear()
        timeSeriesData.clear()
        totalErrors.reset()
        totalOperations.reset()
        startTime = Instant.now()
        logger.info("All error metrics have been reset")
    }

    /**
     * Scheduled cleanup of old data
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    fun cleanupOldData() {
        val cutoff = Instant.now().minus(Duration.ofHours(24))
        
        // Clean up time series data
        timeSeriesData.values.forEach { points ->
            synchronized(points) {
                points.removeIf { it.timestamp.isBefore(cutoff) }
            }
        }
        
        logger.debug("Cleaned up old metrics data")
    }
}