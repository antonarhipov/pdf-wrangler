package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.ErrorResponse
import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

/**
 * Service for systematic error categorization to organize and handle different types of errors.
 * Provides comprehensive error classification and context-aware categorization.
 */
@Service
class ErrorCategorizationService {

    private val logger = LoggerFactory.getLogger(ErrorCategorizationService::class.java)

    /**
     * Primary error categories for systematic error handling
     */
    enum class ErrorCategory {
        VALIDATION_ERROR,       // Input validation and format issues
        PROCESSING_ERROR,       // Core processing and operation failures
        SECURITY_ERROR,         // Security violations and threats
        RESOURCE_ERROR,         // Resource constraints and limits
        SYSTEM_ERROR,          // System-level and infrastructure issues
        BUSINESS_LOGIC_ERROR,  // Business rule violations
        INTEGRATION_ERROR,     // External service and dependency issues
        DATA_ERROR,           // Data integrity and consistency issues
        CONFIGURATION_ERROR,  // Configuration and setup issues
        USER_ERROR           // User-initiated errors and mistakes
    }

    /**
     * Error severity levels for prioritization and handling
     */
    enum class ErrorSeverity {
        CRITICAL,    // System-threatening errors requiring immediate action
        HIGH,        // Serious errors affecting functionality
        MEDIUM,      // Moderate errors with workarounds available
        LOW,         // Minor errors with minimal impact
        INFO         // Informational errors or warnings
    }

    /**
     * Error handling strategies based on category and severity
     */
    enum class ErrorHandlingStrategy {
        IMMEDIATE_RETRY,      // Retry immediately
        DELAYED_RETRY,        // Retry with exponential backoff
        FALLBACK,            // Use alternative approach
        USER_INTERVENTION,   // Require user action
        ESCALATION,         // Escalate to higher level
        LOGGING_ONLY,       // Log and continue
        FAIL_FAST,         // Fail immediately
        GRACEFUL_DEGRADATION // Continue with reduced functionality
    }

    /**
     * Detailed error classification result
     */
    data class ErrorClassification(
        val category: ErrorCategory,
        val severity: ErrorSeverity,
        val handlingStrategy: ErrorHandlingStrategy,
        val context: Map<String, Any> = emptyMap(),
        val suggestedActions: List<String> = emptyList(),
        val escalationRequired: Boolean = false,
        val userNotificationRequired: Boolean = false,
        val recoverable: Boolean = false,
        val retryable: Boolean = false
    )

    /**
     * Categorizes a general exception into structured error classification
     */
    fun categorizeException(exception: Throwable, context: Map<String, Any> = emptyMap()): ErrorClassification {
        return when (exception) {
            is PdfOperationException -> categorizePdfOperationException(exception, context)
            is SecurityException -> categorizeSecurityException(exception, context)
            is IllegalArgumentException -> categorizeValidationException(exception, context)
            is IllegalStateException -> categorizeSystemException(exception, context)
            is OutOfMemoryError -> categorizeResourceException(exception, context)
            is java.util.concurrent.TimeoutException -> categorizeTimeoutException(exception, context)
            is java.io.IOException -> categorizeIOException(exception, context)
            else -> categorizeGenericException(exception, context)
        }
    }

    /**
     * Categorizes PDF operation exceptions with detailed analysis
     */
    fun categorizePdfOperationException(
        exception: PdfOperationException,
        context: Map<String, Any> = emptyMap()
    ): ErrorClassification {
        
        val baseCategory = when (exception.determineErrorCategory()) {
            PdfOperationException.ErrorCategory.VALIDATION_ERROR -> ErrorCategory.VALIDATION_ERROR
            PdfOperationException.ErrorCategory.PROCESSING_ERROR -> ErrorCategory.PROCESSING_ERROR
            PdfOperationException.ErrorCategory.RESOURCE_ERROR -> ErrorCategory.RESOURCE_ERROR
            PdfOperationException.ErrorCategory.FORMAT_ERROR -> ErrorCategory.VALIDATION_ERROR
            PdfOperationException.ErrorCategory.PERMISSION_ERROR -> ErrorCategory.SECURITY_ERROR
            PdfOperationException.ErrorCategory.CONFIGURATION_ERROR -> ErrorCategory.CONFIGURATION_ERROR
            PdfOperationException.ErrorCategory.DEPENDENCY_ERROR -> ErrorCategory.INTEGRATION_ERROR
            PdfOperationException.ErrorCategory.DATA_ERROR -> ErrorCategory.DATA_ERROR
            PdfOperationException.ErrorCategory.TIMEOUT_ERROR -> ErrorCategory.RESOURCE_ERROR
            PdfOperationException.ErrorCategory.SYSTEM_ERROR -> ErrorCategory.SYSTEM_ERROR
        }

        val severity = mapPdfSeverity(exception.severity)
        val handlingStrategy = determineHandlingStrategy(baseCategory, severity, exception.retryable, exception.recoverable)

        return ErrorClassification(
            category = baseCategory,
            severity = severity,
            handlingStrategy = handlingStrategy,
            context = context + exception.operationContext,
            suggestedActions = generateSuggestedActions(baseCategory, exception),
            escalationRequired = severity == ErrorSeverity.CRITICAL,
            userNotificationRequired = shouldNotifyUser(baseCategory, severity),
            recoverable = exception.recoverable,
            retryable = exception.retryable
        )
    }

    /**
     * Categorizes security exceptions with threat assessment
     */
    fun categorizeSecurityException(
        exception: SecurityException,
        context: Map<String, Any> = emptyMap()
    ): ErrorClassification {
        
        val severity = mapSecuritySeverity(exception.severity)
        val handlingStrategy = when (exception.errorCode) {
            "RATE_LIMIT_ERROR" -> ErrorHandlingStrategy.DELAYED_RETRY
            "RESOURCE_LIMIT_ERROR" -> ErrorHandlingStrategy.USER_INTERVENTION
            "MALICIOUS_CONTENT_ERROR" -> ErrorHandlingStrategy.FAIL_FAST
            "PATH_TRAVERSAL_ERROR" -> ErrorHandlingStrategy.FAIL_FAST
            else -> ErrorHandlingStrategy.ESCALATION
        }

        return ErrorClassification(
            category = ErrorCategory.SECURITY_ERROR,
            severity = severity,
            handlingStrategy = handlingStrategy,
            context = context + exception.additionalContext,
            suggestedActions = generateSecuritySuggestedActions(exception),
            escalationRequired = severity >= ErrorSeverity.HIGH,
            userNotificationRequired = true,
            recoverable = false,
            retryable = exception.errorCode in listOf("RATE_LIMIT_ERROR", "RESOURCE_LIMIT_ERROR")
        )
    }

    /**
     * Provides error handling recommendations based on classification
     */
    fun getErrorHandlingRecommendations(classification: ErrorClassification): Map<String, Any> {
        return mapOf(
            "immediateActions" to getImmediateActions(classification),
            "retryStrategy" to getRetryStrategy(classification),
            "userCommunication" to getUserCommunication(classification),
            "monitoringAlerts" to getMonitoringAlerts(classification),
            "escalationPath" to getEscalationPath(classification)
        )
    }

    /**
     * Analyzes error patterns and provides insights
     */
    fun analyzeErrorPatterns(
        exceptions: List<Throwable>,
        timeWindowMinutes: Long = 60
    ): Map<String, Any> {
        
        val classifications = exceptions.map { categorizeException(it) }
        val categoryFrequency = classifications.groupingBy { it.category }.eachCount()
        val severityDistribution = classifications.groupingBy { it.severity }.eachCount()
        
        val criticalErrors = classifications.count { it.severity == ErrorSeverity.CRITICAL }
        val retryableErrors = classifications.count { it.retryable }
        val escalationRequired = classifications.any { it.escalationRequired }

        return mapOf(
            "totalErrors" to exceptions.size,
            "timeWindowMinutes" to timeWindowMinutes,
            "categoryBreakdown" to categoryFrequency,
            "severityDistribution" to severityDistribution,
            "criticalErrorCount" to criticalErrors,
            "retryableErrorCount" to retryableErrors,
            "escalationRequired" to escalationRequired,
            "topErrorCategories" to categoryFrequency.toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { "${it.first}: ${it.second}" },
            "recommendations" to generatePatternRecommendations(classifications)
        )
    }

    private fun categorizeValidationException(
        exception: IllegalArgumentException,
        context: Map<String, Any>
    ): ErrorClassification {
        return ErrorClassification(
            category = ErrorCategory.VALIDATION_ERROR,
            severity = ErrorSeverity.MEDIUM,
            handlingStrategy = ErrorHandlingStrategy.USER_INTERVENTION,
            context = context,
            suggestedActions = listOf("Validate input parameters", "Check data format"),
            userNotificationRequired = true,
            recoverable = true,
            retryable = false
        )
    }

    private fun categorizeSystemException(
        exception: IllegalStateException,
        context: Map<String, Any>
    ): ErrorClassification {
        return ErrorClassification(
            category = ErrorCategory.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            handlingStrategy = ErrorHandlingStrategy.ESCALATION,
            context = context,
            suggestedActions = listOf("Check system state", "Restart service if necessary"),
            escalationRequired = true,
            userNotificationRequired = true,
            recoverable = false,
            retryable = true
        )
    }

    private fun categorizeResourceException(
        exception: OutOfMemoryError,
        context: Map<String, Any>
    ): ErrorClassification {
        return ErrorClassification(
            category = ErrorCategory.RESOURCE_ERROR,
            severity = ErrorSeverity.CRITICAL,
            handlingStrategy = ErrorHandlingStrategy.FAIL_FAST,
            context = context,
            suggestedActions = listOf("Increase memory allocation", "Reduce file sizes", "Use streaming processing"),
            escalationRequired = true,
            userNotificationRequired = true,
            recoverable = false,
            retryable = false
        )
    }

    private fun categorizeTimeoutException(
        exception: java.util.concurrent.TimeoutException,
        context: Map<String, Any>
    ): ErrorClassification {
        return ErrorClassification(
            category = ErrorCategory.RESOURCE_ERROR,
            severity = ErrorSeverity.MEDIUM,
            handlingStrategy = ErrorHandlingStrategy.DELAYED_RETRY,
            context = context,
            suggestedActions = listOf("Increase timeout value", "Retry with smaller batch size"),
            userNotificationRequired = true,
            recoverable = true,
            retryable = true
        )
    }

    private fun categorizeIOException(
        exception: java.io.IOException,
        context: Map<String, Any>
    ): ErrorClassification {
        return ErrorClassification(
            category = ErrorCategory.SYSTEM_ERROR,
            severity = ErrorSeverity.HIGH,
            handlingStrategy = ErrorHandlingStrategy.IMMEDIATE_RETRY,
            context = context,
            suggestedActions = listOf("Check file permissions", "Verify disk space", "Check network connectivity"),
            userNotificationRequired = true,
            recoverable = true,
            retryable = true
        )
    }

    private fun categorizeGenericException(
        exception: Throwable,
        context: Map<String, Any>
    ): ErrorClassification {
        return ErrorClassification(
            category = ErrorCategory.SYSTEM_ERROR,
            severity = ErrorSeverity.MEDIUM,
            handlingStrategy = ErrorHandlingStrategy.LOGGING_ONLY,
            context = context + mapOf("exceptionType" to exception.javaClass.simpleName),
            suggestedActions = listOf("Review logs for details", "Contact support if persistent"),
            userNotificationRequired = false,
            recoverable = false,
            retryable = false
        )
    }

    private fun mapPdfSeverity(severity: PdfOperationException.OperationSeverity): ErrorSeverity {
        return when (severity) {
            PdfOperationException.OperationSeverity.LOW -> ErrorSeverity.LOW
            PdfOperationException.OperationSeverity.MEDIUM -> ErrorSeverity.MEDIUM
            PdfOperationException.OperationSeverity.HIGH -> ErrorSeverity.HIGH
            PdfOperationException.OperationSeverity.CRITICAL -> ErrorSeverity.CRITICAL
        }
    }

    private fun mapSecuritySeverity(severity: SecurityException.SecuritySeverity): ErrorSeverity {
        return when (severity) {
            SecurityException.SecuritySeverity.LOW -> ErrorSeverity.LOW
            SecurityException.SecuritySeverity.MEDIUM -> ErrorSeverity.MEDIUM
            SecurityException.SecuritySeverity.HIGH -> ErrorSeverity.HIGH
            SecurityException.SecuritySeverity.CRITICAL -> ErrorSeverity.CRITICAL
        }
    }

    private fun determineHandlingStrategy(
        category: ErrorCategory,
        severity: ErrorSeverity,
        retryable: Boolean,
        recoverable: Boolean
    ): ErrorHandlingStrategy {
        return when {
            severity == ErrorSeverity.CRITICAL -> ErrorHandlingStrategy.ESCALATION
            category == ErrorCategory.SECURITY_ERROR -> ErrorHandlingStrategy.FAIL_FAST
            category == ErrorCategory.VALIDATION_ERROR -> ErrorHandlingStrategy.USER_INTERVENTION
            retryable && severity <= ErrorSeverity.MEDIUM -> ErrorHandlingStrategy.IMMEDIATE_RETRY
            retryable && severity == ErrorSeverity.HIGH -> ErrorHandlingStrategy.DELAYED_RETRY
            recoverable -> ErrorHandlingStrategy.FALLBACK
            else -> ErrorHandlingStrategy.LOGGING_ONLY
        }
    }

    private fun generateSuggestedActions(
        category: ErrorCategory,
        exception: PdfOperationException
    ): List<String> {
        val actions = mutableListOf<String>()
        
        when (category) {
            ErrorCategory.VALIDATION_ERROR -> {
                actions.addAll(listOf("Verify file format", "Check file integrity", "Validate input parameters"))
            }
            ErrorCategory.PROCESSING_ERROR -> {
                actions.addAll(listOf("Retry with different settings", "Use alternative processing method"))
            }
            ErrorCategory.RESOURCE_ERROR -> {
                actions.addAll(listOf("Use smaller files", "Increase available memory", "Retry during off-peak hours"))
            }
            ErrorCategory.SYSTEM_ERROR -> {
                actions.addAll(listOf("Contact system administrator", "Check system logs"))
            }
            else -> {
                actions.add("Review error details and contact support")
            }
        }

        if (exception.retryable) {
            actions.add("Operation can be retried")
        }
        
        if (exception.recoverable) {
            actions.add("Try alternative approach")
        }

        return actions.distinct()
    }

    private fun generateSecuritySuggestedActions(exception: SecurityException): List<String> {
        return when (exception.errorCode) {
            "FILE_VALIDATION_ERROR" -> listOf("Use valid file format", "Scan file for issues")
            "MALICIOUS_CONTENT_ERROR" -> listOf("Use different file", "Contact security team")
            "RATE_LIMIT_ERROR" -> listOf("Wait before retrying", "Reduce request frequency")
            "PATH_TRAVERSAL_ERROR" -> listOf("Use valid file paths", "Check file location")
            else -> listOf("Review security requirements", "Contact security team")
        }
    }

    private fun shouldNotifyUser(category: ErrorCategory, severity: ErrorSeverity): Boolean {
        return when (category) {
            ErrorCategory.VALIDATION_ERROR -> true
            ErrorCategory.SECURITY_ERROR -> true
            ErrorCategory.USER_ERROR -> true
            else -> severity >= ErrorSeverity.MEDIUM
        }
    }

    private fun getImmediateActions(classification: ErrorClassification): List<String> {
        return when (classification.handlingStrategy) {
            ErrorHandlingStrategy.IMMEDIATE_RETRY -> listOf("Retry operation immediately")
            ErrorHandlingStrategy.DELAYED_RETRY -> listOf("Schedule retry with backoff")
            ErrorHandlingStrategy.FAIL_FAST -> listOf("Stop processing", "Report error")
            ErrorHandlingStrategy.ESCALATION -> listOf("Alert operations team", "Create incident")
            ErrorHandlingStrategy.USER_INTERVENTION -> listOf("Notify user", "Request corrective action")
            ErrorHandlingStrategy.FALLBACK -> listOf("Switch to alternative method")
            ErrorHandlingStrategy.GRACEFUL_DEGRADATION -> listOf("Continue with reduced functionality")
            ErrorHandlingStrategy.LOGGING_ONLY -> listOf("Log error details")
        }
    }

    private fun getRetryStrategy(classification: ErrorClassification): Map<String, Any> {
        return when {
            !classification.retryable -> mapOf("retryable" to false, "reason" to "Operation not retryable")
            classification.handlingStrategy == ErrorHandlingStrategy.IMMEDIATE_RETRY -> mapOf(
                "retryable" to true,
                "strategy" to "immediate",
                "maxAttempts" to 3,
                "delayMs" to 0
            )
            classification.handlingStrategy == ErrorHandlingStrategy.DELAYED_RETRY -> mapOf(
                "retryable" to true,
                "strategy" to "exponential_backoff",
                "maxAttempts" to 5,
                "initialDelayMs" to 1000,
                "maxDelayMs" to 30000
            )
            else -> mapOf("retryable" to false, "reason" to "Strategy does not support retry")
        }
    }

    private fun getUserCommunication(classification: ErrorClassification): Map<String, Any> {
        return mapOf(
            "notifyUser" to classification.userNotificationRequired,
            "severity" to classification.severity.name,
            "suggestedActions" to classification.suggestedActions,
            "escalationRequired" to classification.escalationRequired
        )
    }

    private fun getMonitoringAlerts(classification: ErrorClassification): Map<String, Any> {
        return mapOf(
            "alertRequired" to (classification.severity >= ErrorSeverity.HIGH),
            "alertLevel" to when (classification.severity) {
                ErrorSeverity.CRITICAL -> "critical"
                ErrorSeverity.HIGH -> "high"
                ErrorSeverity.MEDIUM -> "medium"
                else -> "low"
            },
            "escalationRequired" to classification.escalationRequired
        )
    }

    private fun getEscalationPath(classification: ErrorClassification): Map<String, Any> {
        return if (classification.escalationRequired) {
            mapOf(
                "escalate" to true,
                "level" to when (classification.severity) {
                    ErrorSeverity.CRITICAL -> "immediate"
                    ErrorSeverity.HIGH -> "urgent"
                    else -> "normal"
                },
                "recipients" to when (classification.category) {
                    ErrorCategory.SECURITY_ERROR -> listOf("security-team", "operations")
                    ErrorCategory.SYSTEM_ERROR -> listOf("operations", "infrastructure")
                    ErrorCategory.RESOURCE_ERROR -> listOf("operations", "capacity-planning")
                    else -> listOf("operations")
                }
            )
        } else {
            mapOf("escalate" to false)
        }
    }

    private fun generatePatternRecommendations(classifications: List<ErrorClassification>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val criticalCount = classifications.count { it.severity == ErrorSeverity.CRITICAL }
        if (criticalCount > 0) {
            recommendations.add("Address $criticalCount critical errors immediately")
        }
        
        val resourceErrors = classifications.count { it.category == ErrorCategory.RESOURCE_ERROR }
        if (resourceErrors > classifications.size * 0.3) {
            recommendations.add("Consider scaling up resources - ${(resourceErrors.toDouble() / classifications.size * 100).toInt()}% of errors are resource-related")
        }
        
        val validationErrors = classifications.count { it.category == ErrorCategory.VALIDATION_ERROR }
        if (validationErrors > classifications.size * 0.4) {
            recommendations.add("Improve input validation - ${(validationErrors.toDouble() / classifications.size * 100).toInt()}% of errors are validation-related")
        }
        
        val retryableErrors = classifications.count { it.retryable }
        if (retryableErrors > 0) {
            recommendations.add("$retryableErrors errors can be automatically retried")
        }
        
        return recommendations
    }
}