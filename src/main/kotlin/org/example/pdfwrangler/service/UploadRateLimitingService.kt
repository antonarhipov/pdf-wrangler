package org.example.pdfwrangler.service

import org.example.pdfwrangler.config.ConfigurableResourceLimits
import org.example.pdfwrangler.exception.RateLimitSecurityException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import jakarta.servlet.http.HttpServletRequest
import jakarta.annotation.PostConstruct

/**
 * Service for implementing upload rate limiting to prevent abuse and DoS attacks.
 * Provides multiple rate limiting strategies including per-IP limits, sliding window algorithms,
 * and bandwidth throttling.
 */
@Service
class UploadRateLimitingService(
    private val resourceLimits: ConfigurableResourceLimits
) {

    private val logger = LoggerFactory.getLogger(UploadRateLimitingService::class.java)

    // Rate limit tracking data structures
    private val requestCounters = ConcurrentHashMap<String, RequestCounter>()
    private val uploadCounters = ConcurrentHashMap<String, UploadCounter>()
    private val operationCounters = ConcurrentHashMap<String, OperationCounter>()
    private val bandwidthCounters = ConcurrentHashMap<String, BandwidthCounter>()

    // Global rate limiting counters
    private val globalRequestCounter = AtomicInteger(0)
    private val globalUploadCounter = AtomicLong(0)
    private val lastGlobalReset = AtomicLong(System.currentTimeMillis())

    companion object {
        private const val SLIDING_WINDOW_SIZE_MINUTES = 60
        private const val CLEANUP_INTERVAL_MINUTES = 15
        private const val MAX_TRACKED_IPS = 10000
        
        // Rate limit window sizes in milliseconds
        private const val MINUTE_WINDOW = 60 * 1000L
        private const val HOUR_WINDOW = 60 * MINUTE_WINDOW
        private const val DAY_WINDOW = 24 * HOUR_WINDOW
    }

    data class RequestCounter(
        val clientId: String,
        val minuteRequests: SlidingWindow = SlidingWindow(MINUTE_WINDOW),
        val hourRequests: SlidingWindow = SlidingWindow(HOUR_WINDOW),
        val dailyRequests: SlidingWindow = SlidingWindow(DAY_WINDOW),
        val createdAt: Long = System.currentTimeMillis()
    )

    data class UploadCounter(
        val clientId: String,
        val minuteUploads: SlidingWindow = SlidingWindow(MINUTE_WINDOW),
        val hourUploads: SlidingWindow = SlidingWindow(HOUR_WINDOW),
        val dailyUploads: SlidingWindow = SlidingWindow(DAY_WINDOW),
        val totalBytes: AtomicLong = AtomicLong(0),
        val createdAt: Long = System.currentTimeMillis()
    )

    data class OperationCounter(
        val clientId: String,
        val minuteOperations: SlidingWindow = SlidingWindow(MINUTE_WINDOW),
        val hourOperations: SlidingWindow = SlidingWindow(HOUR_WINDOW),
        val dailyOperations: SlidingWindow = SlidingWindow(DAY_WINDOW),
        val createdAt: Long = System.currentTimeMillis()
    )

    data class BandwidthCounter(
        val clientId: String,
        val minuteBandwidth: SlidingWindow = SlidingWindow(MINUTE_WINDOW),
        val hourBandwidth: SlidingWindow = SlidingWindow(HOUR_WINDOW),
        val totalBandwidth: AtomicLong = AtomicLong(0),
        val createdAt: Long = System.currentTimeMillis()
    )

    data class SlidingWindow(
        val windowSizeMs: Long,
        private val requests: MutableList<Long> = mutableListOf()
    ) {
        @Synchronized
        fun addRequest(timestamp: Long = System.currentTimeMillis()) {
            requests.add(timestamp)
            cleanup(timestamp)
        }

        @Synchronized
        fun addBytes(bytes: Long, timestamp: Long = System.currentTimeMillis()) {
            requests.add(timestamp + bytes) // Store timestamp with byte count encoded
            cleanup(timestamp)
        }

        @Synchronized
        fun getCount(currentTime: Long = System.currentTimeMillis()): Int {
            cleanup(currentTime)
            return requests.size
        }

        @Synchronized
        fun getBytes(currentTime: Long = System.currentTimeMillis()): Long {
            cleanup(currentTime)
            return requests.sumOf { if (it > currentTime) it - currentTime else 0 }
        }

        @Synchronized
        private fun cleanup(currentTime: Long) {
            val cutoff = currentTime - windowSizeMs
            requests.removeIf { it <= cutoff }
        }
    }

    data class RateLimitStatus(
        val isAllowed: Boolean,
        val limitType: RateLimitSecurityException.RateLimitType,
        val currentCount: Int,
        val maxAllowed: Int,
        val resetTimeSeconds: Long,
        val message: String
    )

    @PostConstruct
    fun initialize() {
        logger.info("Upload rate limiting service initialized with limits: " +
            "requests/min={}, requests/hour={}, daily operations={}",
            resourceLimits.maxRequestsPerMinute,
            resourceLimits.maxRequestsPerHour,
            resourceLimits.maxDailyOperations)
    }

    /**
     * Checks if a request is allowed based on rate limits.
     * @param request HTTP request to extract client information
     * @return RateLimitStatus indicating if request is allowed
     */
    fun checkRequestRateLimit(request: HttpServletRequest): RateLimitStatus {
        val clientId = extractClientIdentifier(request)
        return checkRequestRateLimit(clientId)
    }

    /**
     * Checks if a request is allowed for a specific client ID.
     * @param clientId Client identifier (IP address, user ID, etc.)
     * @return RateLimitStatus indicating if request is allowed
     */
    fun checkRequestRateLimit(clientId: String): RateLimitStatus {
        val counter = requestCounters.computeIfAbsent(clientId) { RequestCounter(it) }
        val currentTime = System.currentTimeMillis()

        // Check minute limit
        val minuteCount = counter.minuteRequests.getCount(currentTime)
        if (minuteCount >= resourceLimits.maxRequestsPerMinute) {
            return RateLimitStatus(
                isAllowed = false,
                limitType = RateLimitSecurityException.RateLimitType.REQUEST_RATE,
                currentCount = minuteCount,
                maxAllowed = resourceLimits.maxRequestsPerMinute,
                resetTimeSeconds = 60 - ((currentTime % MINUTE_WINDOW) / 1000),
                message = "Request rate limit exceeded: $minuteCount requests in the last minute"
            )
        }

        // Check hour limit
        val hourCount = counter.hourRequests.getCount(currentTime)
        if (hourCount >= resourceLimits.maxRequestsPerHour) {
            return RateLimitStatus(
                isAllowed = false,
                limitType = RateLimitSecurityException.RateLimitType.REQUEST_RATE,
                currentCount = hourCount,
                maxAllowed = resourceLimits.maxRequestsPerHour,
                resetTimeSeconds = 3600 - ((currentTime % HOUR_WINDOW) / 1000),
                message = "Hourly request limit exceeded: $hourCount requests in the last hour"
            )
        }

        // Record the request
        counter.minuteRequests.addRequest(currentTime)
        counter.hourRequests.addRequest(currentTime)
        counter.dailyRequests.addRequest(currentTime)

        return RateLimitStatus(
            isAllowed = true,
            limitType = RateLimitSecurityException.RateLimitType.REQUEST_RATE,
            currentCount = minuteCount + 1,
            maxAllowed = resourceLimits.maxRequestsPerMinute,
            resetTimeSeconds = 60 - ((currentTime % MINUTE_WINDOW) / 1000),
            message = "Request allowed"
        )
    }

    /**
     * Checks if an upload is allowed based on upload rate limits.
     * @param clientId Client identifier
     * @param uploadSizeBytes Size of the upload in bytes
     * @return RateLimitStatus indicating if upload is allowed
     */
    fun checkUploadRateLimit(clientId: String, uploadSizeBytes: Long): RateLimitStatus {
        val counter = uploadCounters.computeIfAbsent(clientId) { UploadCounter(it) }
        val currentTime = System.currentTimeMillis()

        // Check upload count limits (treat large uploads as multiple uploads)
        val uploadCount = maxOf(1, (uploadSizeBytes / (10 * 1024 * 1024)).toInt()) // 1 count per 10MB
        val minuteUploads = counter.minuteUploads.getCount(currentTime)
        val maxMinuteUploads = resourceLimits.maxRequestsPerMinute / 2 // Half of request limit for uploads

        if (minuteUploads + uploadCount > maxMinuteUploads) {
            return RateLimitStatus(
                isAllowed = false,
                limitType = RateLimitSecurityException.RateLimitType.UPLOAD_RATE,
                currentCount = minuteUploads,
                maxAllowed = maxMinuteUploads,
                resetTimeSeconds = 60 - ((currentTime % MINUTE_WINDOW) / 1000),
                message = "Upload rate limit exceeded: too many uploads in the last minute"
            )
        }

        // Check bandwidth limits (bytes per minute)
        val maxBytesPerMinute = 50L * 1024 * 1024 // 50MB per minute
        val bandwidthCounter = bandwidthCounters.computeIfAbsent(clientId) { BandwidthCounter(it) }
        val minuteBandwidth = bandwidthCounter.minuteBandwidth.getBytes(currentTime)
        
        if (minuteBandwidth + uploadSizeBytes > maxBytesPerMinute) {
            return RateLimitStatus(
                isAllowed = false,
                limitType = RateLimitSecurityException.RateLimitType.BANDWIDTH_RATE,
                currentCount = (minuteBandwidth / 1024 / 1024).toInt(),
                maxAllowed = (maxBytesPerMinute / 1024 / 1024).toInt(),
                resetTimeSeconds = 60 - ((currentTime % MINUTE_WINDOW) / 1000),
                message = "Bandwidth limit exceeded: ${minuteBandwidth / 1024 / 1024}MB in the last minute"
            )
        }

        // Record the upload
        repeat(uploadCount) {
            counter.minuteUploads.addRequest(currentTime)
            counter.hourUploads.addRequest(currentTime)
            counter.dailyUploads.addRequest(currentTime)
        }
        bandwidthCounter.minuteBandwidth.addBytes(uploadSizeBytes, currentTime)
        bandwidthCounter.hourBandwidth.addBytes(uploadSizeBytes, currentTime)
        counter.totalBytes.addAndGet(uploadSizeBytes)

        return RateLimitStatus(
            isAllowed = true,
            limitType = RateLimitSecurityException.RateLimitType.UPLOAD_RATE,
            currentCount = minuteUploads + uploadCount,
            maxAllowed = maxMinuteUploads,
            resetTimeSeconds = 60 - ((currentTime % MINUTE_WINDOW) / 1000),
            message = "Upload allowed"
        )
    }

    /**
     * Checks if an operation is allowed based on operation rate limits.
     * @param clientId Client identifier
     * @param operationType Type of operation being performed
     * @return RateLimitStatus indicating if operation is allowed
     */
    fun checkOperationRateLimit(clientId: String, operationType: String): RateLimitStatus {
        val counter = operationCounters.computeIfAbsent(clientId) { OperationCounter(it) }
        val currentTime = System.currentTimeMillis()

        // Check daily operation limit
        val dailyOps = counter.dailyOperations.getCount(currentTime)
        if (dailyOps >= resourceLimits.maxDailyOperations) {
            return RateLimitStatus(
                isAllowed = false,
                limitType = RateLimitSecurityException.RateLimitType.OPERATION_RATE,
                currentCount = dailyOps,
                maxAllowed = resourceLimits.maxDailyOperations,
                resetTimeSeconds = (DAY_WINDOW - (currentTime % DAY_WINDOW)) / 1000,
                message = "Daily operation limit exceeded: $dailyOps operations today"
            )
        }

        // Check hourly operation limit (percentage of daily limit)
        val maxHourlyOps = resourceLimits.maxDailyOperations / 12 // Allow burst of 12 hours worth
        val hourlyOps = counter.hourOperations.getCount(currentTime)
        if (hourlyOps >= maxHourlyOps) {
            return RateLimitStatus(
                isAllowed = false,
                limitType = RateLimitSecurityException.RateLimitType.OPERATION_RATE,
                currentCount = hourlyOps,
                maxAllowed = maxHourlyOps,
                resetTimeSeconds = 3600 - ((currentTime % HOUR_WINDOW) / 1000),
                message = "Hourly operation limit exceeded: $hourlyOps operations in the last hour"
            )
        }

        // Record the operation
        counter.minuteOperations.addRequest(currentTime)
        counter.hourOperations.addRequest(currentTime)
        counter.dailyOperations.addRequest(currentTime)

        logger.debug("Operation '{}' allowed for client: {} (daily: {}/{})", 
            operationType, clientId, dailyOps + 1, resourceLimits.maxDailyOperations)

        return RateLimitStatus(
            isAllowed = true,
            limitType = RateLimitSecurityException.RateLimitType.OPERATION_RATE,
            currentCount = dailyOps + 1,
            maxAllowed = resourceLimits.maxDailyOperations,
            resetTimeSeconds = (DAY_WINDOW - (currentTime % DAY_WINDOW)) / 1000,
            message = "Operation allowed"
        )
    }

    /**
     * Enforces rate limit by throwing exception if limit is exceeded.
     * @param request HTTP request
     * @param uploadSizeBytes Optional upload size for upload rate limiting
     * @param operationType Optional operation type for operation rate limiting
     * @throws RateLimitSecurityException if rate limit is exceeded
     */
    fun enforceRateLimit(
        request: HttpServletRequest,
        uploadSizeBytes: Long? = null,
        operationType: String? = null
    ) {
        val clientId = extractClientIdentifier(request)
        
        // Check request rate limit
        val requestStatus = checkRequestRateLimit(clientId)
        if (!requestStatus.isAllowed) {
            logger.warn("Rate limit exceeded for client {}: {}", clientId, requestStatus.message)
            throw RateLimitSecurityException(
                message = requestStatus.message,
                clientIdentifier = clientId,
                rateLimitType = requestStatus.limitType,
                currentCount = requestStatus.currentCount,
                maxAllowed = requestStatus.maxAllowed,
                resetTimeSeconds = requestStatus.resetTimeSeconds
            )
        }

        // Check upload rate limit if applicable
        uploadSizeBytes?.let { size ->
            val uploadStatus = checkUploadRateLimit(clientId, size)
            if (!uploadStatus.isAllowed) {
                logger.warn("Upload rate limit exceeded for client {}: {}", clientId, uploadStatus.message)
                throw RateLimitSecurityException(
                    message = uploadStatus.message,
                    clientIdentifier = clientId,
                    rateLimitType = uploadStatus.limitType,
                    currentCount = uploadStatus.currentCount,
                    maxAllowed = uploadStatus.maxAllowed,
                    resetTimeSeconds = uploadStatus.resetTimeSeconds
                )
            }
        }

        // Check operation rate limit if applicable
        operationType?.let { opType ->
            val operationStatus = checkOperationRateLimit(clientId, opType)
            if (!operationStatus.isAllowed) {
                logger.warn("Operation rate limit exceeded for client {}: {}", clientId, operationStatus.message)
                throw RateLimitSecurityException(
                    message = operationStatus.message,
                    clientIdentifier = clientId,
                    rateLimitType = operationStatus.limitType,
                    currentCount = operationStatus.currentCount,
                    maxAllowed = operationStatus.maxAllowed,
                    resetTimeSeconds = operationStatus.resetTimeSeconds
                )
            }
        }
    }

    /**
     * Gets rate limit statistics for a client.
     * @param clientId Client identifier
     * @return Map containing rate limit statistics
     */
    fun getRateLimitStatistics(clientId: String): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val requestCounter = requestCounters[clientId]
        val uploadCounter = uploadCounters[clientId]
        val operationCounter = operationCounters[clientId]

        return mapOf(
            "clientId" to clientId,
            "requests" to mapOf(
                "lastMinute" to (requestCounter?.minuteRequests?.getCount(currentTime) ?: 0),
                "lastHour" to (requestCounter?.hourRequests?.getCount(currentTime) ?: 0),
                "lastDay" to (requestCounter?.dailyRequests?.getCount(currentTime) ?: 0)
            ),
            "uploads" to mapOf(
                "lastMinute" to (uploadCounter?.minuteUploads?.getCount(currentTime) ?: 0),
                "lastHour" to (uploadCounter?.hourUploads?.getCount(currentTime) ?: 0),
                "totalBytes" to (uploadCounter?.totalBytes?.get() ?: 0)
            ),
            "operations" to mapOf(
                "lastMinute" to (operationCounter?.minuteOperations?.getCount(currentTime) ?: 0),
                "lastHour" to (operationCounter?.hourOperations?.getCount(currentTime) ?: 0),
                "lastDay" to (operationCounter?.dailyOperations?.getCount(currentTime) ?: 0)
            ),
            "limits" to mapOf(
                "maxRequestsPerMinute" to resourceLimits.maxRequestsPerMinute,
                "maxRequestsPerHour" to resourceLimits.maxRequestsPerHour,
                "maxDailyOperations" to resourceLimits.maxDailyOperations
            )
        )
    }

    /**
     * Gets global rate limiting statistics.
     * @return Map containing global statistics
     */
    fun getGlobalStatistics(): Map<String, Any> {
        return mapOf(
            "totalTrackedIPs" to requestCounters.size,
            "totalUploadCounters" to uploadCounters.size,
            "totalOperationCounters" to operationCounters.size,
            "globalRequestCount" to globalRequestCounter.get(),
            "globalUploadBytes" to globalUploadCounter.get(),
            "memoryUsageEstimate" to estimateMemoryUsage()
        )
    }

    /**
     * Scheduled cleanup task to remove old entries and prevent memory leaks.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MINUTES * 60 * 1000L)
    fun cleanupOldEntries() {
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - (24 * HOUR_WINDOW) // Keep 24 hours of data
        var removedCount = 0

        // Cleanup request counters
        val requestIterator = requestCounters.iterator()
        while (requestIterator.hasNext()) {
            val entry = requestIterator.next()
            if (entry.value.createdAt < cutoffTime && 
                entry.value.dailyRequests.getCount(currentTime) == 0) {
                requestIterator.remove()
                removedCount++
            }
        }

        // Cleanup upload counters
        val uploadIterator = uploadCounters.iterator()
        while (uploadIterator.hasNext()) {
            val entry = uploadIterator.next()
            if (entry.value.createdAt < cutoffTime &&
                entry.value.dailyUploads.getCount(currentTime) == 0) {
                uploadIterator.remove()
                removedCount++
            }
        }

        // Cleanup operation counters
        val operationIterator = operationCounters.iterator()
        while (operationIterator.hasNext()) {
            val entry = operationIterator.next()
            if (entry.value.createdAt < cutoffTime &&
                entry.value.dailyOperations.getCount(currentTime) == 0) {
                operationIterator.remove()
                removedCount++
            }
        }

        // Limit total tracked IPs to prevent memory exhaustion
        if (requestCounters.size > MAX_TRACKED_IPS) {
            val sortedEntries = requestCounters.entries.sortedBy { it.value.createdAt }
            val entriesToRemove = sortedEntries.take(requestCounters.size - MAX_TRACKED_IPS)
            entriesToRemove.forEach { requestCounters.remove(it.key) }
            removedCount += entriesToRemove.size
        }

        if (removedCount > 0) {
            logger.info("Rate limiting cleanup completed: removed {} old entries", removedCount)
        }
    }

    private fun extractClientIdentifier(request: HttpServletRequest): String {
        // Try to get real IP address considering proxies
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        val xRealIp = request.getHeader("X-Real-IP")
        
        return when {
            !xForwardedFor.isNullOrBlank() -> xForwardedFor.split(",")[0].trim()
            !xRealIp.isNullOrBlank() -> xRealIp.trim()
            else -> request.remoteAddr ?: "unknown"
        }
    }

    private fun estimateMemoryUsage(): Long {
        // Rough estimate of memory usage for monitoring
        val counterSize = 1024L // Estimated bytes per counter
        return (requestCounters.size + uploadCounters.size + operationCounters.size) * counterSize
    }
}