package org.example.pdfwrangler.service

import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import jakarta.servlet.http.HttpServletRequest

/**
 * Service for detailed error logging with comprehensive operation context.
 * Provides structured logging with contextual information for better debugging and monitoring.
 */
@Service
class OperationContextLogger(
    private val errorCategorizationService: ErrorCategorizationService,
    private val securityAuditLogger: SecurityAuditLogger
) {

    private val logger = LoggerFactory.getLogger(OperationContextLogger::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    companion object {
        private const val LOG_PREFIX = "[OPERATION_ERROR]"
        private const val MAX_CONTEXT_SIZE = 100
        private const val MAX_STACK_TRACE_ELEMENTS = 15
        
        // MDC keys for structured logging
        private const val MDC_OPERATION_ID = "operation.id"
        private const val MDC_OPERATION_TYPE = "operation.type"
        private const val MDC_ERROR_CATEGORY = "error.category"
        private const val MDC_ERROR_SEVERITY = "error.severity"
        private const val MDC_CLIENT_IP = "client.ip"
        private const val MDC_USER_AGENT = "client.userAgent"
        private const val MDC_REQUEST_URI = "request.uri"
        private const val MDC_HTTP_METHOD = "request.method"
        private const val MDC_SESSION_ID = "session.id"
        private const val MDC_CORRELATION_ID = "correlation.id"
    }

    data class OperationContext(
        val operationId: String,
        val operationType: String,
        val startTime: Instant,
        val endTime: Instant? = null,
        val duration: Long? = null,
        val status: OperationStatus = OperationStatus.IN_PROGRESS,
        val clientInfo: ClientInfo? = null,
        val requestInfo: RequestInfo? = null,
        val resourceUsage: ResourceUsage? = null,
        val customContext: Map<String, Any> = emptyMap()
    ) {
        fun withEndTime(endTime: Instant): OperationContext {
            return copy(
                endTime = endTime,
                duration = java.time.Duration.between(startTime, endTime).toMillis(),
                status = if (status == OperationStatus.IN_PROGRESS) OperationStatus.COMPLETED else status
            )
        }

        fun withError(): OperationContext {
            return copy(status = OperationStatus.FAILED)
        }
    }

    data class ClientInfo(
        val ipAddress: String?,
        val userAgent: String?,
        val sessionId: String?,
        val userId: String? = null,
        val correlationId: String? = null
    )

    data class RequestInfo(
        val uri: String?,
        val method: String?,
        val headers: Map<String, String> = emptyMap(),
        val parameters: Map<String, String> = emptyMap(),
        val contentLength: Long? = null
    )

    data class ResourceUsage(
        val memoryUsedMB: Long? = null,
        val peakMemoryMB: Long? = null,
        val diskUsedMB: Long? = null,
        val cpuTimeMs: Long? = null,
        val fileCount: Int? = null,
        val totalFileSize: Long? = null
    )

    enum class OperationStatus {
        IN_PROGRESS, COMPLETED, FAILED, CANCELLED, TIMEOUT
    }

    data class DetailedErrorLog(
        val timestamp: String,
        val operationContext: OperationContext,
        val exception: ExceptionDetails,
        val classification: ErrorCategorizationService.ErrorClassification,
        val environment: EnvironmentInfo,
        val recommendations: Map<String, Any>
    )

    data class ExceptionDetails(
        val type: String,
        val message: String?,
        val errorCode: String?,
        val stackTrace: List<String>,
        val cause: ExceptionDetails? = null,
        val suppressedExceptions: List<ExceptionDetails> = emptyList(),
        val customAttributes: Map<String, Any> = emptyMap()
    )

    data class EnvironmentInfo(
        val javaVersion: String,
        val osName: String,
        val osVersion: String,
        val applicationVersion: String? = null,
        val activeProfiles: List<String> = emptyList(),
        val availableMemoryMB: Long,
        val totalMemoryMB: Long,
        val freeMemoryMB: Long,
        val availableProcessors: Int
    )

    private val operationContexts = Collections.synchronizedMap(mutableMapOf<String, OperationContext>())

    /**
     * Starts operation context tracking
     */
    fun startOperationContext(
        operationType: String,
        customContext: Map<String, Any> = emptyMap()
    ): String {
        val operationId = generateOperationId()
        val clientInfo = extractClientInfo()
        val requestInfo = extractRequestInfo()
        
        val context = OperationContext(
            operationId = operationId,
            operationType = operationType,
            startTime = Instant.now(),
            clientInfo = clientInfo,
            requestInfo = requestInfo,
            customContext = customContext
        )
        
        operationContexts[operationId] = context
        
        // Set MDC for subsequent logging
        setMDCContext(context)
        
        logger.info("Operation started: {} [{}]", operationType, operationId)
        return operationId
    }

    /**
     * Ends operation context tracking successfully
     */
    fun endOperationContext(operationId: String, resourceUsage: ResourceUsage? = null) {
        operationContexts[operationId]?.let { context ->
            val updatedContext = context
                .withEndTime(Instant.now())
                .copy(resourceUsage = resourceUsage)
            
            operationContexts[operationId] = updatedContext
            
            logger.info("Operation completed: {} [{}] in {}ms", 
                context.operationType, operationId, updatedContext.duration)
            
            clearMDCContext()
        }
    }

    /**
     * Logs detailed error with comprehensive operation context
     */
    /**
     * Logs the start of an operation.
     */
    fun logOperationStart(operationType: String, resourceCount: Int): String {
        val operationId = startOperationContext(operationType, mapOf("resourceCount" to resourceCount))
        logger.info("Started operation: {} with ID: {} for {} resources", operationType, operationId, resourceCount)
        return operationId
    }
    
    /**
     * Logs successful completion of an operation.
     */
    fun logOperationSuccess(operationType: String, durationMs: Long, operationId: String? = null) {
        val actualOperationId = operationId ?: generateOperationId()
        logger.info("Operation {} completed successfully in {}ms with ID: {}", operationType, durationMs, actualOperationId)
        
        if (operationId != null) {
            endOperationContext(actualOperationId)
        }
    }

    fun logOperationError(
        exception: Throwable,
        operationId: String? = null,
        additionalContext: Map<String, Any> = emptyMap()
    ) {
        try {
            val context = operationId?.let { operationContexts[it] }
                ?: createImplicitOperationContext(exception, additionalContext)
            
            val updatedContext = context.withError()
            operationId?.let { operationContexts[it] = updatedContext }
            
            val classification = errorCategorizationService.categorizeException(exception, additionalContext)
            val exceptionDetails = buildExceptionDetails(exception)
            val environmentInfo = buildEnvironmentInfo()
            val recommendations = errorCategorizationService.getErrorHandlingRecommendations(classification)
            
            val detailedLog = DetailedErrorLog(
                timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
                operationContext = updatedContext,
                exception = exceptionDetails,
                classification = classification,
                environment = environmentInfo,
                recommendations = recommendations
            )
            
            setMDCContextForError(updatedContext, classification, exception)
            logDetailedError(detailedLog, classification.severity)
            
            // Log to security audit if it's a security-related error
            if (exception is SecurityException) {
                securityAuditLogger.logSecurityException(exception, additionalContext)
            }
            
            clearMDCContext()
            
        } catch (e: Exception) {
            logger.error("Error during operation error logging", e)
            // Fallback to basic error logging
            logger.error("Original error that failed to log with context", exception)
        }
    }

    /**
     * Logs operation performance metrics
     */
    fun logPerformanceMetrics(
        operationId: String,
        metrics: Map<String, Any>
    ) {
        operationContexts[operationId]?.let { context ->
            val performanceLog = mapOf(
                "operationId" to operationId,
                "operationType" to context.operationType,
                "duration" to context.duration,
                "metrics" to metrics,
                "timestamp" to Instant.now().toString()
            )
            
            try {
                val logMessage = "$LOG_PREFIX Performance: ${objectMapper.writeValueAsString(performanceLog)}"
                logger.info(logMessage)
            } catch (e: Exception) {
                logger.warn("Failed to serialize performance metrics for operation: {}", operationId, e)
            }
        }
    }

    /**
     * Gets current operation statistics
     */
    fun getOperationStatistics(): Map<String, Any> {
        val now = Instant.now()
        val contexts = operationContexts.values.toList()
        
        return mapOf(
            "totalOperations" to contexts.size,
            "inProgressOperations" to contexts.count { it.status == OperationStatus.IN_PROGRESS },
            "failedOperations" to contexts.count { it.status == OperationStatus.FAILED },
            "completedOperations" to contexts.count { it.status == OperationStatus.COMPLETED },
            "averageDurationMs" to (contexts.filter { it.duration != null }
                .map { it.duration!! }
                .takeIf { it.isNotEmpty() }
                ?.average()?.toLong() ?: 0L),
            "operationTypes" to contexts.groupingBy { it.operationType }.eachCount() as Map<String, Int>,
            "oldestOperationAge" to (contexts.minOfOrNull { 
                java.time.Duration.between(it.startTime, now).toMinutes() 
            } ?: 0) as Long
        )
    }

    /**
     * Cleans up old operation contexts
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // Every 10 minutes
    fun cleanupOldContexts() {
        val cutoffTime = Instant.now().minusSeconds(3600) // 1 hour ago
        val keysToRemove = operationContexts.entries
            .filter { it.value.startTime.isBefore(cutoffTime) }
            .map { it.key }
        
        keysToRemove.forEach { operationContexts.remove(it) }
        
        if (keysToRemove.isNotEmpty()) {
            logger.debug("Cleaned up {} old operation contexts", keysToRemove.size)
        }
    }

    private fun createImplicitOperationContext(
        exception: Throwable,
        additionalContext: Map<String, Any>
    ): OperationContext {
        val operationType = when (exception) {
            is PdfOperationException -> exception.operationType.name
            is SecurityException -> "SECURITY_CHECK"
            else -> "UNKNOWN"
        }
        
        return OperationContext(
            operationId = generateOperationId(),
            operationType = operationType,
            startTime = Instant.now().minusSeconds(1), // Assume started 1 second ago
            clientInfo = extractClientInfo(),
            requestInfo = extractRequestInfo(),
            customContext = additionalContext
        )
    }

    private fun buildExceptionDetails(exception: Throwable): ExceptionDetails {
        val customAttributes = when (exception) {
            is PdfOperationException -> exception.operationContext + mapOf(
                "operationId" to exception.operationId,
                "severity" to exception.severity.name,
                "recoverable" to exception.recoverable,
                "retryable" to exception.retryable
            )
            is SecurityException -> exception.additionalContext + mapOf(
                "severity" to exception.severity.name,
                "errorCode" to exception.errorCode
            )
            else -> emptyMap()
        }

        return ExceptionDetails(
            type = exception.javaClass.name,
            message = exception.message,
            errorCode = when (exception) {
                is PdfOperationException -> exception.errorCode
                is SecurityException -> exception.errorCode
                else -> null
            },
            stackTrace = exception.stackTrace
                .take(MAX_STACK_TRACE_ELEMENTS)
                .map { it.toString() },
            cause = exception.cause?.let { buildExceptionDetails(it) },
            suppressedExceptions = exception.suppressed.map { buildExceptionDetails(it) },
            customAttributes = customAttributes
        )
    }

    private fun buildEnvironmentInfo(): EnvironmentInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        
        return EnvironmentInfo(
            javaVersion = System.getProperty("java.version", "unknown"),
            osName = System.getProperty("os.name", "unknown"),
            osVersion = System.getProperty("os.version", "unknown"),
            availableMemoryMB = maxMemory - (totalMemory - freeMemory),
            totalMemoryMB = totalMemory,
            freeMemoryMB = freeMemory,
            availableProcessors = runtime.availableProcessors()
        )
    }

    private fun setMDCContext(context: OperationContext) {
        MDC.put(MDC_OPERATION_ID, context.operationId)
        MDC.put(MDC_OPERATION_TYPE, context.operationType)
        context.clientInfo?.let { client ->
            client.ipAddress?.let { MDC.put(MDC_CLIENT_IP, it) }
            client.userAgent?.let { MDC.put(MDC_USER_AGENT, it) }
            client.sessionId?.let { MDC.put(MDC_SESSION_ID, it) }
            client.correlationId?.let { MDC.put(MDC_CORRELATION_ID, it) }
        }
        context.requestInfo?.let { request ->
            request.uri?.let { MDC.put(MDC_REQUEST_URI, it) }
            request.method?.let { MDC.put(MDC_HTTP_METHOD, it) }
        }
    }

    private fun setMDCContextForError(
        context: OperationContext,
        classification: ErrorCategorizationService.ErrorClassification,
        exception: Throwable
    ) {
        setMDCContext(context)
        MDC.put(MDC_ERROR_CATEGORY, classification.category.name)
        MDC.put(MDC_ERROR_SEVERITY, classification.severity.name)
    }

    private fun clearMDCContext() {
        MDC.remove(MDC_OPERATION_ID)
        MDC.remove(MDC_OPERATION_TYPE)
        MDC.remove(MDC_ERROR_CATEGORY)
        MDC.remove(MDC_ERROR_SEVERITY)
        MDC.remove(MDC_CLIENT_IP)
        MDC.remove(MDC_USER_AGENT)
        MDC.remove(MDC_REQUEST_URI)
        MDC.remove(MDC_HTTP_METHOD)
        MDC.remove(MDC_SESSION_ID)
        MDC.remove(MDC_CORRELATION_ID)
    }

    private fun logDetailedError(
        detailedLog: DetailedErrorLog,
        severity: ErrorCategorizationService.ErrorSeverity
    ) {
        try {
            val logMessage = "$LOG_PREFIX ${objectMapper.writeValueAsString(detailedLog)}"
            
            when (severity) {
                ErrorCategorizationService.ErrorSeverity.CRITICAL -> logger.error(logMessage)
                ErrorCategorizationService.ErrorSeverity.HIGH -> logger.error(logMessage)
                ErrorCategorizationService.ErrorSeverity.MEDIUM -> logger.warn(logMessage)
                ErrorCategorizationService.ErrorSeverity.LOW -> logger.info(logMessage)
                ErrorCategorizationService.ErrorSeverity.INFO -> logger.info(logMessage)
            }
        } catch (e: Exception) {
            logger.error("Failed to serialize detailed error log", e)
            logger.error("Original error: {} - {}", 
                detailedLog.exception.type, detailedLog.exception.message)
        }
    }

    private fun extractClientInfo(): ClientInfo? {
        return try {
            val request = getCurrentRequest()
            request?.let {
                ClientInfo(
                    ipAddress = extractClientIp(it),
                    userAgent = it.getHeader("User-Agent"),
                    sessionId = it.session?.id,
                    correlationId = it.getHeader("X-Correlation-ID") ?: generateCorrelationId()
                )
            }
        } catch (e: Exception) {
            logger.debug("Could not extract client info", e)
            null
        }
    }

    private fun extractRequestInfo(): RequestInfo? {
        return try {
            val request = getCurrentRequest()
            request?.let {
                RequestInfo(
                    uri = it.requestURI,
                    method = it.method,
                    headers = it.headerNames.asSequence()
                        .associateWith { headerName -> it.getHeader(headerName) }
                        .filterValues { it != null },
                    parameters = it.parameterMap
                        .mapValues { entry -> entry.value.joinToString(",") },
                    contentLength = if (it.contentLength >= 0) it.contentLength.toLong() else null
                )
            }
        } catch (e: Exception) {
            logger.debug("Could not extract request info", e)
            null
        }
    }

    private fun getCurrentRequest(): HttpServletRequest? {
        return try {
            val requestAttributes = RequestContextHolder.getRequestAttributes()
            (requestAttributes as? ServletRequestAttributes)?.request
        } catch (e: Exception) {
            null
        }
    }

    private fun extractClientIp(request: HttpServletRequest): String? {
        val headers = listOf("X-Forwarded-For", "X-Real-IP", "X-Originating-IP", "X-Client-IP")
        
        for (header in headers) {
            val ip = request.getHeader(header)
            if (!ip.isNullOrBlank() && ip != "unknown") {
                return ip.split(",")[0].trim()
            }
        }
        
        return request.remoteAddr
    }

    private fun generateOperationId(): String {
        return "op-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
    }

    private fun generateCorrelationId(): String {
        return "corr-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
    }
}