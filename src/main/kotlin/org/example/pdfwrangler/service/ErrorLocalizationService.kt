package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.ErrorResponse
import org.example.pdfwrangler.exception.PdfOperationException
import org.example.pdfwrangler.exception.SecurityException
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Service for error response localization support.
 * Provides multi-language error messages and culturally appropriate error responses.
 */
@Service
class ErrorLocalizationService(
    private val messageSource: MessageSource
) {

    private val logger = LoggerFactory.getLogger(ErrorLocalizationService::class.java)

    companion object {
        // Default locale fallback
        private val DEFAULT_LOCALE = Locale.ENGLISH
        
        // Error message key patterns
        private const val ERROR_CODE_PREFIX = "error.code."
        private const val ERROR_MESSAGE_PREFIX = "error.message."
        private const val ERROR_SUGGESTION_PREFIX = "error.suggestion."
        private const val ERROR_CATEGORY_PREFIX = "error.category."
        private const val OPERATION_TYPE_PREFIX = "operation.type."
        
        // Supported locales for the application
        private val SUPPORTED_LOCALES = setOf(
            Locale.ENGLISH,
            Locale("es"), // Spanish
            Locale.FRENCH,
            Locale.GERMAN,
            Locale("zh", "CN"), // Chinese Simplified
            Locale.JAPANESE,
            Locale("pt", "BR"), // Portuguese Brazil
            Locale("ru"), // Russian
            Locale.ITALIAN,
            Locale("ko") // Korean
        )
    }

    /**
     * Localizes an ErrorResponse based on the current locale context
     */
    fun localizeErrorResponse(errorResponse: ErrorResponse, locale: Locale? = null): ErrorResponse {
        val targetLocale = locale ?: getCurrentLocale()
        
        try {
            val localizedMessage = localizeErrorMessage(errorResponse.errorCode, errorResponse.message, targetLocale)
            val localizedSuggestedAction = localizeErrorSuggestion(errorResponse.errorCode, errorResponse.suggestedAction, targetLocale)
            val localizedCategory = localizeErrorCategory(errorResponse.category.name, targetLocale)
            val localizedOperation = localizeOperationType(errorResponse.operation, targetLocale)
            
            val localizedValidationErrors = errorResponse.validationErrors?.map { validationError ->
                ErrorResponse.ValidationError(
                    field = validationError.field,
                    code = validationError.code,
                    message = localizeValidationError(validationError.code, validationError.message, targetLocale),
                    rejectedValue = validationError.rejectedValue
                )
            }

            return errorResponse.copy(
                message = localizedMessage,
                suggestedAction = localizedSuggestedAction,
                validationErrors = localizedValidationErrors,
                details = errorResponse.details?.plus(
                    mapOf<String, Any>(
                        "localizedCategory" to (localizedCategory as Any),
                        "localizedOperation" to (localizedOperation as Any? ?: ""),
                        "locale" to targetLocale.toString()
                    )
                ) ?: mapOf<String, Any>(
                    "localizedCategory" to (localizedCategory as Any),
                    "localizedOperation" to (localizedOperation as Any? ?: ""),
                    "locale" to targetLocale.toString()
                )
            )
        } catch (e: Exception) {
            logger.warn("Failed to localize error response for locale {}: {}", targetLocale, e.message)
            return errorResponse.copy(
                details = errorResponse.details?.plus("localizationError" to (e.message ?: "Unknown localization error")) 
                    ?: mapOf<String, Any>("localizationError" to (e.message ?: "Unknown localization error"))
            )
        }
    }

    /**
     * Localizes a PdfOperationException message
     */
    fun localizePdfOperationException(
        exception: PdfOperationException,
        locale: Locale? = null
    ): LocalizedExceptionInfo {
        val targetLocale = locale ?: getCurrentLocale()
        
        return LocalizedExceptionInfo(
            originalMessage = exception.message ?: "Unknown error",
            localizedMessage = localizeErrorMessage(exception.errorCode, exception.message, targetLocale),
            localizedSuggestions = generateLocalizedSuggestions(exception, targetLocale),
            localizedOperationType = localizeOperationType(exception.operationType.name, targetLocale),
            locale = targetLocale
        )
    }

    /**
     * Localizes a SecurityException message
     */
    fun localizeSecurityException(
        exception: SecurityException,
        locale: Locale? = null
    ): LocalizedExceptionInfo {
        val targetLocale = locale ?: getCurrentLocale()
        
        return LocalizedExceptionInfo(
            originalMessage = exception.message ?: "Security error",
            localizedMessage = localizeErrorMessage(exception.errorCode, exception.message, targetLocale),
            localizedSuggestions = generateSecuritySuggestions(exception, targetLocale),
            localizedOperationType = localizeOperationType("SECURITY_CHECK", targetLocale),
            locale = targetLocale
        )
    }

    /**
     * Gets localized error messages for a batch of error codes
     */
    fun getLocalizedErrorMessages(
        errorCodes: List<String>,
        locale: Locale? = null
    ): Map<String, String> {
        val targetLocale = locale ?: getCurrentLocale()
        
        return errorCodes.associateWith { errorCode ->
            localizeErrorMessage(errorCode, null, targetLocale)
        }
    }

    /**
     * Gets available locales for the application
     */
    fun getSupportedLocales(): Set<Locale> = SUPPORTED_LOCALES

    /**
     * Checks if a locale is supported
     */
    fun isLocaleSupported(locale: Locale): Boolean {
        return SUPPORTED_LOCALES.any { supportedLocale ->
            supportedLocale.language == locale.language
        }
    }

    /**
     * Gets the best matching supported locale for a given locale
     */
    fun getBestMatchingLocale(requestedLocale: Locale): Locale {
        // First try exact match
        if (SUPPORTED_LOCALES.contains(requestedLocale)) {
            return requestedLocale
        }
        
        // Then try language match
        val languageMatch = SUPPORTED_LOCALES.find { it.language == requestedLocale.language }
        if (languageMatch != null) {
            return languageMatch
        }
        
        // Fallback to default
        return DEFAULT_LOCALE
    }

    /**
     * Creates a localization context for error handling
     */
    fun createLocalizationContext(locale: Locale? = null): LocalizationContext {
        val targetLocale = locale ?: getCurrentLocale()
        val bestMatch = getBestMatchingLocale(targetLocale)
        
        return LocalizationContext(
            requestedLocale = targetLocale,
            actualLocale = bestMatch,
            isSupported = isLocaleSupported(targetLocale),
            fallbackUsed = bestMatch != targetLocale
        )
    }

    private fun getCurrentLocale(): Locale {
        return try {
            LocaleContextHolder.getLocale() ?: DEFAULT_LOCALE
        } catch (e: Exception) {
            logger.debug("Could not get current locale, using default", e)
            DEFAULT_LOCALE
        }
    }

    private fun localizeErrorMessage(errorCode: String, defaultMessage: String?, locale: Locale): String {
        val messageKey = ERROR_MESSAGE_PREFIX + errorCode.lowercase()
        
        return try {
            messageSource.getMessage(messageKey, null, locale)
        } catch (e: Exception) {
            logger.debug("No localized message found for key: {} in locale: {}", messageKey, locale)
            defaultMessage ?: "An error occurred (Code: $errorCode)"
        }
    }

    private fun localizeErrorSuggestion(errorCode: String, defaultSuggestion: String?, locale: Locale): String? {
        if (defaultSuggestion == null) return null
        
        val suggestionKey = ERROR_SUGGESTION_PREFIX + errorCode.lowercase()
        
        return try {
            messageSource.getMessage(suggestionKey, null, locale)
        } catch (e: Exception) {
            logger.debug("No localized suggestion found for key: {} in locale: {}", suggestionKey, locale)
            defaultSuggestion
        }
    }

    private fun localizeErrorCategory(category: String, locale: Locale): String {
        val categoryKey = ERROR_CATEGORY_PREFIX + category.lowercase()
        
        return try {
            messageSource.getMessage(categoryKey, null, locale)
        } catch (e: Exception) {
            logger.debug("No localized category found for key: {} in locale: {}", categoryKey, locale)
            category.replace("_", " ").lowercase().capitalize()
        }
    }

    private fun localizeOperationType(operationType: String?, locale: Locale): String? {
        if (operationType == null) return null
        
        val operationKey = OPERATION_TYPE_PREFIX + operationType.lowercase()
        
        return try {
            messageSource.getMessage(operationKey, null, locale)
        } catch (e: Exception) {
            logger.debug("No localized operation type found for key: {} in locale: {}", operationKey, locale)
            operationType.replace("_", " ").lowercase().capitalize()
        }
    }

    private fun localizeValidationError(errorCode: String, defaultMessage: String, locale: Locale): String {
        val validationKey = "validation." + errorCode.lowercase()
        
        return try {
            messageSource.getMessage(validationKey, null, locale)
        } catch (e: Exception) {
            logger.debug("No localized validation message found for key: {} in locale: {}", validationKey, locale)
            defaultMessage
        }
    }

    private fun generateLocalizedSuggestions(exception: PdfOperationException, locale: Locale): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Add operation-specific suggestions
        when (exception.operationType) {
            PdfOperationException.PdfOperationType.MERGE -> {
                suggestions.addAll(getLocalizedSuggestions("pdf.merge.suggestions", locale))
            }
            PdfOperationException.PdfOperationType.SPLIT -> {
                suggestions.addAll(getLocalizedSuggestions("pdf.split.suggestions", locale))
            }
            PdfOperationException.PdfOperationType.CONVERSION -> {
                suggestions.addAll(getLocalizedSuggestions("pdf.conversion.suggestions", locale))
            }
            PdfOperationException.PdfOperationType.VALIDATION -> {
                suggestions.addAll(getLocalizedSuggestions("pdf.validation.suggestions", locale))
            }
            else -> {
                suggestions.addAll(getLocalizedSuggestions("pdf.general.suggestions", locale))
            }
        }
        
        // Add recovery suggestions if applicable
        if (exception.retryable) {
            suggestions.add(getLocalizedMessage("suggestion.retry", "This operation can be retried", locale))
        }
        
        if (exception.recoverable) {
            suggestions.add(getLocalizedMessage("suggestion.alternative", "Try an alternative approach", locale))
        }
        
        return suggestions.distinct()
    }

    private fun generateSecuritySuggestions(exception: SecurityException, locale: Locale): List<String> {
        val suggestions = mutableListOf<String>()
        
        when (exception.errorCode) {
            "FILE_VALIDATION_ERROR" -> {
                suggestions.addAll(getLocalizedSuggestions("security.file.validation.suggestions", locale))
            }
            "MALICIOUS_CONTENT_ERROR" -> {
                suggestions.addAll(getLocalizedSuggestions("security.malicious.content.suggestions", locale))
            }
            "RATE_LIMIT_ERROR" -> {
                suggestions.addAll(getLocalizedSuggestions("security.rate.limit.suggestions", locale))
            }
            "PATH_TRAVERSAL_ERROR" -> {
                suggestions.addAll(getLocalizedSuggestions("security.path.traversal.suggestions", locale))
            }
            else -> {
                suggestions.addAll(getLocalizedSuggestions("security.general.suggestions", locale))
            }
        }
        
        return suggestions.distinct()
    }

    private fun getLocalizedSuggestions(suggestionKey: String, locale: Locale): List<String> {
        return try {
            val suggestions = messageSource.getMessage(suggestionKey, null, locale)
            suggestions.split("|").map { it.trim() }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            logger.debug("No localized suggestions found for key: {} in locale: {}", suggestionKey, locale)
            emptyList()
        }
    }

    private fun getLocalizedMessage(key: String, defaultMessage: String, locale: Locale): String {
        return try {
            messageSource.getMessage(key, null, locale)
        } catch (e: Exception) {
            logger.debug("No localized message found for key: {} in locale: {}", key, locale)
            defaultMessage
        }
    }

    /**
     * Data class for localized exception information
     */
    data class LocalizedExceptionInfo(
        val originalMessage: String,
        val localizedMessage: String,
        val localizedSuggestions: List<String>,
        val localizedOperationType: String?,
        val locale: Locale
    )

    /**
     * Data class for localization context
     */
    data class LocalizationContext(
        val requestedLocale: Locale,
        val actualLocale: Locale,
        val isSupported: Boolean,
        val fallbackUsed: Boolean
    ) {
        fun toMap(): Map<String, Any> {
            return mapOf(
                "requestedLocale" to requestedLocale.toString(),
                "actualLocale" to actualLocale.toString(),
                "isSupported" to isSupported,
                "fallbackUsed" to fallbackUsed
            )
        }
    }
}