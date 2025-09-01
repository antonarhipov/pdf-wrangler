package org.example.pdfwrangler.exception

import java.time.Instant

/**
 * Base exception class for all PDF operation-related errors in the PDF Wrangler application.
 * Provides a structured hierarchy for handling different types of PDF processing failures.
 */
open class PdfOperationException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String = "PDF_OPERATION_ERROR",
    val severity: OperationSeverity = OperationSeverity.MEDIUM,
    val operationType: PdfOperationType = PdfOperationType.UNKNOWN,
    val operationContext: Map<String, Any> = emptyMap(),
    val recoverable: Boolean = false,
    val retryable: Boolean = false
) : RuntimeException(message, cause) {

    /**
     * Operation severity levels for proper error classification and handling
     */
    enum class OperationSeverity {
        LOW,        // Minor issue, operation may continue with warnings
        MEDIUM,     // Moderate issue, operation should be retried or alternative approach used
        HIGH,       // Serious issue, operation failed but system remains stable
        CRITICAL    // Critical failure, may affect system stability or data integrity
    }

    /**
     * Types of PDF operations for better error categorization
     */
    enum class PdfOperationType {
        MERGE,              // PDF merge operations
        SPLIT,              // PDF split operations
        CONVERSION,         // Format conversion operations
        VALIDATION,         // File validation operations
        EXTRACTION,         // Content extraction operations
        MANIPULATION,       // Page manipulation operations
        WATERMARKING,       // Watermarking operations
        OPTIMIZATION,       // PDF optimization operations
        ANALYSIS,           // Document analysis operations
        METADATA,           // Metadata operations
        SECURITY,           // Security-related operations
        IO,                 // Input/output operations
        PARSING,            // PDF parsing operations
        RENDERING,          // PDF rendering operations
        UNKNOWN             // Unknown operation type
    }

    /**
     * Error categories for systematic error handling
     */
    enum class ErrorCategory {
        VALIDATION_ERROR,       // Input validation failures
        PROCESSING_ERROR,       // Core processing failures
        RESOURCE_ERROR,         // Resource-related errors (memory, disk, etc.)
        FORMAT_ERROR,           // File format or structure errors
        PERMISSION_ERROR,       // Permission or access errors
        CONFIGURATION_ERROR,    // Configuration or setup errors
        DEPENDENCY_ERROR,       // External dependency errors
        DATA_ERROR,            // Data integrity or corruption errors
        TIMEOUT_ERROR,         // Operation timeout errors
        SYSTEM_ERROR           // System-level errors
    }

    val timestamp: Instant = Instant.now()
    val operationId: String = generateOperationId()

    /**
     * Gets a detailed error report including all context information
     */
    fun getDetailedErrorReport(): PdfOperationErrorReport {
        return PdfOperationErrorReport(
            operationId = operationId,
            timestamp = timestamp,
            errorCode = errorCode,
            message = message ?: "Unknown PDF operation error",
            severity = severity,
            operationType = operationType,
            exception = this::class.simpleName ?: "PdfOperationException",
            cause = cause?.message,
            stackTrace = stackTrace?.take(10)?.map { it.toString() } ?: emptyList(),
            operationContext = operationContext,
            recoverable = recoverable,
            retryable = retryable,
            category = determineErrorCategory()
        )
    }

    /**
     * Determines the error category based on the exception type and context
     */
    open fun determineErrorCategory(): ErrorCategory {
        return when {
            errorCode.contains("VALIDATION") -> ErrorCategory.VALIDATION_ERROR
            errorCode.contains("PROCESSING") -> ErrorCategory.PROCESSING_ERROR
            errorCode.contains("RESOURCE") -> ErrorCategory.RESOURCE_ERROR
            errorCode.contains("FORMAT") -> ErrorCategory.FORMAT_ERROR
            errorCode.contains("PERMISSION") -> ErrorCategory.PERMISSION_ERROR
            errorCode.contains("CONFIGURATION") -> ErrorCategory.CONFIGURATION_ERROR
            errorCode.contains("DEPENDENCY") -> ErrorCategory.DEPENDENCY_ERROR
            errorCode.contains("TIMEOUT") -> ErrorCategory.TIMEOUT_ERROR
            errorCode.contains("DATA") -> ErrorCategory.DATA_ERROR
            else -> ErrorCategory.SYSTEM_ERROR
        }
    }

    /**
     * Checks if this error should trigger a retry
     */
    fun shouldRetry(): Boolean {
        return retryable && (severity == OperationSeverity.LOW || severity == OperationSeverity.MEDIUM)
    }

    /**
     * Checks if this error can be recovered from
     */
    fun canRecover(): Boolean {
        return recoverable
    }

    /**
     * Gets context information for logging and monitoring
     */
    fun getLoggingContext(): Map<String, Any> {
        return mapOf(
            "operationId" to operationId,
            "timestamp" to timestamp.toString(),
            "errorCode" to errorCode,
            "severity" to severity.name,
            "operationType" to operationType.name,
            "category" to determineErrorCategory().name,
            "recoverable" to recoverable,
            "retryable" to retryable,
            "hasContextData" to operationContext.isNotEmpty(),
            "contextKeys" to operationContext.keys.toList()
        ) + operationContext
    }

    private fun generateOperationId(): String {
        return "pdf-op-${System.currentTimeMillis()}-${hashCode().toString(16)}"
    }

    data class PdfOperationErrorReport(
        val operationId: String,
        val timestamp: Instant,
        val errorCode: String,
        val message: String,
        val severity: OperationSeverity,
        val operationType: PdfOperationType,
        val exception: String,
        val cause: String?,
        val stackTrace: List<String>,
        val operationContext: Map<String, Any>,
        val recoverable: Boolean,
        val retryable: Boolean,
        val category: ErrorCategory
    )
}

/**
 * Exception for PDF file validation errors
 */
class PdfValidationException(
    message: String,
    cause: Throwable? = null,
    val filename: String? = null,
    val validationRule: String? = null,
    val expectedValue: String? = null,
    val actualValue: String? = null
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_VALIDATION_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.VALIDATION,
    operationContext = buildMap {
        filename?.let { put("filename", it) }
        validationRule?.let { put("validationRule", it) }
        expectedValue?.let { put("expectedValue", it) }
        actualValue?.let { put("actualValue", it) }
    },
    recoverable = false,
    retryable = false
)

/**
 * Exception for PDF parsing and structure errors
 */
class PdfParsingException(
    message: String,
    cause: Throwable? = null,
    val filename: String? = null,
    val pageNumber: Int? = null,
    val objectId: String? = null,
    val parsingStage: String? = null
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_PARSING_ERROR",
    severity = OperationSeverity.HIGH,
    operationType = PdfOperationType.PARSING,
    operationContext = buildMap {
        filename?.let { put("filename", it) }
        pageNumber?.let { put("pageNumber", it) }
        objectId?.let { put("objectId", it) }
        parsingStage?.let { put("parsingStage", it) }
    },
    recoverable = false,
    retryable = false
) {
    override fun determineErrorCategory(): ErrorCategory = ErrorCategory.FORMAT_ERROR
}

/**
 * Exception for PDF processing errors during operations
 */
class PdfProcessingException(
    message: String,
    cause: Throwable? = null,
    operationType: PdfOperationType = PdfOperationType.UNKNOWN,
    val filename: String? = null,
    val processingStage: String? = null,
    val progressPercentage: Int? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_PROCESSING_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = operationType,
    operationContext = buildMap {
        filename?.let { put("filename", it) }
        processingStage?.let { put("processingStage", it) }
        progressPercentage?.let { put("progressPercentage", it) }
    },
    recoverable = recoverable,
    retryable = retryable
) {
    override fun determineErrorCategory(): ErrorCategory = ErrorCategory.PROCESSING_ERROR
}

/**
 * Exception for PDF I/O related errors
 */
class PdfIOException(
    message: String,
    cause: Throwable? = null,
    val filename: String? = null,
    val operation: IOOperation = IOOperation.UNKNOWN,
    val bytesProcessed: Long? = null
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_IO_ERROR",
    severity = OperationSeverity.HIGH,
    operationType = PdfOperationType.IO,
    operationContext = buildMap {
        filename?.let { put("filename", it) }
        put("ioOperation", operation.name)
        bytesProcessed?.let { put("bytesProcessed", it) }
    },
    recoverable = true,
    retryable = true
) {
    
    enum class IOOperation {
        READ, WRITE, DELETE, MOVE, COPY, CREATE, UNKNOWN
    }

    override fun determineErrorCategory(): ErrorCategory = ErrorCategory.RESOURCE_ERROR
}

/**
 * Exception for PDF resource-related errors (memory, disk space, etc.)
 */
class PdfResourceException(
    message: String,
    cause: Throwable? = null,
    val resourceType: ResourceType,
    val currentUsage: Long? = null,
    val maxLimit: Long? = null,
    val filename: String? = null
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_RESOURCE_ERROR",
    severity = when (resourceType) {
        ResourceType.MEMORY -> OperationSeverity.HIGH
        ResourceType.DISK_SPACE -> OperationSeverity.MEDIUM
        ResourceType.PROCESSING_TIME -> OperationSeverity.MEDIUM
        ResourceType.FILE_HANDLES -> OperationSeverity.HIGH
        ResourceType.NETWORK -> OperationSeverity.MEDIUM
    },
    operationType = PdfOperationType.UNKNOWN,
    operationContext = buildMap {
        put("resourceType", resourceType.name)
        currentUsage?.let { put("currentUsage", it) }
        maxLimit?.let { put("maxLimit", it) }
        filename?.let { put("filename", it) }
    },
    recoverable = true,
    retryable = resourceType != ResourceType.MEMORY
) {
    
    enum class ResourceType {
        MEMORY, DISK_SPACE, PROCESSING_TIME, FILE_HANDLES, NETWORK
    }

    override fun determineErrorCategory(): ErrorCategory = ErrorCategory.RESOURCE_ERROR
}

/**
 * Exception for PDF format compatibility and version errors
 */
class PdfFormatException(
    message: String,
    cause: Throwable? = null,
    val filename: String? = null,
    val detectedVersion: String? = null,
    val requiredVersion: String? = null,
    val formatIssue: String? = null
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_FORMAT_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.VALIDATION,
    operationContext = buildMap {
        filename?.let { put("filename", it) }
        detectedVersion?.let { put("detectedVersion", it) }
        requiredVersion?.let { put("requiredVersion", it) }
        formatIssue?.let { put("formatIssue", it) }
    },
    recoverable = false,
    retryable = false
) {
    override fun determineErrorCategory(): ErrorCategory = ErrorCategory.FORMAT_ERROR
}