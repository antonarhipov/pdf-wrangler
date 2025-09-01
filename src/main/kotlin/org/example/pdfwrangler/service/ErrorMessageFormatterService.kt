package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.ErrorResponse
import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.*

/**
 * Service responsible for formatting error messages in a user-friendly manner.
 * Provides contextual, actionable, and appropriately styled error messages.
 */
@Service
class ErrorMessageFormatterService(
    private val errorLocalizationService: ErrorLocalizationService
) {
    private val logger = LoggerFactory.getLogger(ErrorMessageFormatterService::class.java)

    /**
     * Message formatting styles based on severity and context
     */
    enum class MessageStyle {
        TECHNICAL,     // Detailed technical information for developers
        USER_FRIENDLY, // Simplified messages for end users
        BUSINESS,      // Business-oriented messages for stakeholders
        MINIMAL        // Minimal essential information only
    }

    /**
     * Message template structure
     */
    data class MessageTemplate(
        val title: String,
        val description: String,
        val suggestions: List<String> = emptyList(),
        val technicalDetails: Map<String, Any> = emptyMap(),
        val userActions: List<String> = emptyList(),
        val supportInfo: Map<String, String> = emptyMap()
    )

    /**
     * Formatted error message result
     */
    data class FormattedErrorMessage(
        val title: String,
        val message: String,
        val severity: String,
        val timestamp: String,
        val suggestions: List<String> = emptyList(),
        val userActions: List<String> = emptyList(),
        val technicalDetails: Map<String, Any> = emptyMap(),
        val supportReference: String? = null,
        val estimatedResolutionTime: String? = null,
        val relatedDocumentation: List<String> = emptyList()
    )

    /**
     * Format error message with specified style and locale
     */
    fun formatErrorMessage(
        errorResponse: ErrorResponse,
        style: MessageStyle = MessageStyle.USER_FRIENDLY,
        locale: Locale? = null
    ): FormattedErrorMessage {
        val actualLocale = locale ?: Locale.getDefault()
        val template = createMessageTemplate(errorResponse, style, actualLocale)
        
        return FormattedErrorMessage(
            title = template.title,
            message = template.description,
            severity = formatSeverity(errorResponse.severity, style),
            timestamp = formatTimestamp(errorResponse.timestamp, actualLocale),
            suggestions = template.suggestions,
            userActions = template.userActions,
            technicalDetails = if (style == MessageStyle.TECHNICAL) template.technicalDetails else emptyMap(),
            supportReference = generateSupportReference(errorResponse),
            estimatedResolutionTime = estimateResolutionTime(errorResponse),
            relatedDocumentation = getRelatedDocumentation(errorResponse)
        )
    }

    /**
     * Format multiple error messages for bulk operations
     */
    fun formatBulkErrorMessages(
        errors: List<ErrorResponse>,
        style: MessageStyle = MessageStyle.USER_FRIENDLY,
        locale: Locale? = null
    ): Map<String, Any> {
        val actualLocale = locale ?: Locale.getDefault()
        val groupedErrors = errors.groupBy { it.category }
        
        val summary = mapOf(
            "total_errors" to errors.size,
            "error_categories" to groupedErrors.keys.toList(),
            "severity_breakdown" to errors.groupBy { it.severity }.mapValues { it.value.size },
            "most_common_error" to errors.groupBy { it.errorCode }.maxByOrNull { it.value.size }?.key
        )
        
        val formattedErrors = errors.map { formatErrorMessage(it, style, actualLocale) }
        
        val recommendations = generateBulkRecommendations(errors, actualLocale)
        
        return mapOf(
            "summary" to summary,
            "errors" to formattedErrors,
            "overall_recommendations" to recommendations,
            "next_steps" to generateNextSteps(errors, actualLocale)
        )
    }

    /**
     * Create contextual error message for specific PDF operations
     */
    fun formatOperationError(
        exception: PdfOperationException,
        operationContext: Map<String, Any> = emptyMap(),
        style: MessageStyle = MessageStyle.USER_FRIENDLY,
        locale: Locale? = null
    ): FormattedErrorMessage {
        val actualLocale = locale ?: Locale.getDefault()
        val operationType = exception.operationType.name.lowercase().replaceFirstChar { it.uppercase() }
        
        val title = when (style) {
            MessageStyle.USER_FRIENDLY -> getLocalizedMessage(
                "error.operation.${exception.operationType.name.lowercase()}.title",
                "$operationType Operation Failed",
                actualLocale
            )
            MessageStyle.TECHNICAL -> "PdfOperationException: ${exception.errorCode}"
            MessageStyle.BUSINESS -> "PDF Processing Issue in $operationType"
            MessageStyle.MINIMAL -> "Operation Error"
        }
        
        val description = buildOperationDescription(exception, operationContext, style, actualLocale)
        val suggestions = generateOperationSuggestions(exception, operationContext, actualLocale)
        val userActions = generateOperationUserActions(exception, operationContext, actualLocale)
        
        return FormattedErrorMessage(
            title = title,
            message = description,
            severity = formatSeverity(mapPdfSeverityToErrorSeverity(exception.severity), style),
            timestamp = formatCurrentTimestamp(actualLocale),
            suggestions = suggestions,
            userActions = userActions,
            technicalDetails = if (style == MessageStyle.TECHNICAL) buildTechnicalDetails(exception, operationContext) else emptyMap(),
            supportReference = generateSupportReference(exception),
            estimatedResolutionTime = estimateOperationResolutionTime(exception),
            relatedDocumentation = getOperationDocumentation(exception.operationType)
        )
    }

    /**
     * Format security error with appropriate sensitivity
     */
    fun formatSecurityError(
        exception: SecurityException,
        style: MessageStyle = MessageStyle.USER_FRIENDLY,
        locale: Locale? = null
    ): FormattedErrorMessage {
        val actualLocale = locale ?: Locale.getDefault()
        
        // Security errors require careful message formatting to avoid information disclosure
        val title = when (style) {
            MessageStyle.USER_FRIENDLY -> getLocalizedMessage(
                "error.security.generic.title",
                "Access Denied",
                actualLocale
            )
            MessageStyle.TECHNICAL -> "SecurityException: ${exception.errorCode}"
            MessageStyle.BUSINESS -> "Security Policy Violation"
            MessageStyle.MINIMAL -> "Access Error"
        }
        
        val description = buildSecurityDescription(exception, style, actualLocale)
        val suggestions = generateSecuritySuggestions(exception, style, actualLocale)
        
        return FormattedErrorMessage(
            title = title,
            message = description,
            severity = formatSeverity(mapSecuritySeverityToErrorSeverity(exception.severity), style),
            timestamp = formatCurrentTimestamp(actualLocale),
            suggestions = suggestions,
            userActions = generateSecurityUserActions(exception, actualLocale),
            technicalDetails = if (style == MessageStyle.TECHNICAL) buildSecurityTechnicalDetails(exception) else emptyMap(),
            supportReference = generateSecuritySupportReference(exception),
            estimatedResolutionTime = "Immediate",
            relatedDocumentation = getSecurityDocumentation()
        )
    }

    private fun createMessageTemplate(
        errorResponse: ErrorResponse,
        style: MessageStyle,
        locale: Locale
    ): MessageTemplate {
        val title = when (style) {
            MessageStyle.USER_FRIENDLY -> getLocalizedMessage(
                "error.${errorResponse.category.name.lowercase()}.title",
                errorResponse.errorCode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                locale
            )
            MessageStyle.TECHNICAL -> "${errorResponse.errorCode}: ${errorResponse.message}"
            MessageStyle.BUSINESS -> categorizeForBusiness(errorResponse.category, locale)
            MessageStyle.MINIMAL -> "Error"
        }
        
        val description = buildDescription(errorResponse, style, locale)
        val suggestions = errorResponse.validationErrors?.map { 
            formatValidationError(it, locale) 
        } ?: generateGenericSuggestions(errorResponse, locale)
        
        return MessageTemplate(
            title = title,
            description = description,
            suggestions = suggestions,
            technicalDetails = errorResponse.details ?: emptyMap(),
            userActions = generateUserActions(errorResponse, locale),
            supportInfo = mapOf(
                "error_code" to errorResponse.errorCode,
                "trace_id" to (errorResponse.traceId ?: "N/A")
            )
        )
    }

    private fun buildOperationDescription(
        exception: PdfOperationException,
        context: Map<String, Any>,
        style: MessageStyle,
        locale: Locale
    ): String {
        return when (style) {
            MessageStyle.USER_FRIENDLY -> {
                val baseMessage = getLocalizedMessage(
                    "error.operation.${exception.operationType.name.lowercase()}.description",
                    "The ${exception.operationType.name.lowercase()} operation could not be completed successfully.",
                    locale
                )
                
                val contextInfo = buildContextualInfo(context, locale)
                if (contextInfo.isNotEmpty()) "$baseMessage $contextInfo" else baseMessage
            }
            MessageStyle.TECHNICAL -> {
                val stackTrace = exception.stackTrace.take(3).joinToString("\n") { "  at $it" }
                "${exception.message}\n\nStack trace (top 3 lines):\n$stackTrace\n\nContext: $context"
            }
            MessageStyle.BUSINESS -> {
                "A technical issue occurred during PDF ${exception.operationType.name.lowercase()} processing. " +
                        if (exception.recoverable) "This issue may be temporary and could resolve automatically." 
                        else "Manual intervention may be required."
            }
            MessageStyle.MINIMAL -> exception.message ?: "Operation failed"
        }
    }

    private fun buildDescription(errorResponse: ErrorResponse, style: MessageStyle, locale: Locale): String {
        return when (style) {
            MessageStyle.USER_FRIENDLY -> enhanceUserFriendlyMessage(errorResponse.message, errorResponse, locale)
            MessageStyle.TECHNICAL -> buildTechnicalDescription(errorResponse)
            MessageStyle.BUSINESS -> buildBusinessDescription(errorResponse, locale)
            MessageStyle.MINIMAL -> errorResponse.message
        }
    }

    private fun enhanceUserFriendlyMessage(message: String, errorResponse: ErrorResponse, locale: Locale): String {
        val enhancedMessage = StringBuilder(message)
        
        if (errorResponse.retryable) {
            enhancedMessage.append(" ").append(getLocalizedMessage(
                "error.retryable.suffix",
                "You can try this operation again.",
                locale
            ))
        }
        
        if (errorResponse.recoverable && errorResponse.suggestedAction != null) {
            enhancedMessage.append(" ").append(getLocalizedMessage(
                "error.recoverable.suffix",
                "This issue can be resolved: ${errorResponse.suggestedAction}",
                locale
            ))
        }
        
        return enhancedMessage.toString()
    }

    private fun buildTechnicalDescription(errorResponse: ErrorResponse): String {
        val details = mutableListOf<String>()
        details.add("Error Code: ${errorResponse.errorCode}")
        details.add("Category: ${errorResponse.category}")
        details.add("Severity: ${errorResponse.severity}")
        
        errorResponse.traceId?.let { details.add("Trace ID: $it") }
        errorResponse.operation?.let { details.add("Operation: $it") }
        errorResponse.path?.let { details.add("Request Path: $it") }
        
        details.add("Retryable: ${errorResponse.retryable}")
        details.add("Recoverable: ${errorResponse.recoverable}")
        
        return "${errorResponse.message}\n\nTechnical Details:\n${details.joinToString("\n") { "  • $it" }}"
    }

    private fun buildBusinessDescription(errorResponse: ErrorResponse, locale: Locale): String {
        val impact = when (errorResponse.severity) {
            ErrorResponse.ErrorSeverity.CRITICAL -> getLocalizedMessage("error.impact.critical", "Service disruption", locale)
            ErrorResponse.ErrorSeverity.HIGH -> getLocalizedMessage("error.impact.high", "Significant delay", locale)
            ErrorResponse.ErrorSeverity.MEDIUM -> getLocalizedMessage("error.impact.medium", "Minor inconvenience", locale)
            else -> getLocalizedMessage("error.impact.low", "Minimal impact", locale)
        }
        
        return "${errorResponse.message} Impact: $impact."
    }

    private fun formatSeverity(severity: ErrorResponse.ErrorSeverity, style: MessageStyle): String {
        return when (style) {
            MessageStyle.USER_FRIENDLY -> when (severity) {
                ErrorResponse.ErrorSeverity.CRITICAL -> "Critical Issue"
                ErrorResponse.ErrorSeverity.HIGH -> "Important"
                ErrorResponse.ErrorSeverity.MEDIUM -> "Warning"
                ErrorResponse.ErrorSeverity.LOW -> "Notice"
                ErrorResponse.ErrorSeverity.INFO -> "Information"
            }
            else -> severity.name
        }
    }

    private fun generateOperationSuggestions(
        exception: PdfOperationException,
        context: Map<String, Any>,
        locale: Locale
    ): List<String> {
        val suggestions = mutableListOf<String>()
        
        when (exception.operationType) {
            PdfOperationException.PdfOperationType.MERGE -> {
                suggestions.add(getLocalizedMessage("suggestion.merge.check_files", "Verify all input files are valid PDFs", locale))
                suggestions.add(getLocalizedMessage("suggestion.merge.reduce_files", "Try merging fewer files at once", locale))
            }
            PdfOperationException.PdfOperationType.SPLIT -> {
                suggestions.add(getLocalizedMessage("suggestion.split.check_pages", "Verify the page range is valid", locale))
                suggestions.add(getLocalizedMessage("suggestion.split.check_size", "Check if the file is too large", locale))
            }
            PdfOperationException.PdfOperationType.CONVERSION -> {
                suggestions.add(getLocalizedMessage("suggestion.conversion.check_format", "Verify the source format is supported", locale))
                suggestions.add(getLocalizedMessage("suggestion.conversion.reduce_quality", "Try reducing the output quality settings", locale))
            }
            else -> {
                suggestions.add(getLocalizedMessage("suggestion.generic.retry", "Try the operation again", locale))
            }
        }
        
        if (exception.retryable) {
            suggestions.add(getLocalizedMessage("suggestion.wait_retry", "Wait a moment and retry the operation", locale))
        }
        
        return suggestions
    }

    private fun generateUserActions(errorResponse: ErrorResponse, locale: Locale): List<String> {
        val actions = mutableListOf<String>()
        
        when (errorResponse.category) {
            ErrorResponse.ErrorCategory.VALIDATION_ERROR -> {
                actions.add(getLocalizedMessage("action.validation.review_input", "Review and correct your input", locale))
            }
            ErrorResponse.ErrorCategory.RESOURCE_ERROR -> {
                actions.add(getLocalizedMessage("action.resource.wait_retry", "Wait and try again later", locale))
                actions.add(getLocalizedMessage("action.resource.reduce_load", "Reduce file size or complexity", locale))
            }
            ErrorResponse.ErrorCategory.PERMISSION_ERROR -> {
                actions.add(getLocalizedMessage("action.permission.contact_admin", "Contact your administrator", locale))
            }
            else -> {
                if (errorResponse.retryable) {
                    actions.add(getLocalizedMessage("action.generic.retry", "Retry the operation", locale))
                }
                actions.add(getLocalizedMessage("action.generic.contact_support", "Contact support if the issue persists", locale))
            }
        }
        
        return actions
    }

    private fun generateSupportReference(errorResponse: ErrorResponse): String {
        return "REF-${errorResponse.traceId?.takeLast(8) ?: "UNKNOWN"}-${System.currentTimeMillis().toString().takeLast(6)}"
    }

    private fun generateSupportReference(exception: PdfOperationException): String {
        return "PDF-${exception.operationType.name.take(3)}-${System.currentTimeMillis().toString().takeLast(8)}"
    }

    private fun estimateResolutionTime(errorResponse: ErrorResponse): String? {
        return when (errorResponse.category) {
            ErrorResponse.ErrorCategory.VALIDATION_ERROR -> "Immediate (fix input)"
            ErrorResponse.ErrorCategory.RESOURCE_ERROR -> "1-5 minutes"
            ErrorResponse.ErrorCategory.SYSTEM_ERROR -> "5-30 minutes"
            ErrorResponse.ErrorCategory.EXTERNAL_SERVICE_ERROR -> "Variable (depends on external service)"
            else -> if (errorResponse.retryable) "1-2 minutes" else null
        }
    }

    private fun getRelatedDocumentation(errorResponse: ErrorResponse): List<String> {
        return when (errorResponse.category) {
            ErrorResponse.ErrorCategory.VALIDATION_ERROR -> listOf(
                "/docs/api/validation-rules",
                "/docs/troubleshooting/input-validation"
            )
            ErrorResponse.ErrorCategory.RESOURCE_ERROR -> listOf(
                "/docs/limits-and-quotas",
                "/docs/performance-optimization"
            )
            else -> listOf("/docs/troubleshooting/common-errors")
        }
    }

    private fun formatTimestamp(timestamp: String, locale: Locale): String {
        return try {
            val instant = Instant.parse(timestamp)
            val formatter = DateTimeFormatter.ofLocalizedDateTime(
                java.time.format.FormatStyle.MEDIUM,
                java.time.format.FormatStyle.SHORT
            ).withLocale(locale).withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            timestamp
        }
    }

    private fun formatCurrentTimestamp(locale: Locale): String {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(
            java.time.format.FormatStyle.MEDIUM,
            java.time.format.FormatStyle.SHORT
        ).withLocale(locale).withZone(ZoneId.systemDefault())
        return formatter.format(Instant.now())
    }

    private fun getLocalizedMessage(key: String, defaultMessage: String, locale: Locale): String {
        return try {
            // Use the public method to get localized messages
            val messages = errorLocalizationService.getLocalizedErrorMessages(listOf(key), locale)
            messages[key] ?: defaultMessage
        } catch (e: Exception) {
            defaultMessage
        }
    }

    private fun mapPdfSeverityToErrorSeverity(severity: PdfOperationException.OperationSeverity): ErrorResponse.ErrorSeverity {
        return when (severity) {
            PdfOperationException.OperationSeverity.CRITICAL -> ErrorResponse.ErrorSeverity.CRITICAL
            PdfOperationException.OperationSeverity.HIGH -> ErrorResponse.ErrorSeverity.HIGH
            PdfOperationException.OperationSeverity.MEDIUM -> ErrorResponse.ErrorSeverity.MEDIUM
            PdfOperationException.OperationSeverity.LOW -> ErrorResponse.ErrorSeverity.LOW
        }
    }

    private fun mapSecuritySeverityToErrorSeverity(severity: SecurityException.SecuritySeverity): ErrorResponse.ErrorSeverity {
        return when (severity) {
            SecurityException.SecuritySeverity.CRITICAL -> ErrorResponse.ErrorSeverity.CRITICAL
            SecurityException.SecuritySeverity.HIGH -> ErrorResponse.ErrorSeverity.HIGH
            SecurityException.SecuritySeverity.MEDIUM -> ErrorResponse.ErrorSeverity.MEDIUM
            SecurityException.SecuritySeverity.LOW -> ErrorResponse.ErrorSeverity.LOW
        }
    }

    // Additional helper methods for specific formatting scenarios
    private fun buildContextualInfo(context: Map<String, Any>, locale: Locale): String = 
        if (context.isNotEmpty()) {
            getLocalizedMessage("error.context.additional_info", "Additional context available.", locale)
        } else ""

    private fun categorizeForBusiness(category: ErrorResponse.ErrorCategory, locale: Locale): String =
        when (category) {
            ErrorResponse.ErrorCategory.VALIDATION_ERROR -> "Input Validation Issue"
            ErrorResponse.ErrorCategory.PROCESSING_ERROR -> "Processing Failure"
            ErrorResponse.ErrorCategory.RESOURCE_ERROR -> "Resource Constraint"
            ErrorResponse.ErrorCategory.SYSTEM_ERROR -> "System Issue"
            else -> "Technical Issue"
        }

    private fun formatValidationError(validationError: ErrorResponse.ValidationError, locale: Locale): String =
        "${validationError.field}: ${validationError.message}"

    private fun generateGenericSuggestions(errorResponse: ErrorResponse, locale: Locale): List<String> =
        listOf(getLocalizedMessage("suggestion.generic.contact_support", "Contact support for assistance", locale))

    private fun generateBulkRecommendations(errors: List<ErrorResponse>, locale: Locale): List<String> {
        val recommendations = mutableListOf<String>()
        val categories = errors.groupBy { it.category }
        
        if (categories.containsKey(ErrorResponse.ErrorCategory.VALIDATION_ERROR)) {
            recommendations.add("Review input validation for multiple items")
        }
        if (categories.containsKey(ErrorResponse.ErrorCategory.RESOURCE_ERROR)) {
            recommendations.add("Consider processing items in smaller batches")
        }
        
        return recommendations
    }

    private fun generateNextSteps(errors: List<ErrorResponse>, locale: Locale): List<String> =
        listOf(
            "Review individual error details",
            "Address validation errors first",
            "Contact support if issues persist"
        )

    private fun buildTechnicalDetails(exception: PdfOperationException, context: Map<String, Any>): Map<String, Any> =
        mapOf(
            "exception_type" to exception.javaClass.simpleName,
            "error_code" to exception.errorCode,
            "operation_type" to exception.operationType.name,
            "retryable" to exception.retryable,
            "recoverable" to exception.recoverable,
            "context" to context
        )

    private fun estimateOperationResolutionTime(exception: PdfOperationException): String =
        if (exception.retryable) "1-2 minutes" else "Requires manual intervention"

    private fun getOperationDocumentation(operationType: PdfOperationException.PdfOperationType): List<String> =
        listOf("/docs/operations/${operationType.name.lowercase()}")

    private fun buildSecurityDescription(exception: SecurityException, style: MessageStyle, locale: Locale): String =
        when (style) {
            MessageStyle.TECHNICAL -> exception.message ?: "Security violation"
            else -> getLocalizedMessage("error.security.generic.message", "Access denied for security reasons", locale)
        }

    private fun generateSecuritySuggestions(exception: SecurityException, style: MessageStyle, locale: Locale): List<String> =
        if (style == MessageStyle.TECHNICAL) {
            listOf("Check authentication tokens", "Verify user permissions", "Review security policies")
        } else {
            listOf(getLocalizedMessage("suggestion.security.contact_admin", "Contact your administrator", locale))
        }

    private fun generateSecurityUserActions(exception: SecurityException, locale: Locale): List<String> =
        listOf(
            getLocalizedMessage("action.security.check_permissions", "Verify you have the necessary permissions", locale),
            getLocalizedMessage("action.security.contact_admin", "Contact your administrator", locale)
        )

    private fun buildSecurityTechnicalDetails(exception: SecurityException): Map<String, Any> =
        mapOf(
            "exception_type" to exception.javaClass.simpleName,
            "severity" to exception.severity.name,
            "error_code" to exception.errorCode,
            "additional_context" to exception.additionalContext
        )

    private fun generateSecuritySupportReference(exception: SecurityException): String =
        "SEC-${exception.errorCode.take(3)}-${System.currentTimeMillis().toString().takeLast(8)}"

    private fun getSecurityDocumentation(): List<String> =
        listOf("/docs/security/access-control", "/docs/security/troubleshooting")

    private fun generateOperationUserActions(exception: PdfOperationException, context: Map<String, Any>, locale: Locale): List<String> {
        val actions = mutableListOf<String>()
        
        when (exception.operationType) {
            PdfOperationException.PdfOperationType.MERGE -> {
                actions.add("Check that all input files are accessible")
                actions.add("Verify file formats are supported")
            }
            PdfOperationException.PdfOperationType.SPLIT -> {
                actions.add("Verify the page range is within document bounds")
                actions.add("Check file permissions")
            }
            else -> {
                actions.add("Retry the operation")
                actions.add("Contact support if the issue persists")
            }
        }
        
        return actions
    }
}