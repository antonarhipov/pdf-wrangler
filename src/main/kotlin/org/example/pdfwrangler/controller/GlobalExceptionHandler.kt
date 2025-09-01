package org.example.pdfwrangler.controller

import org.example.pdfwrangler.dto.ErrorResponse
import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import org.example.pdfwrangler.service.SecurityAuditLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.NoHandlerFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import java.util.concurrent.TimeoutException

/**
 * Global exception handler for centralized error handling across all controllers.
 * Converts various exception types into standardized ErrorResponse objects.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val securityAuditLogger: SecurityAuditLogger
) {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handles PDF operation exceptions
     */
    @ExceptionHandler(PdfOperationException::class)
    fun handlePdfOperationException(
        ex: PdfOperationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val status = mapPdfExceptionToHttpStatus(ex)
        val errorResponse = ErrorResponse.fromPdfOperationException(
            exception = ex,
            status = status.value(),
            path = request.requestURI
        )

        // Log the exception with context
        logger.error("PDF operation failed: {} - {}", ex.errorCode, ex.message, ex)
        
        // Log detailed context for monitoring
        logger.info("PDF operation context: {}", ex.getLoggingContext())

        return ResponseEntity.status(status).body(errorResponse)
    }

    /**
     * Handles security exceptions
     */
    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(
        ex: SecurityException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val status = mapSecurityExceptionToHttpStatus(ex)
        val errorResponse = ErrorResponse.fromSecurityException(
            exception = ex,
            status = status.value(),
            path = request.requestURI
        )

        // Log security exception through audit logger
        securityAuditLogger.logSecurityException(ex)
        
        // Also log to application logger based on severity
        when (ex.severity) {
            SecurityException.SecuritySeverity.CRITICAL, 
            SecurityException.SecuritySeverity.HIGH -> {
                logger.error("Security exception: {} - {}", ex.errorCode, ex.message, ex)
            }
            SecurityException.SecuritySeverity.MEDIUM -> {
                logger.warn("Security exception: {} - {}", ex.errorCode, ex.message, ex)
            }
            SecurityException.SecuritySeverity.LOW -> {
                logger.info("Security exception: {} - {}", ex.errorCode, ex.message, ex)
            }
        }

        return ResponseEntity.status(status).body(errorResponse)
    }

    /**
     * Handles validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val validationErrors = ex.bindingResult.allErrors.map { error ->
            when (error) {
                is FieldError -> ErrorResponse.ValidationError(
                    field = error.field,
                    code = error.code ?: "VALIDATION_ERROR",
                    message = error.defaultMessage ?: "Invalid value",
                    rejectedValue = error.rejectedValue
                )
                else -> ErrorResponse.ValidationError(
                    field = error.objectName,
                    code = error.code ?: "VALIDATION_ERROR",
                    message = error.defaultMessage ?: "Validation failed"
                )
            }
        }

        val errorResponse = ErrorResponse.validationError(
            message = "Request validation failed",
            validationErrors = validationErrors,
            path = request.requestURI
        )

        logger.warn("Validation failed for request {}: {} errors", request.requestURI, validationErrors.size)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles bind exceptions (form data validation)
     */
    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val validationErrors = ex.bindingResult.fieldErrors.map { error ->
            ErrorResponse.ValidationError(
                field = error.field,
                code = error.code ?: "BIND_ERROR",
                message = error.defaultMessage ?: "Binding failed",
                rejectedValue = error.rejectedValue
            )
        }

        val errorResponse = ErrorResponse.validationError(
            message = "Request binding failed",
            validationErrors = validationErrors,
            path = request.requestURI
        )

        logger.warn("Bind exception for request {}: {} errors", request.requestURI, validationErrors.size)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles constraint violations
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val validationErrors = ex.constraintViolations.map { violation ->
            ErrorResponse.ValidationError(
                field = violation.propertyPath.toString(),
                code = "CONSTRAINT_VIOLATION",
                message = violation.message,
                rejectedValue = violation.invalidValue
            )
        }

        val errorResponse = ErrorResponse.validationError(
            message = "Constraint validation failed",
            validationErrors = validationErrors,
            path = request.requestURI
        )

        logger.warn("Constraint violation for request {}: {} violations", request.requestURI, validationErrors.size)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles file upload size exceeded exceptions
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        ex: MaxUploadSizeExceededException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.resourceError(
            message = "File upload size exceeds maximum allowed limit",
            resourceType = "UPLOAD_SIZE",
            currentUsage = ex.maxUploadSize,
            maxLimit = ex.maxUploadSize,
            path = request.requestURI
        )

        logger.warn("Upload size exceeded for request {}: max={}", request.requestURI, ex.maxUploadSize)
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse)
    }

    /**
     * Handles multipart upload exceptions
     */
    @ExceptionHandler(MultipartException::class)
    fun handleMultipartException(
        ex: MultipartException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.genericError(
            errorCode = "MULTIPART_ERROR",
            message = "File upload failed: ${ex.message}",
            severity = ErrorResponse.ErrorSeverity.MEDIUM,
            category = ErrorResponse.ErrorCategory.VALIDATION_ERROR,
            status = HttpStatus.BAD_REQUEST.value(),
            path = request.requestURI
        )

        logger.warn("Multipart exception for request {}: {}", request.requestURI, ex.message)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles timeout exceptions
     */
    @ExceptionHandler(TimeoutException::class)
    fun handleTimeoutException(
        ex: TimeoutException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.timeoutError(
            message = "Operation timed out: ${ex.message}",
            operation = extractOperationFromPath(request.requestURI),
            path = request.requestURI
        )

        logger.warn("Timeout exception for request {}: {}", request.requestURI, ex.message)
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse)
    }

    /**
     * Handles method not supported exceptions
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.genericError(
            errorCode = "METHOD_NOT_SUPPORTED",
            message = "HTTP method '${ex.method}' is not supported for this endpoint",
            severity = ErrorResponse.ErrorSeverity.LOW,
            category = ErrorResponse.ErrorCategory.VALIDATION_ERROR,
            status = HttpStatus.METHOD_NOT_ALLOWED.value(),
            path = request.requestURI,
            details = mapOf(
                "method" to (ex.method ?: "unknown"),
                "supportedMethods" to (ex.supportedMethods?.toList() ?: emptyList<String>())
            )
        )

        logger.warn("Method not supported for request {}: {} (supported: {})", 
            request.requestURI, ex.method, ex.supportedMethods)
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse)
    }

    /**
     * Handles media type not supported exceptions
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.genericError(
            errorCode = "MEDIA_TYPE_NOT_SUPPORTED",
            message = "Media type '${ex.contentType}' is not supported",
            severity = ErrorResponse.ErrorSeverity.LOW,
            category = ErrorResponse.ErrorCategory.VALIDATION_ERROR,
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            path = request.requestURI,
            details = mapOf(
                "providedMediaType" to (ex.contentType?.toString() ?: "unknown"),
                "supportedMediaTypes" to ex.supportedMediaTypes.map { it.toString() }
            )
        )

        logger.warn("Media type not supported for request {}: {} (supported: {})", 
            request.requestURI, ex.contentType, ex.supportedMediaTypes)
        
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse)
    }

    /**
     * Handles missing request parameter exceptions
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val validationErrors = listOf(
            ErrorResponse.ValidationError(
                field = ex.parameterName,
                code = "MISSING_PARAMETER",
                message = "Required parameter is missing"
            )
        )

        val errorResponse = ErrorResponse.validationError(
            message = "Required parameter '${ex.parameterName}' is missing",
            validationErrors = validationErrors,
            path = request.requestURI
        )

        logger.warn("Missing parameter for request {}: {}", request.requestURI, ex.parameterName)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles method argument type mismatch exceptions
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val validationErrors = listOf(
            ErrorResponse.ValidationError(
                field = ex.name,
                code = "TYPE_MISMATCH",
                message = "Invalid value type for parameter",
                rejectedValue = ex.value
            )
        )

        val errorResponse = ErrorResponse.validationError(
            message = "Parameter '${ex.name}' has invalid type",
            validationErrors = validationErrors,
            path = request.requestURI
        )

        logger.warn("Type mismatch for request {}: parameter={}, value={}, expectedType={}", 
            request.requestURI, ex.name, ex.value, ex.requiredType?.simpleName)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles JSON parsing exceptions
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.genericError(
            errorCode = "INVALID_JSON",
            message = "Invalid JSON format in request body",
            severity = ErrorResponse.ErrorSeverity.MEDIUM,
            category = ErrorResponse.ErrorCategory.VALIDATION_ERROR,
            status = HttpStatus.BAD_REQUEST.value(),
            path = request.requestURI
        )

        logger.warn("Invalid JSON for request {}: {}", request.requestURI, ex.message)
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handles 404 not found exceptions
     */
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFound(
        ex: NoHandlerFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.genericError(
            errorCode = "NOT_FOUND",
            message = "Requested resource not found",
            severity = ErrorResponse.ErrorSeverity.LOW,
            category = ErrorResponse.ErrorCategory.VALIDATION_ERROR,
            status = HttpStatus.NOT_FOUND.value(),
            path = request.requestURI
        )

        logger.warn("Resource not found: {}", request.requestURI)
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    /**
     * Handles all other exceptions
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val errorResponse = ErrorResponse.genericError(
            errorCode = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred",
            severity = ErrorResponse.ErrorSeverity.HIGH,
            category = ErrorResponse.ErrorCategory.SYSTEM_ERROR,
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            path = request.requestURI,
            details = mapOf("exceptionType" to ex.javaClass.simpleName)
        )

        logger.error("Unexpected exception for request {}: {}", request.requestURI, ex.message, ex)
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * Maps PdfOperationException to appropriate HTTP status code
     */
    private fun mapPdfExceptionToHttpStatus(ex: PdfOperationException): HttpStatus {
        return when (ex.determineErrorCategory()) {
            PdfOperationException.ErrorCategory.VALIDATION_ERROR -> HttpStatus.BAD_REQUEST
            PdfOperationException.ErrorCategory.PROCESSING_ERROR -> when (ex.severity) {
                PdfOperationException.OperationSeverity.LOW -> HttpStatus.OK // With warning
                PdfOperationException.OperationSeverity.MEDIUM -> HttpStatus.UNPROCESSABLE_ENTITY
                PdfOperationException.OperationSeverity.HIGH, 
                PdfOperationException.OperationSeverity.CRITICAL -> HttpStatus.INTERNAL_SERVER_ERROR
            }
            PdfOperationException.ErrorCategory.RESOURCE_ERROR -> HttpStatus.TOO_MANY_REQUESTS
            PdfOperationException.ErrorCategory.FORMAT_ERROR -> HttpStatus.BAD_REQUEST
            PdfOperationException.ErrorCategory.PERMISSION_ERROR -> HttpStatus.FORBIDDEN
            PdfOperationException.ErrorCategory.CONFIGURATION_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
            PdfOperationException.ErrorCategory.DEPENDENCY_ERROR -> HttpStatus.BAD_GATEWAY
            PdfOperationException.ErrorCategory.DATA_ERROR -> HttpStatus.BAD_REQUEST
            PdfOperationException.ErrorCategory.TIMEOUT_ERROR -> HttpStatus.REQUEST_TIMEOUT
            PdfOperationException.ErrorCategory.SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
        }
    }

    /**
     * Maps SecurityException to appropriate HTTP status code
     */
    private fun mapSecurityExceptionToHttpStatus(ex: SecurityException): HttpStatus {
        return when (ex.errorCode) {
            "FILE_VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST
            "PATH_TRAVERSAL_ERROR" -> HttpStatus.FORBIDDEN
            "MALICIOUS_CONTENT_ERROR" -> HttpStatus.FORBIDDEN
            "RATE_LIMIT_ERROR" -> HttpStatus.TOO_MANY_REQUESTS
            "RESOURCE_LIMIT_ERROR" -> HttpStatus.TOO_MANY_REQUESTS
            "AUTHENTICATION_ERROR" -> HttpStatus.UNAUTHORIZED
            "CRYPTOGRAPHIC_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR
            "SECURE_TEMP_FILE_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR
            "INPUT_VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST
            else -> HttpStatus.FORBIDDEN
        }
    }

    /**
     * Extracts operation type from request path
     */
    private fun extractOperationFromPath(path: String): String? {
        return when {
            path.contains("/merge") -> "MERGE"
            path.contains("/split") -> "SPLIT"
            path.contains("/convert") -> "CONVERSION"
            path.contains("/watermark") -> "WATERMARK"
            path.contains("/extract") -> "EXTRACTION"
            path.contains("/optimize") -> "OPTIMIZATION"
            else -> null
        }
    }
}