package org.example.pdfwrangler.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Duration

/**
 * Configuration class for managing file size and processing limits dynamically.
 * Provides configurable resource constraints to prevent abuse and resource exhaustion.
 */
@Component
@ConfigurationProperties(prefix = "pdf-wrangler.resource-limits")
@Validated
data class ConfigurableResourceLimits(
    
    /**
     * File size limits
     */
    @field:NotNull
    @field:Min(1)
    var maxFileSize: Long = 100L * 1024 * 1024, // 100MB default
    
    @field:NotNull
    @field:Min(1)
    var maxRequestSize: Long = 200L * 1024 * 1024, // 200MB default
    
    @field:NotNull
    @field:Min(1)
    var maxBatchSize: Int = 20, // Maximum files per batch operation
    
    @field:NotNull
    @field:Min(1)
    var maxTotalBatchSize: Long = 500L * 1024 * 1024, // 500MB total batch size
    
    /**
     * Processing time limits
     */
    @field:NotNull
    var maxProcessingTime: Duration = Duration.ofMinutes(10), // 10 minutes default
    
    @field:NotNull
    var maxUploadTime: Duration = Duration.ofMinutes(5), // 5 minutes upload timeout
    
    @field:NotNull
    var maxBatchProcessingTime: Duration = Duration.ofMinutes(30), // 30 minutes for batch operations
    
    /**
     * Memory limits
     */
    @field:NotNull
    @field:Min(1)
    var maxMemoryUsage: Long = 512L * 1024 * 1024, // 512MB memory limit per operation
    
    @field:NotNull
    @field:Min(1)
    var maxConcurrentOperations: Int = 5, // Maximum concurrent PDF operations
    
    @field:NotNull
    var maxTempFileAge: Duration = Duration.ofHours(2), // 2 hours temp file retention
    
    /**
     * Page and content limits
     */
    @field:NotNull
    @field:Min(1)
    var maxPagesPerDocument: Int = 5000, // Maximum pages in a single PDF
    
    @field:NotNull
    @field:Min(1)
    var maxTotalPagesInBatch: Int = 10000, // Maximum total pages in batch operation
    
    @field:NotNull
    @field:Min(1)
    var maxImageResolution: Long = 4096L * 4096L, // Maximum image resolution (pixels)
    
    @field:NotNull
    @field:Min(1)
    var maxTextExtractionLength: Int = 10 * 1024 * 1024, // 10MB text extraction limit
    
    /**
     * Rate limiting
     */
    @field:NotNull
    @field:Min(1)
    var maxRequestsPerMinute: Int = 60, // Rate limiting per IP
    
    @field:NotNull
    @field:Min(1)
    var maxRequestsPerHour: Int = 1000, // Hourly rate limit per IP
    
    @field:NotNull
    @field:Min(1)
    var maxDailyOperations: Int = 10000, // Daily operations limit per IP
    
    /**
     * Resource cleanup settings
     */
    @field:NotNull
    var tempFileCleanupInterval: Duration = Duration.ofMinutes(30), // Cleanup every 30 minutes
    
    @field:NotNull
    @field:Min(1)
    var maxDiskUsage: Long = 10L * 1024 * 1024 * 1024, // 10GB disk usage limit
    
    @field:NotNull
    @field:Min(1)
    var maxTempFiles: Int = 1000, // Maximum number of temp files
    
    /**
     * Security limits
     */
    @field:NotNull
    @field:Min(1)
    var maxContentScanSize: Long = 50L * 1024 * 1024, // 50MB max for malicious content scanning
    
    @field:NotNull
    @field:Min(1)
    var maxArchiveDepth: Int = 3, // Maximum nesting depth for archive scanning
    
    @field:NotNull
    @field:Min(1)
    var maxEmbeddedFiles: Int = 100, // Maximum embedded files in a document
    
    /**
     * Performance thresholds
     */
    @field:NotNull
    @field:Min(1)
    var warningMemoryThreshold: Long = 256L * 1024 * 1024, // 256MB warning threshold
    
    @field:NotNull
    var warningProcessingTime: Duration = Duration.ofMinutes(5), // 5 minutes warning threshold
    
    @field:NotNull
    @field:Min(1)
    var maxRetryAttempts: Int = 3, // Maximum retry attempts for failed operations
    
    @field:NotNull
    var retryDelay: Duration = Duration.ofSeconds(30) // Delay between retry attempts
) {

    /**
     * Validates if a file size is within limits
     */
    fun isFileSizeAllowed(size: Long): Boolean {
        return size <= maxFileSize && size > 0
    }

    /**
     * Validates if a batch size is within limits
     */
    fun isBatchSizeAllowed(fileCount: Int, totalSize: Long): Boolean {
        return fileCount <= maxBatchSize && totalSize <= maxTotalBatchSize
    }

    /**
     * Validates if processing can start based on current load
     */
    fun canStartProcessing(currentOperations: Int): Boolean {
        return currentOperations < maxConcurrentOperations
    }

    /**
     * Gets the appropriate processing timeout based on operation type
     */
    fun getProcessingTimeout(isBatchOperation: Boolean): Duration {
        return if (isBatchOperation) maxBatchProcessingTime else maxProcessingTime
    }

    /**
     * Checks if memory usage is within safe limits
     */
    fun isMemoryUsageSafe(currentUsage: Long): Boolean {
        return currentUsage <= maxMemoryUsage
    }

    /**
     * Checks if memory usage has reached warning threshold
     */
    fun isMemoryWarningThreshold(currentUsage: Long): Boolean {
        return currentUsage >= warningMemoryThreshold
    }

    /**
     * Validates if page count is within limits
     */
    fun isPageCountAllowed(pages: Int, isBatchOperation: Boolean = false): Boolean {
        return if (isBatchOperation) {
            pages <= maxTotalPagesInBatch
        } else {
            pages <= maxPagesPerDocument
        }
    }

    /**
     * Validates if image resolution is within limits
     */
    fun isImageResolutionAllowed(width: Int, height: Int): Boolean {
        val totalPixels = width.toLong() * height.toLong()
        return totalPixels <= maxImageResolution && width > 0 && height > 0
    }

    /**
     * Gets rate limiting information for requests
     */
    fun getRateLimitInfo(): RateLimitInfo {
        return RateLimitInfo(
            requestsPerMinute = maxRequestsPerMinute,
            requestsPerHour = maxRequestsPerHour,
            dailyOperations = maxDailyOperations
        )
    }

    /**
     * Validates if content size is suitable for security scanning
     */
    fun canScanContent(size: Long): Boolean {
        return size <= maxContentScanSize
    }

    /**
     * Gets cleanup configuration
     */
    fun getCleanupConfig(): CleanupConfig {
        return CleanupConfig(
            interval = tempFileCleanupInterval,
            maxAge = maxTempFileAge,
            maxDiskUsage = maxDiskUsage,
            maxTempFiles = maxTempFiles
        )
    }

    /**
     * Data class for rate limiting information
     */
    data class RateLimitInfo(
        val requestsPerMinute: Int,
        val requestsPerHour: Int,
        val dailyOperations: Int
    )

    /**
     * Data class for cleanup configuration
     */
    data class CleanupConfig(
        val interval: Duration,
        val maxAge: Duration,
        val maxDiskUsage: Long,
        val maxTempFiles: Int
    )

    /**
     * Validates all resource limits on startup
     */
    fun validateLimits(): List<String> {
        val issues = mutableListOf<String>()

        if (maxFileSize <= 0) issues.add("maxFileSize must be positive")
        if (maxRequestSize < maxFileSize) issues.add("maxRequestSize should be >= maxFileSize")
        if (maxBatchSize <= 0) issues.add("maxBatchSize must be positive")
        if (maxTotalBatchSize < maxFileSize) issues.add("maxTotalBatchSize should be >= maxFileSize")
        
        if (maxProcessingTime.isNegative || maxProcessingTime.isZero) {
            issues.add("maxProcessingTime must be positive")
        }
        if (maxUploadTime.isNegative || maxUploadTime.isZero) {
            issues.add("maxUploadTime must be positive")
        }
        if (maxBatchProcessingTime < maxProcessingTime) {
            issues.add("maxBatchProcessingTime should be >= maxProcessingTime")
        }
        
        if (maxMemoryUsage <= 0) issues.add("maxMemoryUsage must be positive")
        if (maxConcurrentOperations <= 0) issues.add("maxConcurrentOperations must be positive")
        if (maxPagesPerDocument <= 0) issues.add("maxPagesPerDocument must be positive")
        if (maxTotalPagesInBatch < maxPagesPerDocument) {
            issues.add("maxTotalPagesInBatch should be >= maxPagesPerDocument")
        }
        
        if (warningMemoryThreshold > maxMemoryUsage) {
            issues.add("warningMemoryThreshold should not exceed maxMemoryUsage")
        }
        
        return issues
    }
}