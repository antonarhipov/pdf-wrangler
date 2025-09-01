package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

/**
 * Service for managing stamp libraries, including creation, storage, organization, and retrieval of stamps.
 * Supports text stamps, image stamps, and template-based stamps with categorization and search capabilities.
 * Task 89: Create stamp library management system
 */
@Service
class StampLibraryManagementService {

    private val logger = LoggerFactory.getLogger(StampLibraryManagementService::class.java)

    // In-memory storage for stamps (in production, this would be a database)
    private val stamps = ConcurrentHashMap<String, StampLibraryEntry>()

    // Usage tracking
    private val usageStats = ConcurrentHashMap<String, UsageStatistics>()

    /**
     * Internal data class for stamp library entries.
     */
    data class StampLibraryEntry(
        val id: String,
        val name: String,
        val type: String, // "text", "image", "template"
        val category: String,
        val description: String?,
        val isPublic: Boolean,
        val createdBy: String?,
        val createdAt: String,
        var lastUsed: String?,
        val tags: List<String>,
        val configuration: Map<String, Any>,
        val previewData: ByteArray? = null,
        var isActive: Boolean = true
    )

    /**
     * Data class for usage statistics.
     */
    data class UsageStatistics(
        val stampId: String,
        var usageCount: Int = 0,
        var lastUsed: String? = null,
        var averageRating: Double = 0.0,
        var totalRatings: Int = 0
    )

    /**
     * Data class for stamp search criteria.
     */
    data class StampSearchCriteria(
        val type: String? = null,
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val nameContains: String? = null,
        val isPublic: Boolean? = null,
        val createdBy: String? = null,
        val sortBy: String = "name", // "name", "usage", "created", "lastUsed"
        val sortOrder: String = "asc", // "asc", "desc"
        val limit: Int? = null
    )

    /**
     * Supported stamp categories.
     */
    private val supportedCategories = setOf(
        "general", "approval", "status", "confidential", "urgent", 
        "draft", "reviewed", "custom", "signature", "date"
    )

    /**
     * Default stamps that are created on service initialization.
     */
    private val defaultStamps = listOf(
        Triple("APPROVED", "approval", "Document has been approved"),
        Triple("DRAFT", "status", "Document is in draft status"),
        Triple("CONFIDENTIAL", "confidential", "Confidential document marking"),
        Triple("URGENT", "urgent", "Urgent document marking"),
        Triple("REVIEWED", "approval", "Document has been reviewed"),
        Triple("FINAL", "status", "Final document version"),
        Triple("VOID", "status", "Document is void"),
        Triple("COPY", "general", "Copy document marking")
    )

    init {
        initializeDefaultStamps()
    }

    /**
     * Creates a new stamp in the library.
     *
     * @param request Stamp creation request
     * @return StampLibraryResponse with creation result
     */
    fun createStamp(request: StampLibraryRequest): StampLibraryResponse {
        logger.info("Creating stamp: {}", request.stampName)

        return try {
            // Validate request
            val validationErrors = validateStampRequest(request)
            if (validationErrors.isNotEmpty()) {
                return StampLibraryResponse(
                    success = false,
                    message = "Validation failed: ${validationErrors.joinToString(", ")}",
                    stampId = null
                )
            }

            // Check for duplicate names
            if (stamps.values.any { it.name.equals(request.stampName, ignoreCase = true) && it.isActive }) {
                return StampLibraryResponse(
                    success = false,
                    message = "Stamp with name '${request.stampName}' already exists",
                    stampId = null
                )
            }

            val stampId = generateStampId()
            val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            // Process preview data
            val previewData = generatePreviewData(request)

            // Create configuration map
            val configuration = buildStampConfiguration(request)

            val stampEntry = StampLibraryEntry(
                id = stampId,
                name = request.stampName,
                type = request.stampType,
                category = request.category,
                description = request.description,
                isPublic = request.isPublic,
                createdBy = "system", // In production, get from authentication context
                createdAt = currentTime,
                lastUsed = null,
                tags = request.tags,
                configuration = configuration,
                previewData = previewData
            )

            stamps[stampId] = stampEntry
            usageStats[stampId] = UsageStatistics(stampId)

            logger.info("Created stamp: {} with ID: {}", request.stampName, stampId)

            StampLibraryResponse(
                success = true,
                message = "Stamp created successfully",
                stampId = stampId
            )

        } catch (e: Exception) {
            logger.error("Failed to create stamp: {}", e.message, e)
            StampLibraryResponse(
                success = false,
                message = "Failed to create stamp: ${e.message}",
                stampId = null
            )
        }
    }

    /**
     * Retrieves all stamps based on optional filtering criteria.
     *
     * @param category Optional category filter
     * @param type Optional type filter
     * @return StampLibraryResponse with stamps list
     */
    fun getAllStamps(category: String? = null, type: String? = null): StampLibraryResponse {
        logger.debug("Getting all stamps (category: {}, type: {})", category, type)

        return try {
            val criteria = StampSearchCriteria(
                category = category,
                type = type,
                isPublic = true // Only return public stamps for general access
            )

            val searchResult = searchStamps(criteria)
            StampLibraryResponse(
                success = true,
                message = "Retrieved ${searchResult.size} stamps",
                stampId = null,
                stamps = searchResult
            )

        } catch (e: Exception) {
            logger.error("Failed to get stamps: {}", e.message, e)
            StampLibraryResponse(
                success = false,
                message = "Failed to retrieve stamps: ${e.message}",
                stampId = null
            )
        }
    }

    /**
     * Searches for stamps based on criteria.
     *
     * @param criteria Search criteria
     * @return List of matching stamps
     */
    fun searchStamps(criteria: StampSearchCriteria): List<StampInfo> {
        var filteredStamps = stamps.values.filter { it.isActive }

        // Apply filters
        criteria.type?.let { type ->
            filteredStamps = filteredStamps.filter { it.type.equals(type, ignoreCase = true) }
        }

        criteria.category?.let { category ->
            filteredStamps = filteredStamps.filter { it.category.equals(category, ignoreCase = true) }
        }

        criteria.nameContains?.let { nameFilter ->
            filteredStamps = filteredStamps.filter { 
                it.name.contains(nameFilter, ignoreCase = true) ||
                it.description?.contains(nameFilter, ignoreCase = true) == true
            }
        }

        criteria.isPublic?.let { isPublic ->
            filteredStamps = filteredStamps.filter { it.isPublic == isPublic }
        }

        criteria.createdBy?.let { creator ->
            filteredStamps = filteredStamps.filter { it.createdBy == creator }
        }

        if (criteria.tags.isNotEmpty()) {
            filteredStamps = filteredStamps.filter { stamp ->
                criteria.tags.any { tag -> stamp.tags.contains(tag) }
            }
        }

        // Apply sorting
        filteredStamps = when (criteria.sortBy.lowercase()) {
            "usage" -> {
                val sorted = filteredStamps.sortedBy { stamp ->
                    usageStats[stamp.id]?.usageCount ?: 0
                }
                if (criteria.sortOrder == "desc") sorted.reversed() else sorted
            }
            "created" -> {
                val sorted = filteredStamps.sortedBy { it.createdAt }
                if (criteria.sortOrder == "desc") sorted.reversed() else sorted
            }
            "lastused" -> {
                val sorted = filteredStamps.sortedBy { it.lastUsed ?: "0" }
                if (criteria.sortOrder == "desc") sorted.reversed() else sorted
            }
            else -> { // "name"
                val sorted = filteredStamps.sortedBy { it.name }
                if (criteria.sortOrder == "desc") sorted.reversed() else sorted
            }
        }

        // Apply limit
        val limitedStamps = criteria.limit?.let { filteredStamps.take(it) } ?: filteredStamps

        // Convert to StampInfo
        return limitedStamps.map { convertToStampInfo(it) }
    }

    /**
     * Gets a specific stamp by ID.
     *
     * @param stampId Stamp ID
     * @return StampInfo if found, null otherwise
     */
    fun getStamp(stampId: String): StampInfo? {
        val stamp = stamps[stampId]
        return if (stamp != null && stamp.isActive) {
            convertToStampInfo(stamp)
        } else null
    }

    /**
     * Deletes a stamp from the library.
     *
     * @param stampId Stamp ID to delete
     * @return StampLibraryResponse indicating success or failure
     */
    fun deleteStamp(stampId: String): StampLibraryResponse {
        return try {
            val stamp = stamps[stampId]
            if (stamp != null) {
                // Soft delete - mark as inactive instead of removing
                stamps[stampId] = stamp.copy(isActive = false)
                logger.info("Deleted stamp: {} ({})", stamp.name, stampId)
                
                StampLibraryResponse(
                    success = true,
                    message = "Stamp deleted successfully",
                    stampId = stampId
                )
            } else {
                StampLibraryResponse(
                    success = false,
                    message = "Stamp not found with ID: $stampId",
                    stampId = null
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to delete stamp {}: {}", stampId, e.message)
            StampLibraryResponse(
                success = false,
                message = "Failed to delete stamp: ${e.message}",
                stampId = null
            )
        }
    }

    /**
     * Records usage of a stamp.
     *
     * @param stampId Stamp ID
     */
    fun recordStampUsage(stampId: String) {
        val stamp = stamps[stampId]
        if (stamp != null && stamp.isActive) {
            val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            
            // Update stamp last used
            stamps[stampId] = stamp.copy(lastUsed = currentTime)
            
            // Update usage statistics
            val stats = usageStats[stampId] ?: UsageStatistics(stampId)
            usageStats[stampId] = stats.copy(
                usageCount = stats.usageCount + 1,
                lastUsed = currentTime
            )
            
            logger.debug("Recorded usage for stamp: {}", stampId)
        }
    }

    /**
     * Rates a stamp.
     *
     * @param stampId Stamp ID
     * @param rating Rating (1-5)
     */
    fun rateStamp(stampId: String, rating: Int) {
        if (rating !in 1..5) {
            logger.warn("Invalid rating {} for stamp {}", rating, stampId)
            return
        }

        val stats = usageStats[stampId] ?: UsageStatistics(stampId)
        val newTotalRatings = stats.totalRatings + 1
        val newAverageRating = ((stats.averageRating * stats.totalRatings) + rating) / newTotalRatings
        
        usageStats[stampId] = stats.copy(
            averageRating = newAverageRating,
            totalRatings = newTotalRatings
        )
        
        logger.debug("Rated stamp {} with rating {}, new average: {}", stampId, rating, newAverageRating)
    }

    /**
     * Gets stamp usage statistics.
     *
     * @return Map of statistics
     */
    fun getLibraryStatistics(): Map<String, Any> {
        val activeStamps = stamps.values.filter { it.isActive }
        
        return mapOf(
            "totalStamps" to activeStamps.size,
            "stampsByCategory" to activeStamps.groupBy { it.category }.mapValues { it.value.size },
            "stampsByType" to activeStamps.groupBy { it.type }.mapValues { it.value.size },
            "publicStamps" to activeStamps.count { it.isPublic },
            "privateStamps" to activeStamps.count { !it.isPublic },
            "mostUsedStamp" to (usageStats.values.maxByOrNull { it.usageCount }?.stampId ?: ""),
            "totalUsages" to usageStats.values.sumOf { it.usageCount },
            "averageRating" to (usageStats.values.filter { it.totalRatings > 0 }
                .map { it.averageRating }.average().takeIf { !it.isNaN() } ?: 0.0)
        )
    }

    /**
     * Gets popular stamps based on usage.
     *
     * @param limit Maximum number of stamps to return
     * @return List of popular stamps
     */
    fun getPopularStamps(limit: Int = 10): List<StampInfo> {
        val criteria = StampSearchCriteria(
            isPublic = true,
            sortBy = "usage",
            sortOrder = "desc",
            limit = limit
        )
        return searchStamps(criteria)
    }

    /**
     * Gets recently created stamps.
     *
     * @param limit Maximum number of stamps to return
     * @return List of recent stamps
     */
    fun getRecentStamps(limit: Int = 10): List<StampInfo> {
        val criteria = StampSearchCriteria(
            isPublic = true,
            sortBy = "created",
            sortOrder = "desc",
            limit = limit
        )
        return searchStamps(criteria)
    }

    /**
     * Gets supported categories.
     */
    fun getSupportedCategories(): Set<String> {
        return supportedCategories
    }

    /**
     * Validates a stamp creation request.
     */
    private fun validateStampRequest(request: StampLibraryRequest): List<String> {
        val errors = mutableListOf<String>()

        if (request.stampName.isBlank()) {
            errors.add("Stamp name is required")
        }

        if (request.stampType !in setOf("text", "image", "template")) {
            errors.add("Stamp type must be 'text', 'image', or 'template'")
        }

        if (request.category !in supportedCategories) {
            errors.add("Category must be one of: ${supportedCategories.joinToString()}")
        }

        when (request.stampType) {
            "text" -> {
                if (request.stampText.isNullOrBlank()) {
                    errors.add("Stamp text is required for text stamps")
                }
            }
            "image" -> {
                if (request.stampImage == null || request.stampImage.isEmpty) {
                    errors.add("Stamp image is required for image stamps")
                }
            }
        }

        return errors
    }

    /**
     * Generates a unique stamp ID.
     */
    private fun generateStampId(): String {
        return "stamp_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Generates preview data for a stamp.
     */
    private fun generatePreviewData(request: StampLibraryRequest): ByteArray? {
        return try {
            when (request.stampType) {
                "image" -> {
                    request.stampImage?.let { imageFile ->
                        // Create a thumbnail of the image
                        val originalImage = ImageIO.read(ByteArrayInputStream(imageFile.bytes))
                        val thumbnailSize = 100
                        val thumbnail = BufferedImage(thumbnailSize, thumbnailSize, BufferedImage.TYPE_INT_RGB)
                        val graphics = thumbnail.createGraphics()
                        graphics.drawImage(originalImage, 0, 0, thumbnailSize, thumbnailSize, null)
                        graphics.dispose()
                        
                        val outputStream = ByteArrayOutputStream()
                        ImageIO.write(thumbnail, "PNG", outputStream)
                        outputStream.toByteArray()
                    }
                }
                "text" -> {
                    // Generate a simple text preview image
                    val previewImage = BufferedImage(200, 50, BufferedImage.TYPE_INT_RGB)
                    val graphics = previewImage.createGraphics()
                    graphics.color = java.awt.Color.WHITE
                    graphics.fillRect(0, 0, 200, 50)
                    graphics.color = java.awt.Color.BLACK
                    graphics.drawString(request.stampText ?: "Text Stamp", 10, 30)
                    graphics.dispose()
                    
                    val outputStream = ByteArrayOutputStream()
                    ImageIO.write(previewImage, "PNG", outputStream)
                    outputStream.toByteArray()
                }
                else -> null
            }
        } catch (e: Exception) {
            logger.warn("Failed to generate preview for stamp {}: {}", request.stampName, e.message)
            null
        }
    }

    /**
     * Builds configuration map for a stamp.
     */
    private fun buildStampConfiguration(request: StampLibraryRequest): Map<String, Any> {
        val config = mutableMapOf<String, Any>()
        
        config["type"] = request.stampType
        config["category"] = request.category
        
        when (request.stampType) {
            "text" -> {
                request.stampText?.let { config["text"] = it }
            }
            "image" -> {
                request.stampImage?.let { 
                    config["imageFileName"] = it.originalFilename ?: "unknown"
                    config["imageSize"] = it.size
                }
            }
        }
        
        return config
    }

    /**
     * Converts internal stamp entry to public StampInfo.
     */
    private fun convertToStampInfo(entry: StampLibraryEntry): StampInfo {
        val stats = usageStats[entry.id]
        val previewBase64 = entry.previewData?.let { Base64.getEncoder().encodeToString(it) }
        
        return StampInfo(
            id = entry.id,
            name = entry.name,
            type = entry.type,
            category = entry.category,
            description = entry.description,
            isPublic = entry.isPublic,
            createdAt = entry.createdAt,
            lastUsed = entry.lastUsed,
            usageCount = stats?.usageCount ?: 0,
            previewBase64 = previewBase64,
            tags = entry.tags
        )
    }

    /**
     * Initializes default stamps.
     */
    private fun initializeDefaultStamps() {
        logger.info("Initializing default stamps")
        
        for ((text, category, description) in defaultStamps) {
            val request = StampLibraryRequest(
                stampName = text,
                stampType = "text",
                stampText = text,
                category = category,
                isPublic = true,
                description = description,
                tags = listOf("default", category)
            )
            
            createStamp(request)
        }
        
        logger.info("Initialized {} default stamps", defaultStamps.size)
    }
}