package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*
import java.util.regex.Pattern

/**
 * Service for detecting malicious content in uploaded files.
 * Scans files for suspicious patterns, embedded scripts, and potential security threats.
 */
@Service
class MaliciousContentDetector {

    private val logger = LoggerFactory.getLogger(MaliciousContentDetector::class.java)

    companion object {
        // Suspicious JavaScript patterns
        private val JAVASCRIPT_PATTERNS = listOf(
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("document\\.write", Pattern.CASE_INSENSITIVE),
            Pattern.compile("window\\.location", Pattern.CASE_INSENSITIVE),
            Pattern.compile("alert\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("confirm\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("prompt\\s*\\(", Pattern.CASE_INSENSITIVE)
        )

        // Suspicious VBScript patterns
        private val VBSCRIPT_PATTERNS = listOf(
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wscript\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("createobject\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("shell\\.application", Pattern.CASE_INSENSITIVE),
            Pattern.compile("filesystemobject", Pattern.CASE_INSENSITIVE)
        )

        // Suspicious executable patterns
        private val EXECUTABLE_PATTERNS = listOf(
            Pattern.compile("cmd\\.exe", Pattern.CASE_INSENSITIVE),
            Pattern.compile("powershell", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/bin/sh", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/bin/bash", Pattern.CASE_INSENSITIVE),
            Pattern.compile("chmod\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wget\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("curl\\s+", Pattern.CASE_INSENSITIVE)
        )

        // SQL injection patterns
        private val SQL_INJECTION_PATTERNS = listOf(
            Pattern.compile("union\\s+select", Pattern.CASE_INSENSITIVE),
            Pattern.compile("drop\\s+table", Pattern.CASE_INSENSITIVE),
            Pattern.compile("delete\\s+from", Pattern.CASE_INSENSITIVE),
            Pattern.compile("insert\\s+into", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'\\s*or\\s+'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--\\s*$", Pattern.MULTILINE),
            Pattern.compile("/\\*.*\\*/", Pattern.DOTALL)
        )

        // XSS patterns
        private val XSS_PATTERNS = listOf(
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onload\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onerror\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onclick\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onmouseover\\s*=", Pattern.CASE_INSENSITIVE)
        )

        // Suspicious PDF patterns
        private val PDF_MALICIOUS_PATTERNS = listOf(
            Pattern.compile("/JavaScript", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/JS", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/OpenAction", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/Launch", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/EmbeddedFile", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/RichMedia", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/Flash", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/Sound", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/Movie", Pattern.CASE_INSENSITIVE)
        )

        // Suspicious file paths within archives
        private val SUSPICIOUS_ARCHIVE_PATHS = listOf(
            "../", "..\\", "..\\/", "..\\\\",
            "/etc/", "\\windows\\", "c:\\", "d:\\",
            "/bin/", "/usr/", "/var/", "/tmp/",
            "autorun.inf", "desktop.ini", ".htaccess"
        )

        // Potentially dangerous MIME types embedded in files
        private val DANGEROUS_EMBEDDED_TYPES = setOf(
            "application/x-executable",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-winexe",
            "application/x-bat",
            "application/x-com",
            "application/x-msi",
            "application/vnd.microsoft.portable-executable"
        )

        // Maximum file size for content scanning (10MB)
        private const val MAX_SCAN_SIZE = 10L * 1024 * 1024

        // Maximum depth for nested archive scanning
        private const val MAX_ARCHIVE_DEPTH = 3
    }

    /**
     * Scans a multipart file for malicious content and security threats.
     * @param file The file to scan
     * @return ScanResult containing scan status and threat details
     */
    fun scanFile(file: MultipartFile): ScanResult {
        logger.debug("Starting malicious content scan for file: {}", file.originalFilename)

        try {
            // Skip scanning for very large files to prevent resource exhaustion
            if (file.size > MAX_SCAN_SIZE) {
                logger.info("Skipping content scan for large file: {} (size: {} bytes)", 
                    file.originalFilename, file.size)
                return ScanResult.clean("File too large for content scanning - basic validation only")
            }

            val filename = file.originalFilename ?: "unknown"
            val contentType = file.contentType
            val fileBytes = file.bytes

            // Check filename for suspicious patterns
            val filenameCheck = scanFilename(filename)
            if (filenameCheck.isThreat) {
                return filenameCheck
            }

            // Check MIME type
            val mimeCheck = scanMimeType(contentType)
            if (mimeCheck.isThreat) {
                return mimeCheck
            }

            // Scan file content
            val contentCheck = scanFileContent(fileBytes, filename)
            if (contentCheck.isThreat) {
                return contentCheck
            }

            // PDF-specific scanning
            if (isPdfFile(filename, contentType)) {
                val pdfCheck = scanPdfContent(fileBytes)
                if (pdfCheck.isThreat) {
                    return pdfCheck
                }
            }

            logger.info("Malicious content scan completed successfully for: {}", filename)
            return ScanResult.clean("No malicious content detected")

        } catch (e: Exception) {
            logger.error("Error during malicious content scan for: {}", file.originalFilename, e)
            return ScanResult.error("Scan failed due to error: ${e.message}")
        }
    }

    /**
     * Scans file content as bytes for malicious patterns.
     * @param content The file content as byte array
     * @param filename The original filename
     * @return ScanResult containing scan results
     */
    fun scanContent(content: ByteArray, filename: String): ScanResult {
        logger.debug("Scanning content for file: {}", filename)

        if (content.size > MAX_SCAN_SIZE) {
            return ScanResult.clean("Content too large for scanning")
        }

        return scanFileContent(content, filename)
    }

    private fun scanFilename(filename: String): ScanResult {
        // Check for double extensions (e.g., .pdf.exe)
        val parts = filename.split('.')
        if (parts.size > 2) {
            val extensions = parts.drop(1)
            val suspiciousExts = setOf("exe", "bat", "cmd", "scr", "pif", "com", "vbs", "js")
            for (ext in extensions) {
                if (ext.lowercase() in suspiciousExts) {
                    return ScanResult.threat("Suspicious double extension detected: .$ext", "DOUBLE_EXTENSION")
                }
            }
        }

        // Check for very long filenames (potential buffer overflow)
        if (filename.length > 260) {
            return ScanResult.threat("Filename too long (potential security risk)", "LONG_FILENAME")
        }

        // Check for Unicode homograph attacks
        if (containsHomographCharacters(filename)) {
            return ScanResult.threat("Suspicious Unicode characters detected", "HOMOGRAPH_ATTACK")
        }

        return ScanResult.clean("Filename check passed")
    }

    private fun scanMimeType(contentType: String?): ScanResult {
        if (contentType == null) {
            return ScanResult.clean("No MIME type to check")
        }

        val cleanType = contentType.split(';')[0].trim().lowercase()
        
        if (cleanType in DANGEROUS_EMBEDDED_TYPES) {
            return ScanResult.threat("Dangerous MIME type detected: $cleanType", "DANGEROUS_MIME")
        }

        return ScanResult.clean("MIME type check passed")
    }

    private fun scanFileContent(content: ByteArray, filename: String): ScanResult {
        val contentString = try {
            String(content, Charsets.UTF_8)
        } catch (e: Exception) {
            // If content is not valid UTF-8, scan as binary
            return scanBinaryContent(content)
        }

        // Scan for JavaScript patterns
        for (pattern in JAVASCRIPT_PATTERNS) {
            if (pattern.matcher(contentString).find()) {
                return ScanResult.threat("Suspicious JavaScript detected", "JAVASCRIPT_THREAT")
            }
        }

        // Scan for VBScript patterns
        for (pattern in VBSCRIPT_PATTERNS) {
            if (pattern.matcher(contentString).find()) {
                return ScanResult.threat("Suspicious VBScript detected", "VBSCRIPT_THREAT")
            }
        }

        // Scan for executable patterns
        for (pattern in EXECUTABLE_PATTERNS) {
            if (pattern.matcher(contentString).find()) {
                return ScanResult.threat("Suspicious executable pattern detected", "EXECUTABLE_THREAT")
            }
        }

        // Scan for SQL injection patterns
        for (pattern in SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(contentString).find()) {
                return ScanResult.threat("Potential SQL injection detected", "SQL_INJECTION")
            }
        }

        // Scan for XSS patterns
        for (pattern in XSS_PATTERNS) {
            if (pattern.matcher(contentString).find()) {
                return ScanResult.threat("Potential XSS attack detected", "XSS_THREAT")
            }
        }

        return ScanResult.clean("Content scan passed")
    }

    private fun scanBinaryContent(content: ByteArray): ScanResult {
        // Check for PE (Windows executable) header
        if (content.size >= 2 && content[0] == 0x4D.toByte() && content[1] == 0x5A.toByte()) {
            return ScanResult.threat("Windows executable detected", "PE_EXECUTABLE")
        }

        // Check for ELF (Linux executable) header
        if (content.size >= 4 && 
            content[0] == 0x7F.toByte() && content[1] == 0x45.toByte() && 
            content[2] == 0x4C.toByte() && content[3] == 0x46.toByte()) {
            return ScanResult.threat("Linux executable detected", "ELF_EXECUTABLE")
        }

        // Check for Mach-O (macOS executable) header
        if (content.size >= 4 && 
            ((content[0] == 0xFE.toByte() && content[1] == 0xED.toByte() && 
              content[2] == 0xFA.toByte() && content[3] == 0xCE.toByte()) ||
             (content[0] == 0xFE.toByte() && content[1] == 0xED.toByte() && 
              content[2] == 0xFA.toByte() && content[3] == 0xCF.toByte()))) {
            return ScanResult.threat("macOS executable detected", "MACHO_EXECUTABLE")
        }

        return ScanResult.clean("Binary content scan passed")
    }

    private fun scanPdfContent(content: ByteArray): ScanResult {
        val contentString = try {
            String(content, Charsets.ISO_8859_1) // PDF uses Latin-1 encoding
        } catch (e: Exception) {
            return ScanResult.clean("Could not decode PDF content for scanning")
        }

        for (pattern in PDF_MALICIOUS_PATTERNS) {
            if (pattern.matcher(contentString).find()) {
                return ScanResult.threat("Suspicious PDF content detected", "MALICIOUS_PDF")
            }
        }

        // Check for suspicious PDF actions
        if (contentString.contains("/AA") && contentString.contains("/O")) {
            return ScanResult.threat("PDF with automatic actions detected", "PDF_AUTO_ACTION")
        }

        return ScanResult.clean("PDF content scan passed")
    }

    private fun containsHomographCharacters(text: String): Boolean {
        for (char in text) {
            // Check for common homograph characters
            when (char.code) {
                in 0x0400..0x04FF, // Cyrillic
                in 0x0370..0x03FF, // Greek
                in 0x0100..0x017F, // Latin Extended-A
                in 0x1E00..0x1EFF  // Latin Extended Additional
                -> return true
            }
        }
        return false
    }

    private fun isPdfFile(filename: String, contentType: String?): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val cleanContentType = contentType?.split(';')?.get(0)?.trim()?.lowercase()
        
        return extension == "pdf" || cleanContentType == "application/pdf" || cleanContentType == "application/x-pdf"
    }

    /**
     * Data class representing the result of malicious content scanning.
     */
    data class ScanResult(
        val isThreat: Boolean,
        val isClean: Boolean,
        val isError: Boolean,
        val message: String,
        val threatType: String? = null,
        val confidence: Double = 1.0
    ) {
        companion object {
            fun threat(message: String, threatType: String, confidence: Double = 1.0): ScanResult =
                ScanResult(true, false, false, message, threatType, confidence)
                
            fun clean(message: String): ScanResult =
                ScanResult(false, true, false, message)
                
            fun error(message: String): ScanResult =
                ScanResult(false, false, true, message)
        }
    }
}