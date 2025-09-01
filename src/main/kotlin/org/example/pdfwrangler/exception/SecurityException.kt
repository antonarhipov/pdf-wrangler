package org.example.pdfwrangler.exception

/**
 * Base security exception class for all security-related errors in the PDF Wrangler application.
 * Provides a structured hierarchy for handling different types of security violations.
 */
open class SecurityException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String = "SECURITY_ERROR",
    val severity: SecuritySeverity = SecuritySeverity.HIGH,
    val additionalContext: Map<String, Any> = emptyMap()
) : RuntimeException(message, cause) {

    /**
     * Security severity levels for proper threat assessment and logging
     */
    enum class SecuritySeverity {
        LOW,        // Minor security issue, may not require immediate action
        MEDIUM,     // Moderate security concern, should be investigated
        HIGH,       // Serious security threat, requires immediate attention
        CRITICAL    // Critical security breach, requires emergency response
    }

    /**
     * Gets a detailed error report including context information
     */
    fun getDetailedErrorReport(): SecurityErrorReport {
        return SecurityErrorReport(
            errorCode = errorCode,
            message = message ?: "Unknown security error",
            severity = severity,
            exception = this::class.simpleName ?: "SecurityException",
            cause = cause?.message,
            timestamp = System.currentTimeMillis(),
            additionalContext = additionalContext
        )
    }

    data class SecurityErrorReport(
        val errorCode: String,
        val message: String,
        val severity: SecuritySeverity,
        val exception: String,
        val cause: String?,
        val timestamp: Long,
        val additionalContext: Map<String, Any>
    )
}

/**
 * Exception thrown when file validation fails due to security concerns
 */
class FileValidationSecurityException(
    message: String,
    cause: Throwable? = null,
    val filename: String? = null,
    val fileSize: Long? = null,
    val contentType: String? = null,
    val validationFailureType: FileValidationFailureType = FileValidationFailureType.UNKNOWN
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "FILE_VALIDATION_ERROR",
    severity = when (validationFailureType) {
        FileValidationFailureType.MALICIOUS_CONTENT -> SecuritySeverity.CRITICAL
        FileValidationFailureType.SUSPICIOUS_SIGNATURE -> SecuritySeverity.HIGH
        FileValidationFailureType.INVALID_FORMAT -> SecuritySeverity.MEDIUM
        FileValidationFailureType.SIZE_VIOLATION -> SecuritySeverity.MEDIUM
        FileValidationFailureType.TYPE_MISMATCH -> SecuritySeverity.MEDIUM
        FileValidationFailureType.UNKNOWN -> SecuritySeverity.HIGH
    },
    additionalContext = buildMap {
        filename?.let { put("filename", it) }
        fileSize?.let { put("fileSize", it) }
        contentType?.let { put("contentType", it) }
        put("validationFailureType", validationFailureType.name)
    }
) {
    enum class FileValidationFailureType {
        MALICIOUS_CONTENT,      // File contains malicious content
        SUSPICIOUS_SIGNATURE,   // File signature doesn't match extension
        INVALID_FORMAT,         // File format is not supported or corrupted
        SIZE_VIOLATION,         // File size exceeds limits
        TYPE_MISMATCH,          // MIME type doesn't match file extension
        UNKNOWN                 // Unknown validation failure
    }
}

/**
 * Exception thrown when path traversal attacks are detected
 */
class PathTraversalSecurityException(
    message: String,
    cause: Throwable? = null,
    val attemptedPath: String? = null,
    val basePath: String? = null,
    val attackPattern: String? = null
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "PATH_TRAVERSAL_ERROR",
    severity = SecuritySeverity.HIGH,
    additionalContext = buildMap {
        attemptedPath?.let { put("attemptedPath", it) }
        basePath?.let { put("basePath", it) }
        attackPattern?.let { put("attackPattern", it) }
    }
)

/**
 * Exception thrown when malicious content is detected in files
 */
class MaliciousContentSecurityException(
    message: String,
    cause: Throwable? = null,
    val filename: String? = null,
    val threatType: String? = null,
    val confidence: Double = 1.0,
    val detectionMethod: String? = null
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "MALICIOUS_CONTENT_ERROR",
    severity = when {
        confidence >= 0.9 -> SecuritySeverity.CRITICAL
        confidence >= 0.7 -> SecuritySeverity.HIGH
        else -> SecuritySeverity.MEDIUM
    },
    additionalContext = buildMap {
        filename?.let { put("filename", it) }
        threatType?.let { put("threatType", it) }
        put("confidence", confidence)
        detectionMethod?.let { put("detectionMethod", it) }
    }
)

/**
 * Exception thrown when resource limits are exceeded
 */
class ResourceLimitSecurityException(
    message: String,
    cause: Throwable? = null,
    val limitType: ResourceLimitType = ResourceLimitType.UNKNOWN,
    val currentValue: Long? = null,
    val maxValue: Long? = null,
    val resourceIdentifier: String? = null
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "RESOURCE_LIMIT_ERROR",
    severity = when (limitType) {
        ResourceLimitType.MEMORY_USAGE -> SecuritySeverity.HIGH
        ResourceLimitType.PROCESSING_TIME -> SecuritySeverity.MEDIUM
        ResourceLimitType.FILE_SIZE -> SecuritySeverity.MEDIUM
        ResourceLimitType.CONCURRENT_OPERATIONS -> SecuritySeverity.HIGH
        ResourceLimitType.RATE_LIMIT -> SecuritySeverity.MEDIUM
        ResourceLimitType.DISK_USAGE -> SecuritySeverity.HIGH
        ResourceLimitType.UNKNOWN -> SecuritySeverity.MEDIUM
    },
    additionalContext = buildMap {
        put("limitType", limitType.name)
        currentValue?.let { put("currentValue", it) }
        maxValue?.let { put("maxValue", it) }
        resourceIdentifier?.let { put("resourceIdentifier", it) }
    }
) {
    enum class ResourceLimitType {
        MEMORY_USAGE,           // Memory usage exceeded
        PROCESSING_TIME,        // Processing time exceeded
        FILE_SIZE,              // File size limit exceeded
        CONCURRENT_OPERATIONS,  // Too many concurrent operations
        RATE_LIMIT,            // Rate limit exceeded
        DISK_USAGE,            // Disk usage limit exceeded
        UNKNOWN                // Unknown resource limit
    }
}

/**
 * Exception thrown when rate limiting is triggered
 */
class RateLimitSecurityException(
    message: String,
    cause: Throwable? = null,
    val clientIdentifier: String? = null,
    val rateLimitType: RateLimitType = RateLimitType.REQUEST_RATE,
    val currentCount: Int? = null,
    val maxAllowed: Int? = null,
    val resetTimeSeconds: Long? = null
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "RATE_LIMIT_ERROR",
    severity = SecuritySeverity.MEDIUM,
    additionalContext = buildMap {
        clientIdentifier?.let { put("clientIdentifier", it) }
        put("rateLimitType", rateLimitType.name)
        currentCount?.let { put("currentCount", it) }
        maxAllowed?.let { put("maxAllowed", it) }
        resetTimeSeconds?.let { put("resetTimeSeconds", it) }
    }
) {
    enum class RateLimitType {
        REQUEST_RATE,       // Request rate limit
        UPLOAD_RATE,        // Upload rate limit
        OPERATION_RATE,     // Operation rate limit
        BANDWIDTH_RATE      // Bandwidth rate limit
    }
}

/**
 * Exception thrown when authentication or authorization fails
 */
class AuthenticationSecurityException(
    message: String,
    cause: Throwable? = null,
    val authenticationMethod: String? = null,
    val failureReason: AuthFailureReason = AuthFailureReason.UNKNOWN
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "AUTHENTICATION_ERROR",
    severity = when (failureReason) {
        AuthFailureReason.INVALID_CREDENTIALS -> SecuritySeverity.MEDIUM
        AuthFailureReason.EXPIRED_TOKEN -> SecuritySeverity.LOW
        AuthFailureReason.INSUFFICIENT_PRIVILEGES -> SecuritySeverity.MEDIUM
        AuthFailureReason.ACCOUNT_LOCKED -> SecuritySeverity.HIGH
        AuthFailureReason.SUSPICIOUS_ACTIVITY -> SecuritySeverity.HIGH
        AuthFailureReason.UNKNOWN -> SecuritySeverity.MEDIUM
    },
    additionalContext = buildMap {
        authenticationMethod?.let { put("authenticationMethod", it) }
        put("failureReason", failureReason.name)
    }
) {
    enum class AuthFailureReason {
        INVALID_CREDENTIALS,        // Invalid username/password
        EXPIRED_TOKEN,             // Authentication token expired
        INSUFFICIENT_PRIVILEGES,    // User lacks required permissions
        ACCOUNT_LOCKED,            // Account is locked due to security
        SUSPICIOUS_ACTIVITY,       // Suspicious authentication activity
        UNKNOWN                    // Unknown authentication failure
    }
}

/**
 * Exception thrown when encryption/decryption operations fail
 */
class CryptographicSecurityException(
    message: String,
    cause: Throwable? = null,
    val operation: CryptoOperation = CryptoOperation.UNKNOWN,
    val algorithm: String? = null
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "CRYPTOGRAPHIC_ERROR",
    severity = SecuritySeverity.HIGH,
    additionalContext = buildMap {
        put("operation", operation.name)
        algorithm?.let { put("algorithm", it) }
    }
) {
    enum class CryptoOperation {
        ENCRYPTION,     // Encryption operation failed
        DECRYPTION,     // Decryption operation failed
        HASHING,        // Hashing operation failed
        SIGNATURE,      // Digital signature operation failed
        KEY_GENERATION, // Key generation failed
        UNKNOWN         // Unknown cryptographic operation
    }
}

/**
 * Exception thrown when secure temporary file operations fail
 */
class SecureTempFileSecurityException(
    message: String,
    cause: Throwable? = null,
    val operation: TempFileOperation = TempFileOperation.UNKNOWN,
    val fileId: String? = null,
    val filePath: String? = null
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "SECURE_TEMP_FILE_ERROR",
    severity = when (operation) {
        TempFileOperation.PERMISSION_VIOLATION -> SecuritySeverity.HIGH
        TempFileOperation.ISOLATION_FAILURE -> SecuritySeverity.HIGH
        TempFileOperation.CLEANUP_FAILURE -> SecuritySeverity.MEDIUM
        TempFileOperation.ACCESS_VIOLATION -> SecuritySeverity.HIGH
        TempFileOperation.UNKNOWN -> SecuritySeverity.MEDIUM
    },
    additionalContext = buildMap {
        put("operation", operation.name)
        fileId?.let { put("fileId", it) }
        filePath?.let { put("filePath", it) }
    }
) {
    enum class TempFileOperation {
        PERMISSION_VIOLATION,   // File permissions were compromised
        ISOLATION_FAILURE,      // File isolation failed
        CLEANUP_FAILURE,        // Secure cleanup failed
        ACCESS_VIOLATION,       // Unauthorized access attempt
        UNKNOWN                 // Unknown temp file operation
    }
}

/**
 * Exception thrown when input validation fails due to security concerns
 */
class InputValidationSecurityException(
    message: String,
    cause: Throwable? = null,
    val inputField: String? = null,
    val inputValue: String? = null,
    val violationType: InputViolationType = InputViolationType.UNKNOWN
) : SecurityException(
    message = message,
    cause = cause,
    errorCode = "INPUT_VALIDATION_ERROR",
    severity = when (violationType) {
        InputViolationType.XSS_ATTEMPT -> SecuritySeverity.HIGH
        InputViolationType.SQL_INJECTION -> SecuritySeverity.CRITICAL
        InputViolationType.COMMAND_INJECTION -> SecuritySeverity.CRITICAL
        InputViolationType.SCRIPT_INJECTION -> SecuritySeverity.HIGH
        InputViolationType.INVALID_FORMAT -> SecuritySeverity.LOW
        InputViolationType.UNKNOWN -> SecuritySeverity.MEDIUM
    },
    additionalContext = buildMap {
        inputField?.let { put("inputField", it) }
        inputValue?.let { put("inputValue", it.take(100)) } // Limit logged input value
        put("violationType", violationType.name)
    }
) {
    enum class InputViolationType {
        XSS_ATTEMPT,        // Cross-site scripting attempt
        SQL_INJECTION,      // SQL injection attempt
        COMMAND_INJECTION,  // Command injection attempt
        SCRIPT_INJECTION,   // Script injection attempt
        INVALID_FORMAT,     // Invalid input format
        UNKNOWN             // Unknown input violation
    }
}