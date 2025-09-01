package org.example.pdfwrangler.util

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility class for preventing directory traversal attacks and validating file paths.
 * Provides comprehensive protection against path manipulation attempts.
 */
@Component
class PathTraversalProtection {

    private val logger = LoggerFactory.getLogger(PathTraversalProtection::class.java)

    companion object {
        // Dangerous path patterns that indicate traversal attempts
        private val DANGEROUS_PATTERNS = listOf(
            "..", "~", "//", "\\\\", "%2e%2e", "%2E%2E", "%2f", "%2F", "%5c", "%5C",
            "..\\", "../", "..%2f", "..%2F", "..%5c", "..%5C",
            "%252e%252e", "%c0%af", "%c1%9c", "\\x2e\\x2e", "0x2e0x2e"
        )

        // Suspicious characters in paths
        private val SUSPICIOUS_CHARS = charArrayOf(
            '<', '>', ':', '"', '|', '?', '*', '\u0000', '\u0001', '\u0002', '\u0003'
        )

        // Reserved names on Windows (also protected on other platforms for consistency)
        private val RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
    }

    /**
     * Validates that a file path is safe and within allowed boundaries.
     * @param path The path to validate
     * @param allowedBasePath The base directory that the path must be within
     * @return PathValidationResult containing validation status and details
     */
    fun validatePath(path: String, allowedBasePath: String): PathValidationResult {
        logger.debug("Validating path: {} against base: {}", path, allowedBasePath)

        try {
            // Basic null/empty checks
            if (path.isBlank()) {
                return PathValidationResult.failure("Path cannot be empty")
            }

            if (allowedBasePath.isBlank()) {
                return PathValidationResult.failure("Base path cannot be empty")
            }

            // Check for dangerous patterns
            val dangerousPattern = DANGEROUS_PATTERNS.find { pattern ->
                path.lowercase().contains(pattern.lowercase())
            }
            if (dangerousPattern != null) {
                logger.warn("Dangerous path pattern detected: {} in path: {}", dangerousPattern, path)
                return PathValidationResult.failure("Path contains dangerous pattern: $dangerousPattern")
            }

            // Check for suspicious characters
            val suspiciousChar = SUSPICIOUS_CHARS.find { char -> path.contains(char) }
            if (suspiciousChar != null) {
                logger.warn("Suspicious character detected: {} in path: {}", suspiciousChar, path)
                return PathValidationResult.failure("Path contains suspicious character: $suspiciousChar")
            }

            // Check for reserved names
            val pathParts = path.split('/', '\\').filter { it.isNotBlank() }
            for (part in pathParts) {
                val baseName = part.substringBeforeLast('.').uppercase()
                if (baseName in RESERVED_NAMES) {
                    logger.warn("Reserved name detected: {} in path: {}", baseName, path)
                    return PathValidationResult.failure("Path contains reserved name: $baseName")
                }
            }

            // Normalize and resolve paths for comparison
            val resolvedPath = resolvePath(path, allowedBasePath)
            if (!resolvedPath.isValid) {
                return resolvedPath
            }

            logger.debug("Path validation successful for: {}", path)
            return PathValidationResult.success("Path validation passed", resolvedPath.normalizedPath!!)

        } catch (e: Exception) {
            logger.error("Unexpected error during path validation for: {}", path, e)
            return PathValidationResult.failure("Path validation failed: ${e.message}")
        }
    }

    /**
     * Validates a Path object against a base directory.
     * @param path The Path to validate
     * @param allowedBasePath The base directory Path
     * @return PathValidationResult containing validation status
     */
    fun validatePath(path: Path, allowedBasePath: Path): PathValidationResult {
        return try {
            val normalizedPath = path.normalize().toAbsolutePath()
            val normalizedBasePath = allowedBasePath.normalize().toAbsolutePath()

            if (normalizedPath.startsWith(normalizedBasePath).not()) {
                logger.warn("Path traversal attempt detected: {} is outside base: {}", 
                    normalizedPath, normalizedBasePath)
                return PathValidationResult.failure("Path is outside allowed directory")
            }

            PathValidationResult.success("Path validation passed", normalizedPath.toString())
        } catch (e: Exception) {
            logger.error("Error validating Path objects", e)
            PathValidationResult.failure("Path validation failed: ${e.message}")
        }
    }

    /**
     * Creates a safe path by joining components and validating the result.
     * @param basePath The base directory path
     * @param pathComponents Variable number of path components to join
     * @return PathValidationResult with the safe joined path
     */
    fun createSafePath(basePath: String, vararg pathComponents: String): PathValidationResult {
        return try {
            if (pathComponents.isEmpty()) {
                return PathValidationResult.failure("No path components provided")
            }

            // Join path components safely
            val joinedPath = pathComponents.joinToString(File.separator)
            
            // Validate each component individually
            for (component in pathComponents) {
                if (component.contains("..") || component.contains("~")) {
                    return PathValidationResult.failure("Path component contains traversal attempt: $component")
                }
                
                if (component.any { it in SUSPICIOUS_CHARS }) {
                    return PathValidationResult.failure("Path component contains suspicious character: $component")
                }
            }

            // Validate the complete path
            validatePath(joinedPath, basePath)

        } catch (e: Exception) {
            logger.error("Error creating safe path", e)
            PathValidationResult.failure("Failed to create safe path: ${e.message}")
        }
    }

    /**
     * Sanitizes a filename by removing or replacing dangerous characters.
     * @param filename The filename to sanitize
     * @param replacement Character to replace dangerous characters with
     * @return Sanitized filename
     */
    fun sanitizeFilename(filename: String, replacement: Char = '_'): String {
        if (filename.isBlank()) {
            return "unnamed_file"
        }

        var sanitized = filename

        // Remove/replace dangerous patterns
        DANGEROUS_PATTERNS.forEach { pattern ->
            sanitized = sanitized.replace(pattern, replacement.toString(), ignoreCase = true)
        }

        // Remove/replace suspicious characters
        SUSPICIOUS_CHARS.forEach { char ->
            sanitized = sanitized.replace(char, replacement)
        }

        // Handle reserved names
        val baseName = sanitized.substringBeforeLast('.')
        val extension = if (sanitized.contains('.')) ".${sanitized.substringAfterLast('.')}" else ""
        
        if (baseName.uppercase() in RESERVED_NAMES) {
            sanitized = "${baseName}_safe$extension"
        }

        // Ensure filename is not empty after sanitization
        if (sanitized.isBlank() || sanitized == extension) {
            sanitized = "sanitized_file$extension"
        }

        // Limit filename length
        if (sanitized.length > 255) {
            val extensionPart = if (extension.isNotEmpty()) extension else ""
            val maxBaseLength = 255 - extensionPart.length
            sanitized = sanitized.substring(0, maxBaseLength) + extensionPart
        }

        return sanitized
    }

    private fun resolvePath(path: String, basePath: String): PathValidationResult {
        return try {
            val normalizedBasePath = Paths.get(basePath).normalize().toAbsolutePath()
            val candidatePath = normalizedBasePath.resolve(path).normalize().toAbsolutePath()

            // Check if the resolved path is within the base path
            if (candidatePath.startsWith(normalizedBasePath).not()) {
                logger.warn("Path traversal detected: {} resolves outside base {}", 
                    path, basePath)
                return PathValidationResult.failure("Path traversal detected")
            }

            PathValidationResult.success("Path resolution successful", candidatePath.toString())
        } catch (e: Exception) {
            logger.error("Error resolving path: {} with base: {}", path, basePath, e)
            PathValidationResult.failure("Path resolution failed: ${e.message}")
        }
    }

    /**
     * Data class representing the result of path validation.
     */
    data class PathValidationResult(
        val isValid: Boolean,
        val message: String,
        val normalizedPath: String? = null,
        val errorCode: String? = null
    ) {
        companion object {
            fun success(message: String, normalizedPath: String): PathValidationResult = 
                PathValidationResult(true, message, normalizedPath)
                
            fun failure(message: String, errorCode: String? = null): PathValidationResult = 
                PathValidationResult(false, message, null, errorCode)
        }
    }
}