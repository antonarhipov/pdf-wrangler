package org.example.pdfwrangler.config

import org.example.pdfwrangler.service.SecurityAuditLogger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.cors.CorsConfiguration as SpringCorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import jakarta.validation.constraints.NotNull
import jakarta.annotation.PostConstruct

/**
 * CORS configuration properties for secure cross-origin API access.
 * Provides configurable settings for allowed origins, headers, methods, and security controls.
 */
@Component
@ConfigurationProperties(prefix = "pdf-wrangler.cors")
@Validated
data class CorsProperties(
    
    /**
     * List of allowed origins for CORS requests.
     * Use "*" for allowing all origins (not recommended for production).
     * Specific origins should include protocol, domain, and port if non-standard.
     */
    @field:NotNull
    var allowedOrigins: List<String> = listOf(
        "http://localhost:3000",
        "http://localhost:8080",
        "https://localhost:3000",
        "https://localhost:8080"
    ),
    
    /**
     * Allowed origin patterns for more flexible matching.
     * Useful for dynamic subdomains or development environments.
     */
    var allowedOriginPatterns: List<String> = listOf(
        "https://*.yourdomain.com",
        "http://localhost:[*]"
    ),
    
    /**
     * List of allowed HTTP methods for CORS requests.
     */
    @field:NotNull
    var allowedMethods: List<String> = listOf(
        HttpMethod.GET.name(),
        HttpMethod.POST.name(),
        HttpMethod.PUT.name(),
        HttpMethod.DELETE.name(),
        HttpMethod.OPTIONS.name(),
        HttpMethod.HEAD.name()
    ),
    
    /**
     * List of allowed headers in CORS requests.
     */
    @field:NotNull
    var allowedHeaders: List<String> = listOf(
        "Accept",
        "Accept-Language",
        "Content-Language",
        "Content-Type",
        "Authorization",
        "Cache-Control",
        "X-Requested-With",
        "Origin",
        "Access-Control-Request-Method",
        "Access-Control-Request-Headers"
    ),
    
    /**
     * List of headers that are exposed to the client.
     */
    var exposedHeaders: List<String> = listOf(
        "Access-Control-Allow-Origin",
        "Access-Control-Allow-Credentials",
        "Content-Disposition",
        "Content-Length",
        "Content-Type"
    ),
    
    /**
     * Whether credentials (cookies, authorization headers, TLS client certificates) 
     * are supported in CORS requests.
     */
    var allowCredentials: Boolean = true,
    
    /**
     * Maximum age (in seconds) for preflight cache.
     * Browsers will cache preflight responses for this duration.
     */
    var maxAge: Long = 3600, // 1 hour
    
    /**
     * Whether to enable CORS for all endpoints.
     * If false, CORS will only be applied to specific patterns.
     */
    var enableGlobal: Boolean = true,
    
    /**
     * Specific URL patterns where CORS should be applied.
     * Only used when enableGlobal is false.
     */
    var pathPatterns: List<String> = listOf(
        "/api/**",
        "/upload/**",
        "/download/**"
    ),
    
    /**
     * Security settings for CORS configuration.
     */
    var security: CorsSecuritySettings = CorsSecuritySettings()
)

/**
 * Security-specific settings for CORS configuration.
 */
data class CorsSecuritySettings(
    /**
     * Whether to log CORS requests for security monitoring.
     */
    var auditCorsRequests: Boolean = true,
    
    /**
     * Whether to validate Origin header against allowed origins.
     */
    var strictOriginValidation: Boolean = true,
    
    /**
     * Maximum number of origins to track for security monitoring.
     */
    var maxTrackedOrigins: Int = 1000,
    
    /**
     * Whether to block requests with suspicious patterns.
     */
    var enableSuspiciousPatternDetection: Boolean = true,
    
    /**
     * List of blocked user agents patterns.
     */
    var blockedUserAgentPatterns: List<String> = listOf(
        ".*bot.*",
        ".*crawler.*",
        ".*scanner.*"
    )
)

/**
 * Main CORS configuration class that sets up secure cross-origin access.
 * TEMPORARILY DISABLED: @Configuration annotation commented out to disable CORS
 */
// @Configuration  // TEMPORARILY DISABLED - uncomment to re-enable CORS
class CorsConfiguration(
    private val corsProperties: CorsProperties,
    private val securityAuditLogger: SecurityAuditLogger
) {

    private val logger = LoggerFactory.getLogger(CorsConfiguration::class.java)

    @PostConstruct
    fun initialize() {
        logger.info("CORS configuration initialized with {} allowed origins", 
            corsProperties.allowedOrigins.size)
            
        if (corsProperties.allowedOrigins.contains("*")) {
            logger.warn("CORS configured to allow all origins - this is not recommended for production")
            securityAuditLogger.logConfigurationChange(
                component = "CORS",
                changeType = "WILDCARD_ORIGINS",
                oldValue = null,
                newValue = "*",
                details = mapOf("security_risk" to "high", "recommendation" to "specify explicit origins")
            )
        }
        
        securityAuditLogger.logConfigurationChange(
            component = "CORS",
            changeType = "INITIALIZATION",
            oldValue = null,
            newValue = "configured",
            details = mapOf(
                "allowedOrigins" to corsProperties.allowedOrigins.size,
                "allowCredentials" to corsProperties.allowCredentials,
                "maxAge" to corsProperties.maxAge
            )
        )
    }

    /**
     * Creates the main CORS configuration source.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = SpringCorsConfiguration()
        
        // Configure allowed origins
        if (corsProperties.allowedOrigins.isNotEmpty()) {
            configuration.allowedOrigins = corsProperties.allowedOrigins
        }
        
        // Configure allowed origin patterns
        if (corsProperties.allowedOriginPatterns.isNotEmpty()) {
            configuration.allowedOriginPatterns = corsProperties.allowedOriginPatterns
        }
        
        // Configure allowed methods
        configuration.allowedMethods = corsProperties.allowedMethods
        
        // Configure allowed headers
        configuration.allowedHeaders = corsProperties.allowedHeaders
        
        // Configure exposed headers
        configuration.exposedHeaders = corsProperties.exposedHeaders
        
        // Configure credentials support
        configuration.allowCredentials = corsProperties.allowCredentials
        
        // Configure preflight cache duration
        configuration.maxAge = corsProperties.maxAge
        
        val source = UrlBasedCorsConfigurationSource()
        
        if (corsProperties.enableGlobal) {
            source.registerCorsConfiguration("/**", configuration)
            logger.info("CORS enabled globally for all endpoints")
        } else {
            corsProperties.pathPatterns.forEach { pattern ->
                source.registerCorsConfiguration(pattern, configuration)
                logger.info("CORS enabled for pattern: {}", pattern)
            }
        }
        
        return source
    }

    /**
     * Creates a security-aware CORS filter.
     */
    @Bean
    fun corsFilter(corsConfigurationSource: CorsConfigurationSource): CorsFilter {
        return SecurityAwareCorsFilter(corsConfigurationSource, corsProperties, securityAuditLogger)
    }

    /**
     * Gets CORS configuration statistics for monitoring.
     */
    fun getCorsStatistics(): Map<String, Any> {
        return mapOf(
            "allowedOrigins" to corsProperties.allowedOrigins,
            "allowedOriginPatterns" to corsProperties.allowedOriginPatterns,
            "allowedMethods" to corsProperties.allowedMethods,
            "allowCredentials" to corsProperties.allowCredentials,
            "maxAge" to corsProperties.maxAge,
            "enableGlobal" to corsProperties.enableGlobal,
            "pathPatterns" to corsProperties.pathPatterns,
            "securitySettings" to mapOf(
                "auditCorsRequests" to corsProperties.security.auditCorsRequests,
                "strictOriginValidation" to corsProperties.security.strictOriginValidation,
                "enableSuspiciousPatternDetection" to corsProperties.security.enableSuspiciousPatternDetection
            )
        )
    }

    /**
     * Validates CORS configuration for security compliance.
     */
    fun validateConfiguration(): List<String> {
        val issues = mutableListOf<String>()
        
        // Check for wildcard origins in production
        if (corsProperties.allowedOrigins.contains("*")) {
            issues.add("Wildcard origin (*) should not be used in production environments")
        }
        
        // Check for HTTP origins when credentials are enabled
        if (corsProperties.allowCredentials) {
            val httpOrigins = corsProperties.allowedOrigins.filter { it.startsWith("http://") }
            if (httpOrigins.isNotEmpty()) {
                issues.add("HTTP origins should not be used with credentials enabled: $httpOrigins")
            }
        }
        
        // Check for overly permissive headers
        if (corsProperties.allowedHeaders.contains("*")) {
            issues.add("Wildcard headers (*) should be avoided for better security")
        }
        
        // Check preflight cache duration
        if (corsProperties.maxAge > 86400) { // More than 24 hours
            issues.add("Preflight cache duration is very long (${corsProperties.maxAge}s), consider reducing it")
        }
        
        // Validate origins format
        corsProperties.allowedOrigins.forEach { origin ->
            if (origin != "*" && !origin.matches(Regex("https?://[^/]+(/.*)?", RegexOption.IGNORE_CASE))) {
                issues.add("Invalid origin format: $origin")
            }
        }
        
        return issues
    }
}

/**
 * Security-enhanced CORS filter that provides additional monitoring and validation.
 */
class SecurityAwareCorsFilter(
    configurationSource: CorsConfigurationSource,
    private val corsProperties: CorsProperties,
    private val securityAuditLogger: SecurityAuditLogger
) : CorsFilter(configurationSource) {

    private val logger = LoggerFactory.getLogger(SecurityAwareCorsFilter::class.java)
    private val suspiciousOrigins = mutableSetOf<String>()

    override fun doFilterInternal(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
        filterChain: jakarta.servlet.FilterChain
    ) {
        val origin = request.getHeader("Origin")
        val userAgent = request.getHeader("User-Agent")
        
        // Audit CORS requests if enabled
        if (corsProperties.security.auditCorsRequests && origin != null) {
            auditCorsRequest(request, origin, userAgent)
        }
        
        // Check for suspicious patterns
        if (corsProperties.security.enableSuspiciousPatternDetection) {
            checkSuspiciousPatterns(request, origin, userAgent)
        }
        
        try {
            super.doFilterInternal(request, response, filterChain)
        } catch (e: Exception) {
            logger.error("Error processing CORS request from origin: $origin", e)
            securityAuditLogger.logSecurityEvent(
                eventType = SecurityAuditLogger.SecurityEventType.SYSTEM_SECURITY_EVENT,
                severity = SecurityAuditLogger.SecuritySeverity.HIGH,
                message = "CORS filter error",
                details = mapOf(
                    "origin" to (origin ?: "unknown"),
                    "error" to (e.message ?: "unknown error"),
                    "userAgent" to (userAgent ?: "unknown")
                ),
                exception = e
            )
            throw e
        }
    }

    private fun auditCorsRequest(
        request: jakarta.servlet.http.HttpServletRequest,
        origin: String,
        userAgent: String?
    ) {
        securityAuditLogger.logSecurityEvent(
            eventType = SecurityAuditLogger.SecurityEventType.SYSTEM_SECURITY_EVENT,
            severity = SecurityAuditLogger.SecuritySeverity.INFO,
            message = "CORS request from origin: $origin",
            details = mapOf(
                "origin" to origin,
                "method" to request.method,
                "requestUri" to request.requestURI,
                "userAgent" to (userAgent ?: "unknown"),
                "isPreflight" to (request.method == "OPTIONS")
            )
        )
    }

    private fun checkSuspiciousPatterns(
        request: jakarta.servlet.http.HttpServletRequest,
        origin: String?,
        userAgent: String?
    ) {
        val suspiciousIndicators = mutableListOf<String>()
        
        // Check user agent patterns
        if (userAgent != null) {
            corsProperties.security.blockedUserAgentPatterns.forEach { pattern ->
                if (userAgent.matches(Regex(pattern, RegexOption.IGNORE_CASE))) {
                    suspiciousIndicators.add("blocked_user_agent_pattern:$pattern")
                }
            }
        }
        
        // Check for suspicious origin patterns
        if (origin != null) {
            // Very long origins might be suspicious
            if (origin.length > 200) {
                suspiciousIndicators.add("excessive_origin_length")
            }
            
            // Check for localhost origins in production (configurable)
            if (origin.contains("localhost") || origin.contains("127.0.0.1")) {
                suspiciousIndicators.add("localhost_origin")
            }
            
            // Track and limit origins
            if (suspiciousOrigins.size < corsProperties.security.maxTrackedOrigins) {
                suspiciousOrigins.add(origin)
            }
        }
        
        // Log suspicious activity if found
        if (suspiciousIndicators.isNotEmpty()) {
            securityAuditLogger.logSuspiciousActivity(
                activityType = "CORS_SUSPICIOUS_REQUEST",
                description = "CORS request with suspicious patterns detected",
                riskScore = 0.6,
                details = mapOf(
                    "origin" to (origin ?: "unknown"),
                    "userAgent" to (userAgent ?: "unknown"),
                    "method" to request.method,
                    "requestUri" to request.requestURI,
                    "suspiciousIndicators" to suspiciousIndicators
                )
            )
        }
    }
}