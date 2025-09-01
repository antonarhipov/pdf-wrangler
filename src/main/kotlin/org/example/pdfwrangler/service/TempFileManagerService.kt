package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Service for managing temporary files with automatic cleanup scheduling.
 * Provides secure temporary file creation and ensures cleanup to prevent disk space issues.
 */
@Service
class TempFileManagerService {

    private val logger = LoggerFactory.getLogger(TempFileManagerService::class.java)
    private val tempFiles = ConcurrentHashMap<String, TempFileInfo>()
    private val tempDirectory: Path = Paths.get(System.getProperty("java.io.tmpdir"), "pdf-wrangler-temp")

    data class TempFileInfo(
        val path: Path,
        val createdAt: Instant,
        val maxAgeMinutes: Long = 60 // Default 1 hour
    )

    @PostConstruct
    fun initialize() {
        try {
            Files.createDirectories(tempDirectory)
            logger.info("Temporary directory initialized: {}", tempDirectory)
        } catch (e: Exception) {
            logger.error("Failed to create temporary directory: {}", tempDirectory, e)
            throw IllegalStateException("Cannot initialize temporary file management", e)
        }
    }

    /**
     * Creates a temporary file with automatic cleanup tracking.
     * @param prefix File name prefix
     * @param suffix File extension (e.g., ".pdf", ".tmp")
     * @param maxAgeMinutes Maximum age before cleanup (default 60 minutes)
     * @return Path to the created temporary file
     */
    fun createTempFile(prefix: String, suffix: String, maxAgeMinutes: Long = 60): Path {
        return try {
            val tempFile = Files.createTempFile(tempDirectory, prefix, suffix)
            val fileInfo = TempFileInfo(tempFile, Instant.now(), maxAgeMinutes)
            tempFiles[tempFile.toString()] = fileInfo
            
            logger.debug("Created temporary file: {} (will expire in {} minutes)", tempFile, maxAgeMinutes)
            tempFile
        } catch (e: Exception) {
            logger.error("Failed to create temporary file with prefix: {}, suffix: {}", prefix, suffix, e)
            throw RuntimeException("Cannot create temporary file", e)
        }
    }

    /**
     * Creates a temporary directory with automatic cleanup tracking.
     * @param prefix Directory name prefix
     * @param maxAgeMinutes Maximum age before cleanup (default 60 minutes)
     * @return Path to the created temporary directory
     */
    fun createTempDirectory(prefix: String, maxAgeMinutes: Long = 60): Path {
        return try {
            val tempDir = Files.createTempDirectory(tempDirectory, prefix)
            val dirInfo = TempFileInfo(tempDir, Instant.now(), maxAgeMinutes)
            tempFiles[tempDir.toString()] = dirInfo
            
            logger.debug("Created temporary directory: {} (will expire in {} minutes)", tempDir, maxAgeMinutes)
            tempDir
        } catch (e: Exception) {
            logger.error("Failed to create temporary directory with prefix: {}", prefix, e)
            throw RuntimeException("Cannot create temporary directory", e)
        }
    }

    /**
     * Manually removes a temporary file or directory from tracking and filesystem.
     * @param path Path to the temporary file/directory to remove
     * @return true if successfully removed, false otherwise
     */
    fun removeTempFile(path: Path): Boolean {
        return try {
            tempFiles.remove(path.toString())
            val deleted = deleteRecursively(path)
            if (deleted) {
                logger.debug("Manually removed temporary file: {}", path)
            } else {
                logger.warn("Failed to delete temporary file: {}", path)
            }
            deleted
        } catch (e: Exception) {
            logger.error("Error removing temporary file: {}", path, e)
            false
        }
    }

    /**
     * Scheduled cleanup task that runs every 30 minutes to remove expired temporary files.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // Every 30 minutes
    fun cleanupExpiredFiles() {
        val now = Instant.now()
        val expiredFiles = mutableListOf<String>()

        tempFiles.forEach { (pathString, fileInfo) ->
            val ageMinutes = java.time.Duration.between(fileInfo.createdAt, now).toMinutes()
            if (ageMinutes >= fileInfo.maxAgeMinutes) {
                expiredFiles.add(pathString)
            }
        }

        var removedCount = 0
        expiredFiles.forEach { pathString ->
            tempFiles.remove(pathString)
            val path = Paths.get(pathString)
            if (deleteRecursively(path)) {
                removedCount++
                logger.debug("Cleaned up expired temporary file: {}", path)
            } else {
                logger.warn("Failed to delete expired temporary file: {}", path)
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} expired temporary files", removedCount)
        }
    }

    /**
     * Emergency cleanup that removes all tracked temporary files.
     * This is called during application shutdown.
     */
    @PreDestroy
    fun cleanupAllFiles() {
        logger.info("Performing emergency cleanup of all temporary files")
        val allPaths = tempFiles.keys.toList()
        var removedCount = 0

        allPaths.forEach { pathString ->
            tempFiles.remove(pathString)
            val path = Paths.get(pathString)
            if (deleteRecursively(path)) {
                removedCount++
            }
        }

        logger.info("Emergency cleanup completed. Removed {} temporary files", removedCount)
    }

    /**
     * Gets statistics about current temporary files.
     * @return Map containing statistics
     */
    fun getStatistics(): Map<String, Any> {
        val now = Instant.now()
        val totalFiles = tempFiles.size
        var totalSizeBytes = 0L
        var oldestFileAge = 0L

        tempFiles.values.forEach { fileInfo ->
            try {
                if (Files.exists(fileInfo.path)) {
                    totalSizeBytes += if (Files.isDirectory(fileInfo.path)) {
                        Files.walk(fileInfo.path).use { paths ->
                            paths.filter { Files.isRegularFile(it) }
                                .mapToLong { Files.size(it) }
                                .sum()
                        }
                    } else {
                        Files.size(fileInfo.path)
                    }
                }
                val ageMinutes = java.time.Duration.between(fileInfo.createdAt, now).toMinutes()
                if (ageMinutes > oldestFileAge) {
                    oldestFileAge = ageMinutes
                }
            } catch (e: Exception) {
                logger.debug("Error calculating size for temp file: {}", fileInfo.path, e)
            }
        }

        return mapOf(
            "totalFiles" to totalFiles,
            "totalSizeBytes" to totalSizeBytes,
            "totalSizeMB" to totalSizeBytes / (1024 * 1024),
            "oldestFileAgeMinutes" to oldestFileAge,
            "tempDirectory" to tempDirectory.toString()
        )
    }

    private fun deleteRecursively(path: Path): Boolean {
        return try {
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                } else {
                    Files.deleteIfExists(path)
                }
                true
            } else {
                true // Already deleted
            }
        } catch (e: Exception) {
            logger.error("Error deleting path recursively: {}", path, e)
            false
        }
    }
}