package org.example.pdfwrangler.service

import org.example.pdfwrangler.config.ConfigurableResourceLimits
import org.example.pdfwrangler.util.PathTraversalProtection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Secure temporary file handler with proper isolation and permissions.
 * Provides enhanced security features for temporary file operations including
 * permission management, process isolation, and secure cleanup.
 */
@Service
class SecureTempFileHandler(
    private val resourceLimits: ConfigurableResourceLimits,
    private val pathTraversalProtection: PathTraversalProtection
) {

    private val logger = LoggerFactory.getLogger(SecureTempFileHandler::class.java)
    private val secureRandom = SecureRandom()
    private val secureFiles = ConcurrentHashMap<String, SecureFileInfo>()
    
    private lateinit var secureBaseDirectory: Path
    private lateinit var isolatedDirectory: Path
    
    companion object {
        private const val SECURE_PREFIX = "secure-pdf-"
        private const val ISOLATION_DIR = "isolated"
        private const val QUARANTINE_DIR = "quarantine"
        
        // Secure file permissions (owner read/write only)
        private val SECURE_FILE_PERMISSIONS = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        )
        
        // Secure directory permissions (owner read/write/execute only)
        private val SECURE_DIR_PERMISSIONS = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        )
    }

    data class SecureFileInfo(
        val path: Path,
        val originalName: String,
        val createdAt: Instant,
        val processId: String,
        val isIsolated: Boolean,
        val permissions: Set<PosixFilePermission>,
        val maxAge: java.time.Duration,
        val checksum: String? = null
    )

    data class SecureFileHandle(
        val path: Path,
        val fileId: String,
        val isReadOnly: Boolean = false,
        val metadata: Map<String, String> = emptyMap()
    ) {
        fun getAbsolutePath(): String = path.toAbsolutePath().toString()
        fun exists(): Boolean = Files.exists(path)
        fun size(): Long = if (exists()) Files.size(path) else 0L
    }

    @PostConstruct
    fun initialize() {
        try {
            // Create secure base directory
            val tempDir = Paths.get(System.getProperty("java.io.tmpdir"))
            secureBaseDirectory = tempDir.resolve("pdf-wrangler-secure")
            isolatedDirectory = secureBaseDirectory.resolve(ISOLATION_DIR)
            val quarantineDirectory = secureBaseDirectory.resolve(QUARANTINE_DIR)
            
            // Create directories with secure permissions
            createSecureDirectory(secureBaseDirectory)
            createSecureDirectory(isolatedDirectory)
            createSecureDirectory(quarantineDirectory)
            
            logger.info("Secure temporary file handler initialized: {}", secureBaseDirectory)
            
        } catch (e: Exception) {
            logger.error("Failed to initialize secure temporary file handler", e)
            throw IllegalStateException("Cannot initialize secure file handling", e)
        }
    }

    /**
     * Creates a secure temporary file with proper isolation and permissions.
     * @param originalFilename Original filename for reference
     * @param isolated Whether to create in isolated directory
     * @param maxAge Maximum age before automatic cleanup
     * @return SecureFileHandle for the created file
     */
    fun createSecureFile(
        originalFilename: String,
        isolated: Boolean = false,
        maxAge: java.time.Duration = resourceLimits.maxTempFileAge
    ): SecureFileHandle {
        
        // Validate filename
        val sanitizedName = pathTraversalProtection.sanitizeFilename(originalFilename)
        
        // Generate secure file ID and path
        val fileId = generateSecureFileId()
        val processId = generateProcessId()
        
        val targetDirectory = if (isolated) isolatedDirectory else secureBaseDirectory
        val secureFileName = "$SECURE_PREFIX$fileId-$sanitizedName"
        val securePath = targetDirectory.resolve(secureFileName)
        
        return try {
            // Validate path is secure
            val pathValidation = pathTraversalProtection.validatePath(securePath, secureBaseDirectory)
            if (!pathValidation.isValid) {
                throw SecurityException("Path validation failed: ${pathValidation.message}")
            }
            
            // Create file with secure permissions
            val createdFile = Files.createFile(securePath)
            setSecureFilePermissions(createdFile)
            
            // Track the secure file
            val fileInfo = SecureFileInfo(
                path = createdFile,
                originalName = originalFilename,
                createdAt = Instant.now(),
                processId = processId,
                isIsolated = isolated,
                permissions = SECURE_FILE_PERMISSIONS,
                maxAge = maxAge
            )
            
            secureFiles[fileId] = fileInfo
            
            logger.debug("Created secure file: {} (ID: {}, isolated: {})", 
                createdFile, fileId, isolated)
                
            SecureFileHandle(
                path = createdFile,
                fileId = fileId,
                metadata = mapOf(
                    "originalName" to originalFilename,
                    "processId" to processId,
                    "isolated" to isolated.toString(),
                    "createdAt" to fileInfo.createdAt.toString()
                )
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create secure file for: {}", originalFilename, e)
            throw SecurityException("Cannot create secure file", e)
        }
    }

    /**
     * Creates a secure temporary directory with proper isolation.
     * @param directoryName Name prefix for the directory
     * @param isolated Whether to create in isolated area
     * @return SecureFileHandle for the created directory
     */
    fun createSecureDirectory(
        directoryName: String,
        isolated: Boolean = false
    ): SecureFileHandle {
        
        val sanitizedName = pathTraversalProtection.sanitizeFilename(directoryName)
        val fileId = generateSecureFileId()
        val processId = generateProcessId()
        
        val targetDirectory = if (isolated) isolatedDirectory else secureBaseDirectory
        val secureDirName = "$SECURE_PREFIX$fileId-$sanitizedName"
        val securePath = targetDirectory.resolve(secureDirName)
        
        return try {
            val createdDir = Files.createDirectory(securePath)
            setSecureDirectoryPermissions(createdDir)
            
            val fileInfo = SecureFileInfo(
                path = createdDir,
                originalName = directoryName,
                createdAt = Instant.now(),
                processId = processId,
                isIsolated = isolated,
                permissions = SECURE_DIR_PERMISSIONS,
                maxAge = resourceLimits.maxTempFileAge
            )
            
            secureFiles[fileId] = fileInfo
            
            logger.debug("Created secure directory: {} (ID: {})", createdDir, fileId)
            
            SecureFileHandle(
                path = createdDir,
                fileId = fileId,
                metadata = mapOf(
                    "originalName" to directoryName,
                    "processId" to processId,
                    "type" to "directory",
                    "isolated" to isolated.toString()
                )
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create secure directory: {}", directoryName, e)
            throw SecurityException("Cannot create secure directory", e)
        }
    }

    /**
     * Moves a file to quarantine for security analysis.
     * @param fileHandle The file to quarantine
     * @return New handle for quarantined file
     */
    fun quarantineFile(fileHandle: SecureFileHandle): SecureFileHandle {
        val fileInfo = secureFiles[fileHandle.fileId]
            ?: throw IllegalArgumentException("File not found: ${fileHandle.fileId}")
            
        val quarantineDir = secureBaseDirectory.resolve(QUARANTINE_DIR)
        if (!Files.exists(quarantineDir)) {
            createSecureDirectory(quarantineDir)
        }
        
        val quarantineFileName = "quarantine-${fileHandle.fileId}-${fileInfo.originalName}"
        val quarantinePath = quarantineDir.resolve(quarantineFileName)
        
        return try {
            Files.move(fileHandle.path, quarantinePath, StandardCopyOption.REPLACE_EXISTING)
            setSecureFilePermissions(quarantinePath)
            
            // Update tracking info
            val updatedInfo = fileInfo.copy(path = quarantinePath)
            secureFiles[fileHandle.fileId] = updatedInfo
            
            logger.warn("File quarantined: {} -> {}", fileHandle.path, quarantinePath)
            
            SecureFileHandle(
                path = quarantinePath,
                fileId = fileHandle.fileId,
                isReadOnly = true,
                metadata = fileHandle.metadata + ("quarantined" to "true")
            )
            
        } catch (e: Exception) {
            logger.error("Failed to quarantine file: {}", fileHandle.path, e)
            throw SecurityException("Cannot quarantine file", e)
        }
    }

    /**
     * Securely deletes a temporary file with proper cleanup.
     * @param fileHandle The file handle to delete
     * @return true if successfully deleted
     */
    fun secureDelete(fileHandle: SecureFileHandle): Boolean {
        return try {
            val fileInfo = secureFiles.remove(fileHandle.fileId)
            if (fileInfo != null) {
                secureDeletePath(fileInfo.path)
                logger.debug("Securely deleted file: {}", fileInfo.path)
                true
            } else {
                logger.warn("Attempted to delete unknown file: {}", fileHandle.fileId)
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to securely delete file: {}", fileHandle.path, e)
            false
        }
    }

    /**
     * Validates file integrity and permissions.
     * @param fileHandle The file to validate
     * @return Validation result
     */
    fun validateFileIntegrity(fileHandle: SecureFileHandle): ValidationResult {
        val fileInfo = secureFiles[fileHandle.fileId]
            ?: return ValidationResult.failure("File not tracked: ${fileHandle.fileId}")
            
        try {
            // Check file exists
            if (!Files.exists(fileHandle.path)) {
                return ValidationResult.failure("File does not exist")
            }
            
            // Validate path is still secure
            val pathValidation = pathTraversalProtection.validatePath(fileHandle.path, secureBaseDirectory)
            if (!pathValidation.isValid) {
                return ValidationResult.failure("Path security compromised: ${pathValidation.message}")
            }
            
            // Check permissions (if on POSIX system)
            try {
                val currentPermissions = Files.getPosixFilePermissions(fileHandle.path)
                if (currentPermissions != fileInfo.permissions) {
                    logger.warn("File permissions changed: {} (expected: {}, actual: {})", 
                        fileHandle.path, fileInfo.permissions, currentPermissions)
                    return ValidationResult.failure("File permissions have been modified")
                }
            } catch (e: UnsupportedOperationException) {
                // Not a POSIX system, skip permission check
                logger.debug("Permission validation skipped (not POSIX system)")
            }
            
            // Check file age
            val age = java.time.Duration.between(fileInfo.createdAt, Instant.now())
            if (age > fileInfo.maxAge) {
                return ValidationResult.failure("File has expired (age: ${age.toMinutes()} minutes)")
            }
            
            return ValidationResult.success("File integrity validated")
            
        } catch (e: Exception) {
            logger.error("Error validating file integrity: {}", fileHandle.path, e)
            return ValidationResult.failure("Integrity validation failed: ${e.message}")
        }
    }

    /**
     * Gets statistics about secure file operations.
     */
    fun getSecurityStatistics(): Map<String, Any> {
        val now = Instant.now()
        val totalFiles = secureFiles.size
        val isolatedFiles = secureFiles.values.count { it.isIsolated }
        val expiredFiles = secureFiles.values.count { 
            java.time.Duration.between(it.createdAt, now) > it.maxAge 
        }
        
        var totalSize = 0L
        secureFiles.values.forEach { fileInfo ->
            try {
                if (Files.exists(fileInfo.path)) {
                    totalSize += if (Files.isDirectory(fileInfo.path)) {
                        Files.walk(fileInfo.path).use { paths ->
                            paths.filter { Files.isRegularFile(it) }
                                .mapToLong { Files.size(it) }
                                .sum()
                        }
                    } else {
                        Files.size(fileInfo.path)
                    }
                }
            } catch (e: Exception) {
                logger.debug("Error calculating size for secure file: {}", fileInfo.path, e)
            }
        }
        
        return mapOf(
            "totalSecureFiles" to totalFiles,
            "isolatedFiles" to isolatedFiles,
            "expiredFiles" to expiredFiles,
            "totalSizeBytes" to totalSize,
            "totalSizeMB" to totalSize / (1024 * 1024),
            "secureBaseDirectory" to secureBaseDirectory.toString(),
            "isolatedDirectory" to isolatedDirectory.toString()
        )
    }

    @PreDestroy
    fun cleanup() {
        logger.info("Performing secure cleanup of all temporary files")
        val allFileIds = secureFiles.keys.toList()
        var cleanedCount = 0
        
        allFileIds.forEach { fileId ->
            val fileInfo = secureFiles.remove(fileId)
            if (fileInfo != null && secureDeletePath(fileInfo.path)) {
                cleanedCount++
            }
        }
        
        logger.info("Secure cleanup completed. Cleaned {} files", cleanedCount)
    }

    private fun createSecureDirectory(path: Path) {
        if (!Files.exists(path)) {
            Files.createDirectories(path)
            setSecureDirectoryPermissions(path)
        }
    }

    private fun setSecureFilePermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(path, SECURE_FILE_PERMISSIONS)
        } catch (e: UnsupportedOperationException) {
            logger.debug("POSIX permissions not supported, using default file permissions")
        }
    }

    private fun setSecureDirectoryPermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(path, SECURE_DIR_PERMISSIONS)
        } catch (e: UnsupportedOperationException) {
            logger.debug("POSIX permissions not supported, using default directory permissions")
        }
    }

    private fun generateSecureFileId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateProcessId(): String {
        return "${ProcessHandle.current().pid()}-${System.currentTimeMillis()}"
    }

    private fun secureDeletePath(path: Path): Boolean {
        return try {
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach { 
                            try {
                                Files.deleteIfExists(it)
                            } catch (e: Exception) {
                                logger.debug("Error deleting file during secure cleanup: {}", it, e)
                            }
                        }
                } else {
                    Files.deleteIfExists(path)
                }
                true
            } else {
                true // Already deleted
            }
        } catch (e: Exception) {
            logger.error("Error during secure delete: {}", path, e)
            false
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    ) {
        companion object {
            fun success(message: String): ValidationResult = ValidationResult(true, message)
            fun failure(message: String): ValidationResult = ValidationResult(false, message)
        }
    }
}