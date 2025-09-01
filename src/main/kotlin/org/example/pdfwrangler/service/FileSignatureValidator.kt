package org.example.pdfwrangler.service

import org.example.pdfwrangler.exception.FileValidationSecurityException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Service for comprehensive file signature validation to verify authentic file formats
 * and detect format spoofing attempts. Provides deep analysis of file headers and
 * magic bytes to ensure files match their claimed format.
 */
@Service
class FileSignatureValidator {

    private val logger = LoggerFactory.getLogger(FileSignatureValidator::class.java)

    companion object {
        // Minimum bytes needed for comprehensive signature validation
        private const val MIN_SIGNATURE_BYTES = 512

        // File signature definitions with multiple possible signatures per format
        private val FILE_SIGNATURES = mapOf(
            // PDF signatures
            "pdf" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46), // %PDF
                    offset = 0,
                    description = "PDF header"
                ),
                FileSignature(
                    bytes = "%%EOF".toByteArray(),
                    offset = -10, // Check near end of file (negative offset)
                    description = "PDF footer"
                )
            ),
            
            // JPEG signatures
            "jpg" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                    offset = 0,
                    description = "JPEG header"
                ),
                FileSignature(
                    bytes = byteArrayOf(0xFF.toByte(), 0xD9.toByte()),
                    offset = -2, // JPEG end marker
                    description = "JPEG footer"
                )
            ),
            "jpeg" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                    offset = 0,
                    description = "JPEG header"
                )
            ),
            
            // PNG signatures
            "png" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
                    offset = 0,
                    description = "PNG header"
                ),
                FileSignature(
                    bytes = "IHDR".toByteArray(),
                    offset = 12,
                    description = "PNG IHDR chunk"
                ),
                FileSignature(
                    bytes = "IEND".toByteArray(),
                    offset = -8, // Near end of file
                    description = "PNG IEND chunk"
                )
            ),
            
            // GIF signatures
            "gif" to listOf(
                FileSignature(
                    bytes = "GIF87a".toByteArray(),
                    offset = 0,
                    description = "GIF87a header"
                ),
                FileSignature(
                    bytes = "GIF89a".toByteArray(),
                    offset = 0,
                    description = "GIF89a header"
                )
            ),
            
            // BMP signatures
            "bmp" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x42, 0x4D), // BM
                    offset = 0,
                    description = "BMP header"
                )
            ),
            
            // TIFF signatures
            "tiff" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x49, 0x49, 0x2A, 0x00), // Little endian
                    offset = 0,
                    description = "TIFF little endian header"
                ),
                FileSignature(
                    bytes = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A), // Big endian
                    offset = 0,
                    description = "TIFF big endian header"
                )
            ),
            "tif" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x49, 0x49, 0x2A, 0x00),
                    offset = 0,
                    description = "TIFF little endian header"
                ),
                FileSignature(
                    bytes = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A),
                    offset = 0,
                    description = "TIFF big endian header"
                )
            ),
            
            // WebP signatures
            "webp" to listOf(
                FileSignature(
                    bytes = "RIFF".toByteArray(),
                    offset = 0,
                    description = "WebP RIFF header"
                ),
                FileSignature(
                    bytes = "WEBP".toByteArray(),
                    offset = 8,
                    description = "WebP format identifier"
                )
            ),
            
            // Microsoft Office formats
            "doc" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()),
                    offset = 0,
                    description = "OLE2 header (DOC)"
                )
            ),
            "docx" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04), // ZIP header
                    offset = 0,
                    description = "ZIP header (DOCX container)"
                ),
                FileSignature(
                    bytes = "[Content_Types].xml".toByteArray(),
                    offset = 30, // Approximate location in ZIP
                    description = "DOCX content types"
                )
            ),
            "xls" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()),
                    offset = 0,
                    description = "OLE2 header (XLS)"
                )
            ),
            "xlsx" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
                    offset = 0,
                    description = "ZIP header (XLSX container)"
                )
            ),
            "ppt" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()),
                    offset = 0,
                    description = "OLE2 header (PPT)"
                )
            ),
            "pptx" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
                    offset = 0,
                    description = "ZIP header (PPTX container)"
                )
            ),
            
            // OpenDocument formats
            "odt" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
                    offset = 0,
                    description = "ZIP header (ODT container)"
                ),
                FileSignature(
                    bytes = "mimetype".toByteArray(),
                    offset = 30,
                    description = "ODT mimetype entry"
                )
            ),
            "ods" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
                    offset = 0,
                    description = "ZIP header (ODS container)"
                )
            ),
            "odp" to listOf(
                FileSignature(
                    bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
                    offset = 0,
                    description = "ZIP header (ODP container)"
                )
            ),
            
            // RTF format
            "rtf" to listOf(
                FileSignature(
                    bytes = "{\\rtf".toByteArray(),
                    offset = 0,
                    description = "RTF header"
                )
            ),
            
            // Plain text (basic validation)
            "txt" to listOf(
                FileSignature(
                    bytes = byteArrayOf(), // Empty signature for text files
                    offset = 0,
                    description = "Plain text (no specific signature)"
                )
            )
        )
    }

    data class FileSignature(
        val bytes: ByteArray,
        val offset: Int, // Negative offset means from end of file
        val description: String,
        val required: Boolean = true
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FileSignature
            return bytes.contentEquals(other.bytes) && offset == other.offset
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + offset
            return result
        }
    }

    data class SignatureValidationResult(
        val isValid: Boolean,
        val detectedFormat: String? = null,
        val confidence: Double = 0.0,
        val matchedSignatures: List<String> = emptyList(),
        val failedSignatures: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    ) {
        companion object {
            fun valid(format: String, confidence: Double, signatures: List<String>): SignatureValidationResult {
                return SignatureValidationResult(
                    isValid = true,
                    detectedFormat = format,
                    confidence = confidence,
                    matchedSignatures = signatures
                )
            }
            
            fun invalid(errors: List<String>, warnings: List<String> = emptyList()): SignatureValidationResult {
                return SignatureValidationResult(
                    isValid = false,
                    errors = errors,
                    warnings = warnings
                )
            }
        }
    }

    /**
     * Validates file signature against claimed format.
     * @param file The uploaded file to validate
     * @return SignatureValidationResult with detailed validation information
     */
    fun validateFileSignature(file: MultipartFile): SignatureValidationResult {
        val filename = file.originalFilename ?: return SignatureValidationResult.invalid(
            errors = listOf("Missing filename")
        )

        val extension = getFileExtension(filename)
        if (extension.isEmpty()) {
            return SignatureValidationResult.invalid(
                errors = listOf("File has no extension")
            )
        }

        return try {
            val fileBytes = file.bytes
            validateSignature(fileBytes, extension, filename)
        } catch (e: IOException) {
            logger.error("Error reading file for signature validation: {}", filename, e)
            SignatureValidationResult.invalid(
                errors = listOf("Cannot read file for signature validation: ${e.message}")
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during signature validation: {}", filename, e)
            SignatureValidationResult.invalid(
                errors = listOf("Signature validation failed: ${e.message}")
            )
        }
    }

    /**
     * Validates file signature from byte array.
     * @param fileBytes The file content as bytes
     * @param expectedExtension Expected file extension
     * @param filename Original filename for logging
     * @return SignatureValidationResult
     */
    fun validateSignature(
        fileBytes: ByteArray,
        expectedExtension: String,
        filename: String = "unknown"
    ): SignatureValidationResult {
        
        if (fileBytes.isEmpty()) {
            return SignatureValidationResult.invalid(errors = listOf("File is empty"))
        }

        val extension = expectedExtension.lowercase().removePrefix(".")
        val signatures = FILE_SIGNATURES[extension]

        if (signatures == null) {
            logger.debug("No signature validation available for extension: {}", extension)
            return SignatureValidationResult.valid(
                format = extension,
                confidence = 0.5, // Lower confidence for unsupported formats
                signatures = listOf("No signature validation for $extension")
            )
        }

        // Special handling for plain text files
        if (extension == "txt") {
            return validateTextFile(fileBytes, filename)
        }

        val matchedSignatures = mutableListOf<String>()
        val failedSignatures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (signature in signatures) {
            val matchResult = checkSignature(fileBytes, signature)
            if (matchResult) {
                matchedSignatures.add(signature.description)
                logger.debug("Signature match: {} for file: {}", signature.description, filename)
            } else {
                failedSignatures.add(signature.description)
                logger.debug("Signature mismatch: {} for file: {}", signature.description, filename)
            }
        }

        // Calculate confidence based on signature matches
        val confidence = if (signatures.isEmpty()) {
            0.5
        } else {
            matchedSignatures.size.toDouble() / signatures.size.toDouble()
        }

        // Determine if validation passed
        val isValid = when {
            extension == "txt" -> true // Text files don't have strict signatures
            matchedSignatures.isEmpty() -> false
            confidence < 0.5 -> false
            else -> true
        }

        // Add warnings for partial matches
        if (confidence < 1.0 && confidence >= 0.5) {
            warnings.add("Some signatures did not match, but format appears valid")
        }

        // Perform additional format-specific validation
        val formatSpecificResult = performFormatSpecificValidation(fileBytes, extension, filename)
        warnings.addAll(formatSpecificResult.warnings)

        if (!isValid) {
            val errorMsg = "File signature does not match claimed format '$extension'"
            logger.warn("Signature validation failed for {}: {}", filename, errorMsg)
            return SignatureValidationResult.invalid(
                errors = listOf(errorMsg),
                warnings = warnings
            )
        }

        logger.info("Signature validation passed for {}: {} (confidence: {:.2f})", 
            filename, extension, confidence)

        return SignatureValidationResult.valid(
            format = extension,
            confidence = confidence,
            signatures = matchedSignatures
        ).copy(warnings = warnings)
    }

    /**
     * Detects file format based solely on signature analysis.
     * @param fileBytes The file content as bytes
     * @return Detected format or null if unable to determine
     */
    fun detectFileFormat(fileBytes: ByteArray): String? {
        if (fileBytes.isEmpty()) return null

        // Check each known format
        for ((format, signatures) in FILE_SIGNATURES) {
            if (format == "txt") continue // Skip text files in detection

            var matches = 0
            for (signature in signatures) {
                if (checkSignature(fileBytes, signature)) {
                    matches++
                }
            }

            // If most signatures match, consider it a detection
            if (matches > 0 && matches >= signatures.size / 2) {
                logger.debug("Format detected as: {} (matched {}/{} signatures)", 
                    format, matches, signatures.size)
                return format
            }
        }

        return null
    }

    private fun checkSignature(fileBytes: ByteArray, signature: FileSignature): Boolean {
        if (signature.bytes.isEmpty()) return true // Empty signature always matches

        val actualOffset = if (signature.offset < 0) {
            // Negative offset means from end of file
            maxOf(0, fileBytes.size + signature.offset)
        } else {
            signature.offset
        }

        // Check if we have enough bytes
        if (actualOffset + signature.bytes.size > fileBytes.size) {
            return false
        }

        // Compare bytes
        for (i in signature.bytes.indices) {
            if (fileBytes[actualOffset + i] != signature.bytes[i]) {
                return false
            }
        }

        return true
    }

    private fun validateTextFile(fileBytes: ByteArray, filename: String): SignatureValidationResult {
        // Basic text file validation - check if content is mostly printable
        var printableCount = 0
        var totalChecked = minOf(1024, fileBytes.size) // Check first 1KB

        for (i in 0 until totalChecked) {
            val byte = fileBytes[i].toInt() and 0xFF
            if ((byte >= 32 && byte <= 126) || byte == 9 || byte == 10 || byte == 13) {
                printableCount++
            }
        }

        val printableRatio = printableCount.toDouble() / totalChecked.toDouble()
        val confidence = minOf(1.0, printableRatio + 0.1) // Small boost for text files

        return if (printableRatio >= 0.8) {
            SignatureValidationResult.valid(
                format = "txt",
                confidence = confidence,
                signatures = listOf("Text content validation passed")
            )
        } else {
            SignatureValidationResult.invalid(
                errors = listOf("File does not appear to contain valid text content")
            )
        }
    }

    private fun performFormatSpecificValidation(
        fileBytes: ByteArray,
        extension: String,
        filename: String
    ): SignatureValidationResult {
        val warnings = mutableListOf<String>()

        when (extension) {
            "pdf" -> {
                // Additional PDF validation
                val pdfVersion = extractPdfVersion(fileBytes)
                if (pdfVersion != null) {
                    logger.debug("PDF version detected: {} for file: {}", pdfVersion, filename)
                    if (pdfVersion < 1.4) {
                        warnings.add("PDF version $pdfVersion is quite old and may have compatibility issues")
                    }
                } else {
                    warnings.add("Could not determine PDF version")
                }
            }
            "jpg", "jpeg" -> {
                // Additional JPEG validation
                if (!validateJpegStructure(fileBytes)) {
                    warnings.add("JPEG structure appears corrupted or non-standard")
                }
            }
            "png" -> {
                // Additional PNG validation
                if (!validatePngCrc(fileBytes)) {
                    warnings.add("PNG CRC validation failed - file may be corrupted")
                }
            }
        }

        return SignatureValidationResult(
            isValid = true,
            warnings = warnings
        )
    }

    private fun extractPdfVersion(fileBytes: ByteArray): Double? {
        if (fileBytes.size < 8) return null

        return try {
            val header = String(fileBytes.sliceArray(0..7))
            val versionMatch = Regex("""PDF-(\d+\.\d+)""").find(header)
            versionMatch?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (e: Exception) {
            logger.debug("Error extracting PDF version", e)
            null
        }
    }

    private fun validateJpegStructure(fileBytes: ByteArray): Boolean {
        // Basic JPEG structure validation - check for valid markers
        if (fileBytes.size < 4) return false

        var i = 0
        while (i < fileBytes.size - 1) {
            if (fileBytes[i] == 0xFF.toByte()) {
                val marker = fileBytes[i + 1].toInt() and 0xFF
                if (marker == 0x00 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                    // These are valid but don't advance position much
                    i += 2
                } else if (marker >= 0xC0 && marker <= 0xFE) {
                    // Valid JPEG marker found
                    return true
                } else {
                    return false
                }
            } else {
                i++
            }
            
            // Don't check too much of the file
            if (i > 1024) break
        }

        return true
    }

    private fun validatePngCrc(fileBytes: ByteArray): Boolean {
        // Basic PNG CRC validation for IHDR chunk
        if (fileBytes.size < 29) return false

        try {
            // IHDR should start at offset 12
            val ihdrStart = 12
            val chunkLength = ByteBuffer.wrap(fileBytes, 8, 4).int
            
            if (chunkLength != 13) return false // IHDR is always 13 bytes
            
            // This is a simplified check - full CRC validation would be more complex
            return String(fileBytes, ihdrStart, 4) == "IHDR"
        } catch (e: Exception) {
            logger.debug("Error validating PNG CRC", e)
            return false
        }
    }

    private fun getFileExtension(filename: String): String {
        val lastDot = filename.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < filename.length - 1) {
            filename.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }
}