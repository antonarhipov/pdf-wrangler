package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing watermark templates.
 * Provides functionality to save, load, update, and delete watermark configurations.
 * Task 81: Add watermark template management system
 */
@Service
class WatermarkTemplateManagementService(
    private val textWatermarkService: TextWatermarkService,
    private val imageWatermarkService: ImageWatermarkService
) {

    private val logger = LoggerFactory.getLogger(WatermarkTemplateManagementService::class.java)

    // In-memory storage for templates (in production, this would be a database)
    private val templates = ConcurrentHashMap<String, WatermarkTemplate>()

    /**
     * Data class for watermark template.
     */
    data class WatermarkTemplate(
        val id: String,
        val name: String,
        val description: String? = null,
        val type: String, // "text" or "image"
        val configuration: Map<String, Any>,
        val createdAt: String,
        val updatedAt: String,
        var lastUsed: String? = null,
        val tags: List<String> = emptyList(),
        val isPublic: Boolean = false,
        val createdBy: String? = null
    )

    /**
     * Data class for template search/filter criteria.
     */
    data class TemplateSearchCriteria(
        val type: String? = null,
        val tags: List<String> = emptyList(),
        val nameContains: String? = null,
        val createdBy: String? = null,
        val isPublic: Boolean? = null,
        val sortBy: String = "name", // "name", "createdAt", "lastUsed"
        val sortOrder: String = "asc" // "asc", "desc"
    )

    /**
     * Data class for template operation result.
     */
    data class TemplateOperationResult(
        val success: Boolean,
        val message: String,
        val templateId: String? = null,
        val template: WatermarkTemplate? = null,
        val templates: List<WatermarkTemplate>? = null
    )

    /**
     * Creates a new watermark template from text watermark configuration.
     *
     * @param name Template name
     * @param textConfig Text watermark configuration
     * @param description Optional description
     * @param tags Optional tags for categorization
     * @param isPublic Whether template is publicly accessible
     * @param createdBy User who created the template
     * @return TemplateOperationResult with created template
     */
    fun createTextTemplate(
        name: String,
        textConfig: TextWatermarkService.TextWatermarkConfig,
        description: String? = null,
        tags: List<String> = emptyList(),
        isPublic: Boolean = false,
        createdBy: String? = null
    ): TemplateOperationResult {
        logger.info("Creating text watermark template: {}", name)

        // Validate configuration
        val validationErrors = textWatermarkService.validateTextWatermarkConfig(textConfig)
        if (validationErrors.isNotEmpty()) {
            return TemplateOperationResult(
                success = false,
                message = "Invalid text configuration: ${validationErrors.joinToString(", ")}"
            )
        }

        // Check for duplicate names
        if (templates.values.any { it.name.equals(name, ignoreCase = true) }) {
            return TemplateOperationResult(
                success = false,
                message = "Template with name '$name' already exists"
            )
        }

        val templateId = generateTemplateId()
        val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val configuration = mutableMapOf<String, Any>().apply {
            put("text", textConfig.text)
            put("fontFamily", textConfig.fontFamily)
            put("fontSize", textConfig.fontSize)
            put("fontColor", textConfig.fontColor)
            put("fontBold", textConfig.fontBold)
            put("fontItalic", textConfig.fontItalic)
            put("opacity", textConfig.opacity)
            put("rotation", textConfig.rotation)
            put("position", textConfig.position)
            textConfig.customX?.let { put("customX", it) }
            textConfig.customY?.let { put("customY", it) }
            put("horizontalSpacing", textConfig.horizontalSpacing)
            put("verticalSpacing", textConfig.verticalSpacing)
            put("repeatWatermark", textConfig.repeatWatermark)
        }

        val template = WatermarkTemplate(
            id = templateId,
            name = name,
            description = description,
            type = "text",
            configuration = configuration,
            createdAt = currentTime,
            updatedAt = currentTime,
            tags = tags,
            isPublic = isPublic,
            createdBy = createdBy
        )

        templates[templateId] = template

        logger.info("Created text watermark template: {} with ID: {}", name, templateId)

        return TemplateOperationResult(
            success = true,
            message = "Text watermark template created successfully",
            templateId = templateId,
            template = template
        )
    }

    /**
     * Creates a new watermark template from image watermark configuration.
     *
     * @param name Template name
     * @param imageConfig Image watermark configuration
     * @param description Optional description
     * @param tags Optional tags for categorization
     * @param isPublic Whether template is publicly accessible
     * @param createdBy User who created the template
     * @return TemplateOperationResult with created template
     */
    fun createImageTemplate(
        name: String,
        imageConfig: ImageWatermarkService.ImageWatermarkConfig,
        description: String? = null,
        tags: List<String> = emptyList(),
        isPublic: Boolean = false,
        createdBy: String? = null
    ): TemplateOperationResult {
        logger.info("Creating image watermark template: {}", name)

        // Validate configuration
        val validationErrors = imageWatermarkService.validateImageWatermarkConfig(imageConfig)
        if (validationErrors.isNotEmpty()) {
            return TemplateOperationResult(
                success = false,
                message = "Invalid image configuration: ${validationErrors.joinToString(", ")}"
            )
        }

        // Check for duplicate names
        if (templates.values.any { it.name.equals(name, ignoreCase = true) }) {
            return TemplateOperationResult(
                success = false,
                message = "Template with name '$name' already exists"
            )
        }

        val templateId = generateTemplateId()
        val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        // Note: In a real implementation, image files would be stored separately
        // and referenced by ID or path. Here we store basic configuration only.
        val configuration = mutableMapOf<String, Any>().apply {
            put("imageFileName", imageConfig.imageFile.originalFilename ?: "unknown")
            put("imageScale", imageConfig.imageScale)
            put("opacity", imageConfig.opacity)
            put("rotation", imageConfig.rotation)
            put("position", imageConfig.position)
            imageConfig.customX?.let { put("customX", it) }
            imageConfig.customY?.let { put("customY", it) }
            put("horizontalSpacing", imageConfig.horizontalSpacing)
            put("verticalSpacing", imageConfig.verticalSpacing)
            put("repeatWatermark", imageConfig.repeatWatermark)
            put("maintainAspectRatio", imageConfig.maintainAspectRatio)
            imageConfig.maxWidth?.let { put("maxWidth", it) }
            imageConfig.maxHeight?.let { put("maxHeight", it) }
        }

        val template = WatermarkTemplate(
            id = templateId,
            name = name,
            description = description,
            type = "image",
            configuration = configuration,
            createdAt = currentTime,
            updatedAt = currentTime,
            tags = tags,
            isPublic = isPublic,
            createdBy = createdBy
        )

        templates[templateId] = template

        logger.info("Created image watermark template: {} with ID: {}", name, templateId)

        return TemplateOperationResult(
            success = true,
            message = "Image watermark template created successfully",
            templateId = templateId,
            template = template
        )
    }

    /**
     * Retrieves a watermark template by ID.
     *
     * @param templateId Template ID
     * @return TemplateOperationResult with template if found
     */
    fun getTemplate(templateId: String): TemplateOperationResult {
        val template = templates[templateId]
        return if (template != null) {
            TemplateOperationResult(
                success = true,
                message = "Template retrieved successfully",
                templateId = templateId,
                template = template
            )
        } else {
            TemplateOperationResult(
                success = false,
                message = "Template not found with ID: $templateId"
            )
        }
    }

    /**
     * Updates an existing watermark template.
     *
     * @param templateId Template ID to update
     * @param name New name (optional)
     * @param description New description (optional)
     * @param tags New tags (optional)
     * @param isPublic New public status (optional)
     * @return TemplateOperationResult with updated template
     */
    fun updateTemplate(
        templateId: String,
        name: String? = null,
        description: String? = null,
        tags: List<String>? = null,
        isPublic: Boolean? = null
    ): TemplateOperationResult {
        val existingTemplate = templates[templateId]
            ?: return TemplateOperationResult(
                success = false,
                message = "Template not found with ID: $templateId"
            )

        // Check for name conflicts if name is being changed
        if (name != null && name != existingTemplate.name) {
            if (templates.values.any { it.id != templateId && it.name.equals(name, ignoreCase = true) }) {
                return TemplateOperationResult(
                    success = false,
                    message = "Template with name '$name' already exists"
                )
            }
        }

        val updatedTemplate = existingTemplate.copy(
            name = name ?: existingTemplate.name,
            description = description ?: existingTemplate.description,
            tags = tags ?: existingTemplate.tags,
            isPublic = isPublic ?: existingTemplate.isPublic,
            updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        templates[templateId] = updatedTemplate

        logger.info("Updated watermark template: {} (ID: {})", updatedTemplate.name, templateId)

        return TemplateOperationResult(
            success = true,
            message = "Template updated successfully",
            templateId = templateId,
            template = updatedTemplate
        )
    }

    /**
     * Deletes a watermark template.
     *
     * @param templateId Template ID to delete
     * @return TemplateOperationResult indicating success or failure
     */
    fun deleteTemplate(templateId: String): TemplateOperationResult {
        val removedTemplate = templates.remove(templateId)
        return if (removedTemplate != null) {
            logger.info("Deleted watermark template: {} (ID: {})", removedTemplate.name, templateId)
            TemplateOperationResult(
                success = true,
                message = "Template deleted successfully",
                templateId = templateId
            )
        } else {
            TemplateOperationResult(
                success = false,
                message = "Template not found with ID: $templateId"
            )
        }
    }

    /**
     * Searches for templates based on criteria.
     *
     * @param criteria Search criteria
     * @return TemplateOperationResult with matching templates
     */
    fun searchTemplates(criteria: TemplateSearchCriteria): TemplateOperationResult {
        var filteredTemplates = templates.values.toList()

        // Apply filters
        criteria.type?.let { type ->
            filteredTemplates = filteredTemplates.filter { it.type.equals(type, ignoreCase = true) }
        }

        criteria.nameContains?.let { nameFilter ->
            filteredTemplates = filteredTemplates.filter { 
                it.name.contains(nameFilter, ignoreCase = true) 
            }
        }

        criteria.createdBy?.let { creator ->
            filteredTemplates = filteredTemplates.filter { it.createdBy == creator }
        }

        criteria.isPublic?.let { isPublic ->
            filteredTemplates = filteredTemplates.filter { it.isPublic == isPublic }
        }

        if (criteria.tags.isNotEmpty()) {
            filteredTemplates = filteredTemplates.filter { template ->
                criteria.tags.any { tag -> template.tags.contains(tag) }
            }
        }

        // Apply sorting
        filteredTemplates = when (criteria.sortBy.lowercase()) {
            "createdat" -> if (criteria.sortOrder == "desc") {
                filteredTemplates.sortedByDescending { it.createdAt }
            } else {
                filteredTemplates.sortedBy { it.createdAt }
            }
            "lastused" -> if (criteria.sortOrder == "desc") {
                filteredTemplates.sortedByDescending { it.lastUsed ?: "0" }
            } else {
                filteredTemplates.sortedBy { it.lastUsed ?: "0" }
            }
            else -> if (criteria.sortOrder == "desc") {
                filteredTemplates.sortedByDescending { it.name }
            } else {
                filteredTemplates.sortedBy { it.name }
            }
        }

        logger.debug("Template search returned {} results", filteredTemplates.size)

        return TemplateOperationResult(
            success = true,
            message = "Template search completed successfully",
            templates = filteredTemplates
        )
    }

    /**
     * Gets all templates.
     *
     * @return TemplateOperationResult with all templates
     */
    fun getAllTemplates(): TemplateOperationResult {
        val allTemplates = templates.values.sortedBy { it.name }
        return TemplateOperationResult(
            success = true,
            message = "Retrieved all templates successfully",
            templates = allTemplates
        )
    }

    /**
     * Marks a template as used (updates lastUsed timestamp).
     *
     * @param templateId Template ID
     */
    fun markTemplateAsUsed(templateId: String) {
        templates[templateId]?.let { template ->
            templates[templateId] = template.copy(
                lastUsed = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            logger.debug("Marked template {} as used", templateId)
        }
    }

    /**
     * Converts a template back to text watermark configuration.
     *
     * @param templateId Template ID
     * @return TextWatermarkConfig if template is text type, null otherwise
     */
    fun getTextConfigFromTemplate(templateId: String): TextWatermarkService.TextWatermarkConfig? {
        val template = templates[templateId]
        if (template?.type != "text") return null

        val config = template.configuration
        return try {
            TextWatermarkService.TextWatermarkConfig(
                text = config["text"] as String,
                fontFamily = config["fontFamily"] as String? ?: "Arial",
                fontSize = (config["fontSize"] as Number?)?.toInt() ?: 24,
                fontColor = config["fontColor"] as String? ?: "#000000",
                fontBold = config["fontBold"] as Boolean? ?: false,
                fontItalic = config["fontItalic"] as Boolean? ?: false,
                opacity = (config["opacity"] as Number?)?.toDouble() ?: 0.5,
                rotation = (config["rotation"] as Number?)?.toDouble() ?: 0.0,
                position = config["position"] as String? ?: "center",
                customX = (config["customX"] as Number?)?.toDouble(),
                customY = (config["customY"] as Number?)?.toDouble(),
                horizontalSpacing = (config["horizontalSpacing"] as Number?)?.toDouble() ?: 0.0,
                verticalSpacing = (config["verticalSpacing"] as Number?)?.toDouble() ?: 0.0,
                repeatWatermark = config["repeatWatermark"] as Boolean? ?: false
            )
        } catch (e: Exception) {
            logger.error("Failed to convert template {} to text config: {}", templateId, e.message)
            null
        }
    }

    /**
     * Gets template statistics.
     *
     * @return Map with various template statistics
     */
    fun getTemplateStatistics(): Map<String, Any> {
        val allTemplates = templates.values
        val textTemplates = allTemplates.count { it.type == "text" }
        val imageTemplates = allTemplates.count { it.type == "image" }
        val publicTemplates = allTemplates.count { it.isPublic }
        val recentlyUsed = allTemplates.count { 
            it.lastUsed != null && LocalDateTime.parse(it.lastUsed).isAfter(
                LocalDateTime.now().minusDays(7)
            )
        }

        return mapOf(
            "totalTemplates" to allTemplates.size,
            "textTemplates" to textTemplates,
            "imageTemplates" to imageTemplates,
            "publicTemplates" to publicTemplates,
            "privateTemplates" to (allTemplates.size - publicTemplates),
            "recentlyUsed" to recentlyUsed,
            "mostUsedTemplate" to (allTemplates.maxByOrNull { it.lastUsed ?: "0" }?.name ?: "None")
        )
    }

    /**
     * Exports templates as JSON (simplified version).
     *
     * @param templateIds List of template IDs to export (empty for all)
     * @return JSON string representation of templates
     */
    fun exportTemplates(templateIds: List<String> = emptyList()): String {
        val templatesToExport = if (templateIds.isEmpty()) {
            templates.values
        } else {
            templateIds.mapNotNull { templates[it] }
        }

        // Simplified JSON export (in production, use proper JSON library)
        return templatesToExport.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { template ->
            """
            {
                "id": "${template.id}",
                "name": "${template.name}",
                "type": "${template.type}",
                "createdAt": "${template.createdAt}",
                "configuration": ${configurationToJson(template.configuration)}
            }
            """.trimIndent()
        }
    }

    /**
     * Generates a unique template ID.
     */
    private fun generateTemplateId(): String {
        return "wm_template_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Converts configuration map to simple JSON representation.
     */
    private fun configurationToJson(config: Map<String, Any>): String {
        return config.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ","
        ) { (key, value) ->
            when (value) {
                is String -> "\"$key\": \"$value\""
                is Number, is Boolean -> "\"$key\": $value"
                null -> "\"$key\": null"
                else -> "\"$key\": \"$value\""
            }
        }
    }

    /**
     * Validates template data.
     */
    fun validateTemplate(template: WatermarkTemplate): List<String> {
        val errors = mutableListOf<String>()

        if (template.name.isBlank()) {
            errors.add("Template name cannot be empty")
        }

        if (template.type !in listOf("text", "image")) {
            errors.add("Template type must be 'text' or 'image'")
        }

        if (template.configuration.isEmpty()) {
            errors.add("Template configuration cannot be empty")
        }

        return errors
    }
}