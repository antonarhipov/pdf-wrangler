package org.example.pdfwrangler.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Standardized error response DTO for API endpoints.
 * Provides consistent error format across all operations with proper error codes and messages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    /**
     * Unique error code for the specific error type
     */
    @JsonProperty("error_code")
    val errorCode: String,

    /**
     * Human-readable error message
     */
    @JsonProperty("message")
    val message: String,

    /**
     * Error severity level
     */
    @JsonProperty("severity")
    val severity: ErrorSeverity,

    /**
     * Category of the error for systematic handling
     */
    @JsonProperty("category")
    val category: ErrorCategory,

    /**
     * Timestamp when the error occurred
     */
    @JsonProperty("timestamp")
    val timestamp: String = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),

    /**
     * Unique identifier for tracking this specific error occurrence
     */
    @JsonProperty("trace_id")
    val traceId: String? = null,

    /**
     * Operation that was being performed when the error occurred
     */
    @JsonProperty("operation")
    val operation: String? = null,

    /**
     * HTTP status code associated with this error
     */
    @JsonProperty("status")
    val status: Int? = null,

    /**
     * Request path where the error occurred
     */
    @JsonProperty("path")
    val path: String? = null,

    /**
     * Whether this error can be retried
     */
    @JsonProperty("retryable")
    val retryable: Boolean = false,

    /**
     * Whether this error can be recovered from
     */
    @JsonProperty("recoverable")
    val recoverable: Boolean = false,

    /**
     * Suggested next steps or actions for the client
     */
    @JsonProperty("suggested_action")
    val suggestedAction: String? = null,

    /**
     * Additional context information about the error
     */
    @JsonProperty("details")
    val details: Map<String, Any>? = null,

    /**
     * Validation errors for form/input validation failures
     */
    @JsonProperty("validation_errors")
    val validationErrors: List<ValidationError>? = null,

    /**
     * Related error information or nested errors
     */
    @JsonProperty("related_errors")
    val relatedErrors: List<ErrorResponse>? = null
) {

    enum class ErrorSeverity {
        INFO, LOW, MEDIUM, HIGH, CRITICAL
    }

    enum class ErrorCategory {
        VALIDATION_ERROR,
        PROCESSING_ERROR,
        RESOURCE_ERROR,
        FORMAT_ERROR,
        PERMISSION_ERROR,
        CONFIGURATION_ERROR,
        DEPENDENCY_ERROR,
        DATA_ERROR,
        TIMEOUT_ERROR,
        SYSTEM_ERROR,
        SECURITY_ERROR,
        BUSINESS_LOGIC_ERROR,
        EXTERNAL_SERVICE_ERROR
    }

    data class ValidationError(
        @JsonProperty("field")
        val field: String,

        @JsonProperty("code")
        val code: String,

        @JsonProperty("message")
        val message: String,

        @JsonProperty("rejected_value")
        val rejectedValue: Any? = null
    )

    companion object {
        /**
         * Creates an ErrorResponse from a PdfOperationException
         */
        fun fromPdfOperationException(
            exception: PdfOperationException,
            status: Int? = null,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = exception.errorCode,
                message = exception.message ?: "PDF operation failed",
                severity = mapPdfSeverity(exception.severity),
                category = mapPdfCategory(exception.determineErrorCategory()),
                traceId = exception.operationId,
                operation = exception.operationType.name,
                status = status,
                path = path,
                retryable = exception.retryable,
                recoverable = exception.recoverable,
                suggestedAction = generateSuggestedAction(exception),
                details = exception.operationContext.takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Creates an ErrorResponse from a SecurityException
         */
        fun fromSecurityException(
            exception: SecurityException,
            status: Int? = null,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = exception.errorCode,
                message = exception.message ?: "Security error occurred",
                severity = mapSecuritySeverity(exception.severity),
                category = ErrorCategory.SECURITY_ERROR,
                traceId = generateTraceId(),
                operation = "SECURITY_CHECK",
                status = status,
                path = path,
                retryable = false,
                recoverable = false,
                suggestedAction = generateSecuritySuggestedAction(exception),
                details = exception.additionalContext.takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Creates a validation error response
         */
        fun validationError(
            message: String = "Input validation failed",
            validationErrors: List<ValidationError>,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = "VALIDATION_ERROR",
                message = message,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.VALIDATION_ERROR,
                status = 400,
                path = path,
                retryable = false,
                recoverable = true,
                suggestedAction = "Please correct the validation errors and try again",
                validationErrors = validationErrors
            )
        }

        /**
         * Creates a generic error response
         */
        fun genericError(
            errorCode: String,
            message: String,
            severity: ErrorSeverity = ErrorSeverity.MEDIUM,
            category: ErrorCategory = ErrorCategory.SYSTEM_ERROR,
            status: Int? = null,
            path: String? = null,
            details: Map<String, Any>? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = errorCode,
                message = message,
                severity = severity,
                category = category,
                status = status,
                path = path,
                details = details,
                suggestedAction = generateGenericSuggestedAction(category)
            )
        }

        /**
         * Creates a resource error response
         */
        fun resourceError(
            message: String,
            resourceType: String,
            currentUsage: Long? = null,
            maxLimit: Long? = null,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = "RESOURCE_LIMIT_EXCEEDED",
                message = message,
                severity = ErrorSeverity.HIGH,
                category = ErrorCategory.RESOURCE_ERROR,
                status = 429,
                path = path,
                retryable = true,
                recoverable = true,
                suggestedAction = "Please try again later or reduce resource usage",
                details = buildMap {
                    put("resourceType", resourceType)
                    currentUsage?.let { put("currentUsage", it) }
                    maxLimit?.let { put("maxLimit", it) }
                }
            )
        }

        /**
         * Creates a timeout error response
         */
        fun timeoutError(
            message: String = "Operation timed out",
            operation: String? = null,
            timeoutSeconds: Long? = null,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = "OPERATION_TIMEOUT",
                message = message,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.TIMEOUT_ERROR,
                status = 408,
                path = path,
                retryable = true,
                recoverable = true,
                operation = operation,
                suggestedAction = "The operation took too long to complete. Please try again or use smaller files",
                details = timeoutSeconds?.let { mapOf("timeoutSeconds" to it) }
            )
        }

        /**
         * Creates an external service error response
         */
        fun externalServiceError(
            serviceName: String,
            message: String = "External service error",
            status: Int? = null,
            path: String? = null
        ): ErrorResponse {
            return ErrorResponse(
                errorCode = "EXTERNAL_SERVICE_ERROR",
                message = message,
                severity = ErrorSeverity.HIGH,
                category = ErrorCategory.EXTERNAL_SERVICE_ERROR,
                status = status ?: 502,
                path = path,
                retryable = true,
                recoverable = true,
                suggestedAction = "There was an issue with an external service. Please try again later",
                details = mapOf("serviceName" to serviceName)
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

        private fun mapPdfCategory(category: PdfOperationException.ErrorCategory): ErrorCategory {
            return when (category) {
                PdfOperationException.ErrorCategory.VALIDATION_ERROR -> ErrorCategory.VALIDATION_ERROR
                PdfOperationException.ErrorCategory.PROCESSING_ERROR -> ErrorCategory.PROCESSING_ERROR
                PdfOperationException.ErrorCategory.RESOURCE_ERROR -> ErrorCategory.RESOURCE_ERROR
                PdfOperationException.ErrorCategory.FORMAT_ERROR -> ErrorCategory.FORMAT_ERROR
                PdfOperationException.ErrorCategory.PERMISSION_ERROR -> ErrorCategory.PERMISSION_ERROR
                PdfOperationException.ErrorCategory.CONFIGURATION_ERROR -> ErrorCategory.CONFIGURATION_ERROR
                PdfOperationException.ErrorCategory.DEPENDENCY_ERROR -> ErrorCategory.DEPENDENCY_ERROR
                PdfOperationException.ErrorCategory.DATA_ERROR -> ErrorCategory.DATA_ERROR
                PdfOperationException.ErrorCategory.TIMEOUT_ERROR -> ErrorCategory.TIMEOUT_ERROR
                PdfOperationException.ErrorCategory.SYSTEM_ERROR -> ErrorCategory.SYSTEM_ERROR
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

        private fun generateSuggestedAction(exception: PdfOperationException): String {
            return when {
                exception.retryable -> "This operation can be retried. Please try again"
                exception.recoverable -> "Please check your input and try again with different parameters"
                exception.operationType == PdfOperationException.PdfOperationType.VALIDATION -> 
                    "Please ensure your PDF file is valid and not corrupted"
                exception.determineErrorCategory() == PdfOperationException.ErrorCategory.RESOURCE_ERROR -> 
                    "Please try with smaller files or try again later"
                exception.determineErrorCategory() == PdfOperationException.ErrorCategory.FORMAT_ERROR -> 
                    "Please ensure your file is a valid PDF format"
                else -> "Please contact support if the problem persists"
            }
        }

        private fun generateSecuritySuggestedAction(exception: SecurityException): String {
            return when (exception.errorCode) {
                "FILE_VALIDATION_ERROR" -> "Please ensure your file meets security requirements"
                "PATH_TRAVERSAL_ERROR" -> "Please use valid file paths without navigation attempts"
                "MALICIOUS_CONTENT_ERROR" -> "The file contains suspicious content and cannot be processed"
                "RATE_LIMIT_ERROR" -> "You have exceeded rate limits. Please wait before trying again"
                "RESOURCE_LIMIT_ERROR" -> "Resource limits have been exceeded. Please try with smaller files"
                else -> "Security policy violation. Please ensure your request complies with security requirements"
            }
        }

        private fun generateGenericSuggestedAction(category: ErrorCategory): String {
            return when (category) {
                ErrorCategory.VALIDATION_ERROR -> "Please correct the input validation errors"
                ErrorCategory.PROCESSING_ERROR -> "An error occurred during processing. Please try again"
                ErrorCategory.RESOURCE_ERROR -> "Resource limits exceeded. Please try with smaller files or try again later"
                ErrorCategory.FORMAT_ERROR -> "Please ensure your file format is supported"
                ErrorCategory.PERMISSION_ERROR -> "You don't have permission to perform this operation"
                ErrorCategory.CONFIGURATION_ERROR -> "System configuration error. Please contact support"
                ErrorCategory.DEPENDENCY_ERROR -> "External dependency error. Please try again later"
                ErrorCategory.DATA_ERROR -> "Data integrity error. Please check your input"
                ErrorCategory.TIMEOUT_ERROR -> "Operation timed out. Please try again or use smaller files"
                ErrorCategory.SECURITY_ERROR -> "Security policy violation"
                ErrorCategory.BUSINESS_LOGIC_ERROR -> "Business rule validation failed"
                ErrorCategory.EXTERNAL_SERVICE_ERROR -> "External service error. Please try again later"
                ErrorCategory.SYSTEM_ERROR -> "System error occurred. Please try again or contact support"
            }
        }

        private fun generateTraceId(): String {
            return "trace-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"
        }
    }
}

/**
 * Builder class for creating complex error responses
 */
class ErrorResponseBuilder {
    private var errorCode: String = "UNKNOWN_ERROR"
    private var message: String = "An error occurred"
    private var severity: ErrorResponse.ErrorSeverity = ErrorResponse.ErrorSeverity.MEDIUM
    private var category: ErrorResponse.ErrorCategory = ErrorResponse.ErrorCategory.SYSTEM_ERROR
    private var traceId: String? = null
    private var operation: String? = null
    private var status: Int? = null
    private var path: String? = null
    private var retryable: Boolean = false
    private var recoverable: Boolean = false
    private var suggestedAction: String? = null
    private var details: Map<String, Any>? = null
    private var validationErrors: List<ErrorResponse.ValidationError>? = null
    private var relatedErrors: List<ErrorResponse>? = null

    fun errorCode(code: String) = apply { this.errorCode = code }
    fun message(msg: String) = apply { this.message = msg }
    fun severity(sev: ErrorResponse.ErrorSeverity) = apply { this.severity = sev }
    fun category(cat: ErrorResponse.ErrorCategory) = apply { this.category = cat }
    fun traceId(id: String) = apply { this.traceId = id }
    fun operation(op: String) = apply { this.operation = op }
    fun status(st: Int) = apply { this.status = st }
    fun path(p: String) = apply { this.path = p }
    fun retryable(retry: Boolean) = apply { this.retryable = retry }
    fun recoverable(recover: Boolean) = apply { this.recoverable = recover }
    fun suggestedAction(action: String) = apply { this.suggestedAction = action }
    fun details(det: Map<String, Any>) = apply { this.details = det }
    fun validationErrors(errors: List<ErrorResponse.ValidationError>) = apply { this.validationErrors = errors }
    fun relatedErrors(errors: List<ErrorResponse>) = apply { this.relatedErrors = errors }

    fun build(): ErrorResponse {
        return ErrorResponse(
            errorCode = errorCode,
            message = message,
            severity = severity,
            category = category,
            traceId = traceId,
            operation = operation,
            status = status,
            path = path,
            retryable = retryable,
            recoverable = recoverable,
            suggestedAction = suggestedAction,
            details = details,
            validationErrors = validationErrors,
            relatedErrors = relatedErrors
        )
    }
}