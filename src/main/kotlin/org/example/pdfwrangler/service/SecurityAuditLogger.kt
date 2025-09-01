package org.example.pdfwrangler.service

import org.example.pdfwrangler.exception.SecurityException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import jakarta.servlet.http.HttpServletRequest
import jakarta.annotation.PostConstruct
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Service for comprehensive security audit logging to track and monitor
 * security-related activities throughout the application.
 * Provides structured logging with proper context and event classification.
 */
@Service
class SecurityAuditLogger {

    private val logger = LoggerFactory.getLogger("SECURITY_AUDIT")

    companion object {
        private const val AUDIT_EVENT_PREFIX = "[SECURITY_AUDIT]"
        private const val MAX_FIELD_LENGTH = 1000
        private const val MAX_CONTEXT_SIZE = 50
        
        // MDC keys for structured logging
        private const val MDC_EVENT_TYPE = "security.event.type"
        private const val MDC_SEVERITY = "security.severity"
        private const val MDC_CLIENT_IP = "security.client.ip"
        private const val MDC_USER_AGENT = "security.user.agent"
        private const val MDC_EVENT_ID = "security.event.id"
        private const val MDC_SESSION_ID = "security.session.id"
        
        // Shared ObjectMapper for JSON serialization
        private val objectMapper: ObjectMapper = jacksonObjectMapper()
    }

    enum class SecurityEventType {
        FILE_UPLOAD,
        FILE_VALIDATION_FAILURE,
        MALICIOUS_CONTENT_DETECTED,
        PATH_TRAVERSAL_ATTEMPT,
        RATE_LIMIT_EXCEEDED,
        AUTHENTICATION_FAILURE,
        AUTHORIZATION_FAILURE,
        RESOURCE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY,
        SECURITY_EXCEPTION,
        SYSTEM_SECURITY_EVENT,
        DATA_ACCESS_VIOLATION,
        INPUT_VALIDATION_FAILURE,
        CRYPTOGRAPHIC_FAILURE,
        TEMP_FILE_VIOLATION,
        CONFIGURATION_CHANGE
    }

    enum class SecuritySeverity {
        INFO,       // Informational security event
        LOW,        // Low priority security concern
        MEDIUM,     // Moderate security issue requiring attention
        HIGH,       // High priority security threat
        CRITICAL    // Critical security incident requiring immediate response
    }

    data class SecurityAuditEvent(
        val eventId: String = UUID.randomUUID().toString(),
        val timestamp: Instant = Instant.now(),
        val eventType: SecurityEventType,
        val severity: SecuritySeverity,
        val message: String,
        val clientIp: String? = null,
        val userAgent: String? = null,
        val sessionId: String? = null,
        val userId: String? = null,
        val resourceId: String? = null,
        val action: String? = null,
        val outcome: String? = null,
        val details: Map<String, Any> = emptyMap(),
        val exception: String? = null,
        val stackTrace: String? = null,
        val requestUri: String? = null,
        val httpMethod: String? = null,
        val responseStatus: Int? = null,
        val processingTimeMs: Long? = null
    ) {
        fun toStructuredLogMessage(): String {
            val logData = mapOf(
                "eventId" to eventId,
                "timestamp" to timestamp.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
                "eventType" to eventType.name,
                "severity" to severity.name,
                "message" to truncateField(message),
                "clientIp" to clientIp,
                "userAgent" to truncateField(userAgent),
                "sessionId" to sessionId,
                "userId" to userId,
                "resourceId" to resourceId,
                "action" to action,
                "outcome" to outcome,
                "requestUri" to requestUri,
                "httpMethod" to httpMethod,
                "responseStatus" to responseStatus,
                "processingTimeMs" to processingTimeMs,
                "exception" to exception,
                "details" to limitMapSize(details)
            ).filterValues { it != null }
            
            return try {
                "$AUDIT_EVENT_PREFIX ${objectMapper.writeValueAsString(logData)}"
            } catch (e: Exception) {
                "$AUDIT_EVENT_PREFIX {\"eventId\":\"$eventId\",\"error\":\"Failed to serialize audit event\",\"message\":\"${truncateField(message)}\"}"
            }
        }
        
        private fun truncateField(value: String?): String? {
            return value?.take(MAX_FIELD_LENGTH)
        }
        
        private fun limitMapSize(map: Map<String, Any>): Map<String, Any> {
            return if (map.size <= MAX_CONTEXT_SIZE) {
                map
            } else {
                map.entries.take(MAX_CONTEXT_SIZE).associate { it.key to it.value } + 
                ("_truncated" to "Additional ${map.size - MAX_CONTEXT_SIZE} entries omitted")
            }
        }
    }

    @PostConstruct
    fun initialize() {
        logger.info("Security audit logging system initialized")
        logSecurityEvent(
            eventType = SecurityEventType.SYSTEM_SECURITY_EVENT,
            severity = SecuritySeverity.INFO,
            message = "Security audit logging system started",
            details = mapOf(
                "component" to "SecurityAuditLogger",
                "status" to "initialized"
            )
        )
    }

    /**
     * Logs a general security event.
     */
    fun logSecurityEvent(
        eventType: SecurityEventType,
        severity: SecuritySeverity,
        message: String,
        details: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        val request = getCurrentRequest()
        val event = SecurityAuditEvent(
            eventType = eventType,
            severity = severity,
            message = message,
            clientIp = extractClientIp(request),
            userAgent = request?.getHeader("User-Agent"),
            sessionId = request?.session?.id,
            requestUri = request?.requestURI,
            httpMethod = request?.method,
            details = details,
            exception = exception?.javaClass?.simpleName,
            stackTrace = exception?.stackTrace?.take(5)?.joinToString("\n") { it.toString() }
        )
        
        logAuditEvent(event)
    }

    /**
     * Logs file upload security events.
     */
    fun logFileUpload(
        filename: String,
        fileSize: Long,
        contentType: String,
        outcome: String,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.FILE_UPLOAD,
            severity = if (outcome == "SUCCESS") SecuritySeverity.INFO else SecuritySeverity.MEDIUM,
            message = "File upload: $filename",
            details = details + mapOf(
                "filename" to filename,
                "fileSize" to fileSize,
                "contentType" to contentType,
                "outcome" to outcome
            )
        )
    }

    /**
     * Logs file validation failures.
     */
    fun logValidationFailure(
        filename: String?,
        validationType: String,
        reason: String,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.FILE_VALIDATION_FAILURE,
            severity = SecuritySeverity.MEDIUM,
            message = "File validation failed: $reason",
            details = details + buildMap {
                put("validationType", validationType)
                put("reason", reason)
                filename?.let { put("filename", it) }
            }
        )
    }

    /**
     * Logs malicious content detection.
     */
    fun logMaliciousContentDetected(
        filename: String?,
        threatType: String,
        confidence: Double,
        detectionMethod: String,
        details: Map<String, Any> = emptyMap()
    ) {
        val severity = when {
            confidence >= 0.9 -> SecuritySeverity.CRITICAL
            confidence >= 0.7 -> SecuritySeverity.HIGH
            else -> SecuritySeverity.MEDIUM
        }
        
        logSecurityEvent(
            eventType = SecurityEventType.MALICIOUS_CONTENT_DETECTED,
            severity = severity,
            message = "Malicious content detected: $threatType",
            details = details + buildMap {
                put("threatType", threatType)
                put("confidence", confidence)
                put("detectionMethod", detectionMethod)
                filename?.let { put("filename", it) }
            }
        )
    }

    /**
     * Logs path traversal attempts.
     */
    fun logPathTraversalAttempt(
        attemptedPath: String,
        attackPattern: String?,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.PATH_TRAVERSAL_ATTEMPT,
            severity = SecuritySeverity.HIGH,
            message = "Path traversal attempt detected",
            details = details + buildMap {
                put("attemptedPath", attemptedPath)
                attackPattern?.let { put("attackPattern", it) }
            }
        )
    }

    /**
     * Logs rate limit violations.
     */
    fun logRateLimitExceeded(
        clientId: String,
        limitType: String,
        currentCount: Int,
        maxAllowed: Int,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.RATE_LIMIT_EXCEEDED,
            severity = SecuritySeverity.MEDIUM,
            message = "Rate limit exceeded: $limitType",
            details = details + mapOf(
                "clientId" to clientId,
                "limitType" to limitType,
                "currentCount" to currentCount,
                "maxAllowed" to maxAllowed
            )
        )
    }

    /**
     * Logs authentication failures.
     */
    fun logAuthenticationFailure(
        authMethod: String?,
        reason: String,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.AUTHENTICATION_FAILURE,
            severity = SecuritySeverity.HIGH,
            message = "Authentication failure: $reason",
            details = details + buildMap {
                put("reason", reason)
                authMethod?.let { put("authMethod", it) }
            }
        )
    }

    /**
     * Logs resource limit violations.
     */
    fun logResourceLimitExceeded(
        limitType: String,
        currentValue: Long,
        maxValue: Long,
        resourceId: String?,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.RESOURCE_LIMIT_EXCEEDED,
            severity = SecuritySeverity.HIGH,
            message = "Resource limit exceeded: $limitType",
            details = details + buildMap {
                put("limitType", limitType)
                put("currentValue", currentValue)
                put("maxValue", maxValue)
                resourceId?.let { put("resourceId", it) }
            }
        )
    }

    /**
     * Logs security exceptions with full context.
     */
    fun logSecurityException(securityException: SecurityException, details: Map<String, Any> = emptyMap()) {
        val combinedDetails = details + securityException.additionalContext
        
        logSecurityEvent(
            eventType = SecurityEventType.SECURITY_EXCEPTION,
            severity = mapSeverity(securityException.severity),
            message = securityException.message ?: "Security exception occurred",
            details = combinedDetails + mapOf(
                "errorCode" to securityException.errorCode,
                "exceptionType" to securityException.javaClass.simpleName
            ),
            exception = securityException
        )
    }

    /**
     * Logs suspicious activity patterns.
     */
    fun logSuspiciousActivity(
        activityType: String,
        description: String,
        riskScore: Double? = null,
        details: Map<String, Any> = emptyMap()
    ) {
        val severity = when {
            riskScore != null && riskScore >= 0.8 -> SecuritySeverity.HIGH
            riskScore != null && riskScore >= 0.5 -> SecuritySeverity.MEDIUM
            else -> SecuritySeverity.LOW
        }
        
        logSecurityEvent(
            eventType = SecurityEventType.SUSPICIOUS_ACTIVITY,
            severity = severity,
            message = "Suspicious activity detected: $activityType",
            details = details + buildMap {
                put("activityType", activityType)
                put("description", description)
                riskScore?.let { put("riskScore", it) }
            }
        )
    }

    /**
     * Logs system security configuration changes.
     */
    fun logConfigurationChange(
        component: String,
        changeType: String,
        oldValue: String?,
        newValue: String?,
        details: Map<String, Any> = emptyMap()
    ) {
        logSecurityEvent(
            eventType = SecurityEventType.CONFIGURATION_CHANGE,
            severity = SecuritySeverity.MEDIUM,
            message = "Security configuration changed: $component",
            details = details + buildMap {
                put("component", component)
                put("changeType", changeType)
                oldValue?.let { put("oldValue", it) }
                newValue?.let { put("newValue", it) }
            }
        )
    }

    /**
     * Gets audit statistics for monitoring.
     */
    fun getAuditStatistics(): Map<String, Any> {
        return mapOf(
            "auditLoggerStatus" to "active",
            "structuredLoggingEnabled" to true,
            "eventTypesSupported" to SecurityEventType.values().map { it.name },
            "severityLevels" to SecuritySeverity.values().map { it.name },
            "maxFieldLength" to MAX_FIELD_LENGTH,
            "maxContextSize" to MAX_CONTEXT_SIZE
        )
    }

    private fun logAuditEvent(event: SecurityAuditEvent) {
        try {
            // Set MDC for structured logging
            MDC.put(MDC_EVENT_TYPE, event.eventType.name)
            MDC.put(MDC_SEVERITY, event.severity.name)
            MDC.put(MDC_EVENT_ID, event.eventId)
            event.clientIp?.let { MDC.put(MDC_CLIENT_IP, it) }
            event.userAgent?.let { MDC.put(MDC_USER_AGENT, it) }
            event.sessionId?.let { MDC.put(MDC_SESSION_ID, it) }

            val structuredMessage = event.toStructuredLogMessage()
            
            // Log at appropriate level based on severity
            when (event.severity) {
                SecuritySeverity.INFO -> logger.info(structuredMessage)
                SecuritySeverity.LOW -> logger.info(structuredMessage)
                SecuritySeverity.MEDIUM -> logger.warn(structuredMessage)
                SecuritySeverity.HIGH -> logger.error(structuredMessage)
                SecuritySeverity.CRITICAL -> logger.error(structuredMessage)
            }
            
        } finally {
            // Clear MDC to prevent memory leaks
            MDC.remove(MDC_EVENT_TYPE)
            MDC.remove(MDC_SEVERITY)
            MDC.remove(MDC_EVENT_ID)
            MDC.remove(MDC_CLIENT_IP)
            MDC.remove(MDC_USER_AGENT)
            MDC.remove(MDC_SESSION_ID)
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

    private fun extractClientIp(request: HttpServletRequest?): String? {
        if (request == null) return null
        
        // Check common headers for real IP address
        val headers = listOf("X-Forwarded-For", "X-Real-IP", "X-Originating-IP", "X-Client-IP")
        
        for (header in headers) {
            val ip = request.getHeader(header)
            if (!ip.isNullOrBlank() && ip != "unknown") {
                return ip.split(",")[0].trim()
            }
        }
        
        return request.remoteAddr
    }

    private fun mapSeverity(securitySeverity: SecurityException.SecuritySeverity): SecuritySeverity {
        return when (securitySeverity) {
            SecurityException.SecuritySeverity.LOW -> SecuritySeverity.LOW
            SecurityException.SecuritySeverity.MEDIUM -> SecuritySeverity.MEDIUM
            SecurityException.SecuritySeverity.HIGH -> SecuritySeverity.HIGH
            SecurityException.SecuritySeverity.CRITICAL -> SecuritySeverity.CRITICAL
        }
    }
}