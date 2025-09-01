package org.example.pdfwrangler.service

import org.example.pdfwrangler.exception.PdfOperationException
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Service responsible for handling error recovery mechanisms for transient failures.
 * Provides retry logic, circuit breaker patterns, and fallback strategies.
 */
@Service
class ErrorRecoveryService(
    private val operationContextLogger: OperationContextLogger
) {
    private val logger = LoggerFactory.getLogger(ErrorRecoveryService::class.java)
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val executor = Executors.newCachedThreadPool()

    /**
     * Recovery strategies for different types of failures
     */
    enum class RecoveryStrategy {
        IMMEDIATE_RETRY,
        EXPONENTIAL_BACKOFF,
        CIRCUIT_BREAKER,
        FALLBACK_ONLY,
        NO_RECOVERY
    }

    /**
     * Configuration for retry operations
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelay: Duration = Duration.ofSeconds(1),
        val maxDelay: Duration = Duration.ofMinutes(1),
        val multiplier: Double = 2.0,
        val jitter: Boolean = true,
        val retryableExceptions: Set<Class<out Exception>> = setOf(
            PdfOperationException::class.java,
            TimeoutException::class.java,
            java.io.IOException::class.java
        )
    )

    /**
     * Circuit breaker for preventing cascading failures
     */
    class CircuitBreaker(
        private val name: String,
        private val failureThreshold: Int = 5,
        private val recoveryTimeout: Duration = Duration.ofMinutes(1),
        private val successThreshold: Int = 3
    ) {
        enum class State { CLOSED, OPEN, HALF_OPEN }
        
        private var state = State.CLOSED
        private val failureCount = AtomicInteger(0)
        private val successCount = AtomicInteger(0)
        private val lastFailureTime = AtomicLong(0)
        private val logger = LoggerFactory.getLogger(CircuitBreaker::class.java)

        fun canExecute(): Boolean {
            when (state) {
                State.CLOSED -> return true
                State.OPEN -> {
                    if (System.currentTimeMillis() - lastFailureTime.get() > recoveryTimeout.toMillis()) {
                        state = State.HALF_OPEN
                        successCount.set(0)
                        logger.info("Circuit breaker [$name] transitioning to HALF_OPEN state")
                        return true
                    }
                    return false
                }
                State.HALF_OPEN -> return true
            }
        }

        fun onSuccess() {
            when (state) {
                State.CLOSED -> failureCount.set(0)
                State.HALF_OPEN -> {
                    if (successCount.incrementAndGet() >= successThreshold) {
                        state = State.CLOSED
                        failureCount.set(0)
                        logger.info("Circuit breaker [$name] transitioning to CLOSED state")
                    }
                }
                State.OPEN -> {
                    // Should not happen, but reset if it does
                    state = State.CLOSED
                    failureCount.set(0)
                }
            }
        }

        fun onFailure() {
            lastFailureTime.set(System.currentTimeMillis())
            when (state) {
                State.CLOSED -> {
                    if (failureCount.incrementAndGet() >= failureThreshold) {
                        state = State.OPEN
                        logger.warn("Circuit breaker [$name] transitioning to OPEN state after $failureThreshold failures")
                    }
                }
                State.HALF_OPEN -> {
                    state = State.OPEN
                    logger.warn("Circuit breaker [$name] transitioning back to OPEN state")
                }
                State.OPEN -> {
                    // Already open, just update timestamp
                }
            }
        }

        fun getState() = state
        fun getFailureCount() = failureCount.get()
    }

    /**
     * Execute operation with recovery mechanisms
     */
    fun <T> executeWithRecovery(
        operationName: String,
        operation: () -> T,
        fallback: (() -> T)? = null,
        config: RetryConfig = RetryConfig()
    ): T {
        val circuitBreaker = getOrCreateCircuitBreaker(operationName)
        
        if (!circuitBreaker.canExecute()) {
            logger.warn("Circuit breaker is OPEN for operation: $operationName")
            return fallback?.invoke() 
                ?: throw PdfOperationException(
                    "Circuit breaker is open for operation: $operationName",
                    errorCode = "CIRCUIT_BREAKER_OPEN",
                    operationType = PdfOperationException.PdfOperationType.UNKNOWN,
                    recoverable = false,
                    retryable = false
                )
        }

        var lastException: Exception? = null
        var attempt = 0
        
        while (attempt < config.maxAttempts) {
            attempt++
            
            try {
                val result = operation()
                circuitBreaker.onSuccess()
                
                if (attempt > 1) {
                    logger.info("Operation [$operationName] succeeded on attempt $attempt")
                }
                
                return result
                
            } catch (e: Exception) {
                lastException = e
                circuitBreaker.onFailure()
                
                if (!shouldRetry(e, config) || attempt >= config.maxAttempts) {
                    break
                }
                
                val delay = calculateDelay(attempt, config)
                logger.warn("Operation [$operationName] failed on attempt $attempt, retrying in ${delay.toMillis()}ms", e)
                
                try {
                    Thread.sleep(delay.toMillis())
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        
        // All retries exhausted, try fallback
        fallback?.let { fallbackFunction ->
            try {
                logger.info("Executing fallback for operation: $operationName")
                return fallbackFunction()
            } catch (fallbackException: Exception) {
                logger.error("Fallback also failed for operation: $operationName", fallbackException)
                // Fall through to throw original exception
            }
        }
        
        // No successful execution or fallback
        throw lastException ?: RuntimeException("Operation failed without exception details")
    }

    /**
     * Execute operation asynchronously with recovery
     */
    fun <T> executeAsyncWithRecovery(
        operationName: String,
        operation: () -> T,
        fallback: (() -> T)? = null,
        config: RetryConfig = RetryConfig()
    ): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            executeWithRecovery(operationName, operation, fallback, config)
        }, executor)
    }

    /**
     * Determine recovery strategy based on exception type and context
     */
    fun determineRecoveryStrategy(exception: Throwable, operationContext: Map<String, Any> = emptyMap()): RecoveryStrategy {
        return when {
            exception is PdfOperationException -> {
                when {
                    exception.retryable && exception.recoverable -> RecoveryStrategy.EXPONENTIAL_BACKOFF
                    exception.retryable -> RecoveryStrategy.IMMEDIATE_RETRY
                    exception.recoverable -> RecoveryStrategy.FALLBACK_ONLY
                    else -> RecoveryStrategy.NO_RECOVERY
                }
            }
            exception is TimeoutException -> RecoveryStrategy.CIRCUIT_BREAKER
            exception is java.io.IOException -> RecoveryStrategy.EXPONENTIAL_BACKOFF
            exception is IllegalArgumentException -> RecoveryStrategy.NO_RECOVERY
            else -> RecoveryStrategy.IMMEDIATE_RETRY
        }
    }

    /**
     * Get recovery recommendations for an exception
     */
    fun getRecoveryRecommendations(exception: Throwable): Map<String, Any> {
        val strategy = determineRecoveryStrategy(exception)
        
        return mapOf(
            "strategy" to strategy.name,
            "retryable" to shouldRetry(exception, RetryConfig()),
            "fallback_available" to (strategy != RecoveryStrategy.NO_RECOVERY),
            "circuit_breaker_recommended" to (strategy == RecoveryStrategy.CIRCUIT_BREAKER),
            "immediate_action_required" to (strategy == RecoveryStrategy.NO_RECOVERY),
            "suggested_config" to when (strategy) {
                RecoveryStrategy.IMMEDIATE_RETRY -> mapOf(
                    "max_attempts" to 3,
                    "delay" to "1s"
                )
                RecoveryStrategy.EXPONENTIAL_BACKOFF -> mapOf(
                    "max_attempts" to 5,
                    "initial_delay" to "1s",
                    "max_delay" to "60s",
                    "multiplier" to 2.0
                )
                RecoveryStrategy.CIRCUIT_BREAKER -> mapOf(
                    "failure_threshold" to 5,
                    "recovery_timeout" to "60s"
                )
                else -> emptyMap()
            }
        )
    }

    /**
     * Create a fallback result for common PDF operations
     */
    fun createFallbackResult(operationType: PdfOperationException.PdfOperationType, context: Map<String, Any> = emptyMap()): Map<String, Any> {
        return when (operationType) {
            PdfOperationException.PdfOperationType.MERGE -> mapOf(
                "status" to "partial_success",
                "message" to "Some files could not be merged, continuing with available files",
                "processed_files" to (context["successful_files"] ?: emptyList<String>()),
                "failed_files" to (context["failed_files"] ?: emptyList<String>())
            )
            PdfOperationException.PdfOperationType.SPLIT -> mapOf(
                "status" to "partial_success",
                "message" to "Document split completed with some pages skipped",
                "successful_pages" to (context["successful_pages"] ?: emptyList<Int>()),
                "failed_pages" to (context["failed_pages"] ?: emptyList<Int>())
            )
            PdfOperationException.PdfOperationType.CONVERSION -> mapOf(
                "status" to "fallback_format",
                "message" to "Converted to alternative format due to original format issues",
                "original_format" to (context["original_format"] ?: "unknown"),
                "fallback_format" to (context["fallback_format"] ?: "pdf")
            )
            else -> mapOf(
                "status" to "graceful_degradation",
                "message" to "Operation completed with reduced functionality",
                "details" to context
            )
        }
    }

    private fun getOrCreateCircuitBreaker(operationName: String): CircuitBreaker {
        return circuitBreakers.computeIfAbsent(operationName) {
            CircuitBreaker(operationName)
        }
    }

    private fun shouldRetry(exception: Throwable, config: RetryConfig): Boolean {
        return config.retryableExceptions.any { it.isInstance(exception) } ||
                (exception is PdfOperationException && exception.retryable)
    }

    private fun calculateDelay(attempt: Int, config: RetryConfig): Duration {
        val baseDelay = config.initialDelay.toMillis().toDouble()
        val exponentialDelay = baseDelay * Math.pow(config.multiplier, (attempt - 1).toDouble())
        
        val delayWithCap = Math.min(exponentialDelay, config.maxDelay.toMillis().toDouble()).toLong()
        
        return if (config.jitter) {
            val jitterAmount = (delayWithCap * 0.1 * Random.nextDouble()).toLong()
            Duration.ofMillis(delayWithCap + jitterAmount)
        } else {
            Duration.ofMillis(delayWithCap)
        }
    }

    /**
     * Get circuit breaker statistics
     */
    fun getCircuitBreakerStats(): Map<String, Any> {
        return circuitBreakers.mapValues { (_, breaker) ->
            mapOf(
                "state" to breaker.getState().name,
                "failure_count" to breaker.getFailureCount()
            )
        }
    }

    /**
     * Reset circuit breaker for a specific operation
     */
    fun resetCircuitBreaker(operationName: String) {
        circuitBreakers.remove(operationName)
        logger.info("Circuit breaker reset for operation: $operationName")
    }

    /**
     * Shutdown the service and cleanup resources
     */
    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}