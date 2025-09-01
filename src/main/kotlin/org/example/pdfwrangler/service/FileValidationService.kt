package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.*

/**
 * Service for comprehensive file validation including type checking, format validation,
 * and security threat detection for uploaded files.
 */
@Service
class FileValidationService {

    private val logger = LoggerFactory.getLogger(FileValidationService::class.java)

    companion object {
        // PDF file signature (magic bytes)
        private val PDF_SIGNATURE = byteArrayOf(0x25, 0x50, 0x44, 0x46) // %PDF

        // Maximum file size (100MB as configured in application.properties)
        private const val MAX_FILE_SIZE = 100L * 1024 * 1024

        // Allowed MIME types
        private val ALLOWED_MIME_TYPES = setOf(
            "application/pdf",
            "application/x-pdf",
            "image/jpeg",
            "image/jpg", 
            "image/png",
            "image/gif",
            "image/bmp",
            "image/tiff",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf",
            "text/plain"
        )

        // Allowed file extensions
        private val ALLOWED_EXTENSIONS = setOf(
            ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp",
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".odt", ".ods", ".odp", ".rtf", ".txt"
        )

        // Suspicious patterns in filenames
        private val SUSPICIOUS_PATTERNS = listOf(
            "..", "/", "\\", ":", "*", "?", "\"", "<", ">", "|",
            "script", "javascript", "vbscript", "onload", "onerror"
        )
    }

    /**
     * Validates a multipart file for security and format compliance.
     * @param file The uploaded file to validate
     * @return ValidationResult containing validation status and details
     */
    /**
     * Validates a PDF file specifically for merge operations.
     */
    fun validatePdfFile(file: MultipartFile): ValidationResult {
        val generalValidation = validateFile(file)
        if (!generalValidation.isValid) {
            return generalValidation
        }
        
        // Additional PDF-specific validation
        if (!isPdfFile(file.originalFilename ?: "", file.contentType)) {
            return ValidationResult.failure("File is not a valid PDF", "INVALID_PDF_FORMAT")
        }
        
        return ValidationResult.success("PDF file validation passed")
    }

    fun validateFile(file: MultipartFile): ValidationResult {
        logger.debug("Starting validation for file: {}", file.originalFilename)

        try {
            // Basic null and empty checks
            if (file.isEmpty) {
                return ValidationResult.failure("File is empty")
            }

            val originalFilename = file.originalFilename
            if (originalFilename.isNullOrBlank()) {
                return ValidationResult.failure("File name is missing")
            }

            // File size validation
            if (file.size > MAX_FILE_SIZE) {
                return ValidationResult.failure("File size exceeds maximum allowed size of ${MAX_FILE_SIZE / (1024 * 1024)}MB")
            }

            // Filename security validation
            val filenameValidation = validateFilename(originalFilename)
            if (!filenameValidation.isValid) {
                return filenameValidation
            }

            // File extension validation
            val extensionValidation = validateFileExtension(originalFilename)
            if (!extensionValidation.isValid) {
                return extensionValidation
            }

            // MIME type validation
            val mimeValidation = validateMimeType(file.contentType)
            if (!mimeValidation.isValid) {
                return mimeValidation
            }

            // File signature validation
            val signatureValidation = validateFileSignature(file.bytes, originalFilename)
            if (!signatureValidation.isValid) {
                return signatureValidation
            }

            // PDF-specific validation if it's a PDF file
            if (isPdfFile(originalFilename, file.contentType)) {
                val pdfValidation = validatePdfStructure(file.bytes)
                if (!pdfValidation.isValid) {
                    return pdfValidation
                }
            }

            logger.info("File validation successful for: {}", originalFilename)
            return ValidationResult.success("File validation passed")

        } catch (e: Exception) {
            logger.error("Unexpected error during file validation for: {}", file.originalFilename, e)
            return ValidationResult.failure("Validation failed due to unexpected error: ${e.message}")
        }
    }

    /**
     * Validates a file path for security compliance.
     * @param filePath The file path to validate
     * @return ValidationResult containing validation status
     */
    fun validateFilePath(filePath: Path): ValidationResult {
        val pathString = filePath.toString()
        
        // Check for path traversal attempts
        if (pathString.contains("..") || pathString.contains("~")) {
            return ValidationResult.failure("Path traversal detected in file path")
        }

        // Check for suspicious path patterns
        if (pathString.matches(Regex(".*[<>:\"|?*].*"))) {
            return ValidationResult.failure("Invalid characters detected in file path")
        }

        return ValidationResult.success("File path validation passed")
    }

    private fun validateFilename(filename: String): ValidationResult {
        // Check for suspicious patterns
        val suspiciousPattern = SUSPICIOUS_PATTERNS.find { filename.lowercase().contains(it.lowercase()) }
        if (suspiciousPattern != null) {
            return ValidationResult.failure("Suspicious pattern '$suspiciousPattern' detected in filename")
        }

        // Check filename length
        if (filename.length > 255) {
            return ValidationResult.failure("Filename too long (max 255 characters)")
        }

        // Check for control characters
        if (filename.any { it.code < 32 || it.code == 127 }) {
            return ValidationResult.failure("Control characters detected in filename")
        }

        return ValidationResult.success("Filename validation passed")
    }

    private fun validateFileExtension(filename: String): ValidationResult {
        val extension = filename.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) {
            return ValidationResult.failure("File has no extension")
        }

        val fullExtension = ".$extension"
        if (fullExtension !in ALLOWED_EXTENSIONS) {
            return ValidationResult.failure("File extension '$fullExtension' is not allowed")
        }

        return ValidationResult.success("File extension validation passed")
    }

    private fun validateMimeType(contentType: String?): ValidationResult {
        if (contentType.isNullOrBlank()) {
            return ValidationResult.failure("MIME type is missing")
        }

        // Clean up MIME type (remove charset if present)
        val cleanMimeType = contentType.split(';')[0].trim().lowercase()

        if (cleanMimeType !in ALLOWED_MIME_TYPES) {
            return ValidationResult.failure("MIME type '$cleanMimeType' is not allowed")
        }

        return ValidationResult.success("MIME type validation passed")
    }

    private fun validateFileSignature(bytes: ByteArray, filename: String): ValidationResult {
        if (bytes.size < 4) {
            return ValidationResult.failure("File too small to validate signature")
        }

        val extension = filename.substringAfterLast('.', "").lowercase()
        
        return when (extension) {
            "pdf" -> {
                if (bytes.sliceArray(0..3).contentEquals(PDF_SIGNATURE)) {
                    ValidationResult.success("PDF signature validation passed")
                } else {
                    ValidationResult.failure("File does not have valid PDF signature")
                }
            }
            "jpg", "jpeg" -> {
                if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                    ValidationResult.success("JPEG signature validation passed")
                } else {
                    ValidationResult.failure("File does not have valid JPEG signature")
                }
            }
            "png" -> {
                val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
                if (bytes.sliceArray(0..3).contentEquals(pngSignature)) {
                    ValidationResult.success("PNG signature validation passed")
                } else {
                    ValidationResult.failure("File does not have valid PNG signature")
                }
            }
            "gif" -> {
                val gif87Signature = "GIF87a".toByteArray()
                val gif89Signature = "GIF89a".toByteArray()
                if (bytes.sliceArray(0..5).contentEquals(gif87Signature) || 
                    bytes.sliceArray(0..5).contentEquals(gif89Signature)) {
                    ValidationResult.success("GIF signature validation passed")
                } else {
                    ValidationResult.failure("File does not have valid GIF signature")
                }
            }
            else -> {
                // For other file types, skip signature validation for now
                ValidationResult.success("Signature validation skipped for file type")
            }
        }
    }

    private fun validatePdfStructure(bytes: ByteArray): ValidationResult {
        return try {
            // For now, we'll do basic PDF structure validation without full parsing
            // This can be enhanced later with proper PDFBox integration
            
            // Check PDF signature again as a basic structure validation
            if (bytes.size < 8) {
                return ValidationResult.failure("PDF file too small")
            }
            
            val headerString = String(bytes.sliceArray(0..7))
            if (!headerString.startsWith("%PDF-")) {
                return ValidationResult.failure("Invalid PDF header")
            }
            
            // Check for PDF trailer
            val endBytes = if (bytes.size > 1024) {
                bytes.sliceArray((bytes.size - 1024)..bytes.lastIndex)
            } else {
                bytes
            }
            
            val endString = String(endBytes)
            if (!endString.contains("%%EOF")) {
                return ValidationResult.failure("PDF file appears to be truncated or corrupted")
            }
            
            // Basic checks passed
            logger.debug("Basic PDF structure validation passed")
            ValidationResult.success("PDF structure validation passed")
            
        } catch (e: Exception) {
            logger.error("Unexpected error during PDF validation", e)
            ValidationResult.failure("PDF validation failed: ${e.message}")
        }
    }

    private fun isPdfFile(filename: String, contentType: String?): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val cleanContentType = contentType?.split(';')?.get(0)?.trim()?.lowercase()
        
        return extension == "pdf" || cleanContentType == "application/pdf" || cleanContentType == "application/x-pdf"
    }

    /**
     * Data class representing the result of file validation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val errorCode: String? = null
    ) {
        companion object {
            fun success(message: String): ValidationResult = ValidationResult(true, message)
            fun failure(message: String, errorCode: String? = null): ValidationResult = 
                ValidationResult(false, message, errorCode)
        }
    }
}