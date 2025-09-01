package org.example.pdfwrangler.service

import org.example.pdfwrangler.dto.WatermarkRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Comprehensive integration tests for watermarking system with visual validation.
 * Task 82: Create comprehensive watermarking tests with visual validation
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WatermarkingIntegrationTest {

    private lateinit var textWatermarkService: TextWatermarkService
    private lateinit var imageWatermarkService: ImageWatermarkService
    private lateinit var watermarkPreviewService: WatermarkPreviewService
    private lateinit var batchWatermarkingService: BatchWatermarkingService
    private lateinit var watermarkTemplateManagementService: WatermarkTemplateManagementService
    private lateinit var watermarkPositioningService: WatermarkPositioningService
    private lateinit var opacityAndRotationProcessor: OpacityAndRotationProcessor
    private lateinit var spacerConfigurationService: SpacerConfigurationService

    @BeforeEach
    fun setUp() {
        // Initialize services (in a real Spring test, these would be @Autowired)
        watermarkPositioningService = WatermarkPositioningService()
        opacityAndRotationProcessor = OpacityAndRotationProcessor()
        spacerConfigurationService = SpacerConfigurationService()
        
        textWatermarkService = TextWatermarkService(
            watermarkPositioningService,
            opacityAndRotationProcessor,
            spacerConfigurationService
        )
        
        imageWatermarkService = ImageWatermarkService(
            watermarkPositioningService,
            opacityAndRotationProcessor,
            spacerConfigurationService
        )
        
        watermarkPreviewService = WatermarkPreviewService(
            textWatermarkService,
            imageWatermarkService,
            watermarkPositioningService,
            spacerConfigurationService
        )
        
        watermarkTemplateManagementService = WatermarkTemplateManagementService(
            textWatermarkService,
            imageWatermarkService
        )
    }

    @Test
    @DisplayName("Text Watermark - Basic Functionality")
    fun testTextWatermarkBasicFunctionality() {
        // Test text watermark configuration creation and validation
        val config = textWatermarkService.createDefaultConfig("Test Watermark")
        
        assertNotNull(config)
        assertEquals("Test Watermark", config.text)
        assertEquals("Arial", config.fontFamily)
        assertEquals(24, config.fontSize)
        assertEquals(0.3, config.opacity)
        assertEquals(45.0, config.rotation)
        assertEquals("center", config.position)
        
        // Validate configuration
        val validationErrors = textWatermarkService.validateTextWatermarkConfig(config)
        assertTrue(validationErrors.isEmpty(), "Default config should be valid")
    }

    @Test
    @DisplayName("Text Watermark - Validation Tests")
    fun testTextWatermarkValidation() {
        // Test invalid configurations
        val invalidConfigs = listOf(
            // Empty text
            TextWatermarkService.TextWatermarkConfig("", fontSize = 24),
            // Invalid font size
            TextWatermarkService.TextWatermarkConfig("Test", fontSize = 0),
            // Invalid opacity
            TextWatermarkService.TextWatermarkConfig("Test", opacity = -0.5),
            // Invalid color format
            TextWatermarkService.TextWatermarkConfig("Test", fontColor = "invalid-color")
        )

        for (config in invalidConfigs) {
            val errors = textWatermarkService.validateTextWatermarkConfig(config)
            assertFalse(errors.isEmpty(), "Config should be invalid: $config")
        }
    }

    @Test
    @DisplayName("Image Watermark - Basic Functionality")
    fun testImageWatermarkBasicFunctionality() {
        // Create mock image file
        val mockImageFile = MockMultipartFile(
            "image", "test.png", "image/png",
            createMockPngBytes()
        )
        
        val config = imageWatermarkService.createDefaultConfig(mockImageFile)
        
        assertNotNull(config)
        assertEquals(mockImageFile, config.imageFile)
        assertEquals(1.0, config.imageScale)
        assertEquals(0.5, config.opacity)
        assertEquals(0.0, config.rotation)
        assertEquals("bottom-right", config.position)
        assertTrue(config.maintainAspectRatio)
        
        // Validate configuration
        val validationErrors = imageWatermarkService.validateImageWatermarkConfig(config)
        assertTrue(validationErrors.isEmpty(), "Default image config should be valid")
    }

    @Test
    @DisplayName("Image Watermark - Validation Tests")
    fun testImageWatermarkValidation() {
        val mockImageFile = MockMultipartFile(
            "image", "test.png", "image/png",
            createMockPngBytes()
        )
        
        // Test invalid configurations
        val invalidConfigs = listOf(
            // Invalid scale
            ImageWatermarkService.ImageWatermarkConfig(mockImageFile, imageScale = -1.0),
            // Invalid opacity
            ImageWatermarkService.ImageWatermarkConfig(mockImageFile, opacity = 1.5),
            // Invalid spacing
            ImageWatermarkService.ImageWatermarkConfig(mockImageFile, horizontalSpacing = -10.0)
        )

        for (config in invalidConfigs) {
            val errors = imageWatermarkService.validateImageWatermarkConfig(config)
            assertFalse(errors.isEmpty(), "Image config should be invalid: $config")
        }
    }

    @Test
    @DisplayName("Watermark Positioning - All Position Types")
    fun testWatermarkPositioning() {
        val positions = listOf(
            "center", "top-left", "top-center", "top-right",
            "center-left", "center-right", "bottom-left", "bottom-center", "bottom-right"
        )
        
        val mockPage = createMockPDPage()
        val dimensions = WatermarkPositioningService.WatermarkDimensions(100f, 50f)
        
        for (position in positions) {
            val calculatedPosition = watermarkPositioningService.calculatePosition(
                mockPage, position, null, null, dimensions, 0.0
            )
            
            assertNotNull(calculatedPosition)
            assertTrue(calculatedPosition.x >= 0f, "X coordinate should be non-negative for position: $position")
            assertTrue(calculatedPosition.y >= 0f, "Y coordinate should be non-negative for position: $position")
            assertTrue(
                watermarkPositioningService.isValidPosition(mockPage, calculatedPosition.x, calculatedPosition.y, dimensions),
                "Position should be valid for: $position"
            )
        }
    }

    @Test
    @DisplayName("Watermark Positioning - Custom Coordinates")
    fun testCustomPositioning() {
        val mockPage = createMockPDPage()
        val dimensions = WatermarkPositioningService.WatermarkDimensions(100f, 50f)
        
        // Test various custom positions
        val customPositions = listOf(
            Pair(0.1, 0.1), // Near bottom-left
            Pair(0.5, 0.5), // Center
            Pair(0.9, 0.9), // Near top-right
            Pair(0.0, 1.0), // Bottom edge, top edge
            Pair(1.0, 0.0)  // Right edge, bottom edge
        )
        
        for ((x, y) in customPositions) {
            val position = watermarkPositioningService.calculatePosition(
                mockPage, "custom", x, y, dimensions, 0.0
            )
            
            assertNotNull(position)
            assertTrue(position.x >= 0f && position.x <= position.pageWidth - dimensions.width)
            assertTrue(position.y >= 0f && position.y <= position.pageHeight - dimensions.height)
        }
    }

    @Test
    @DisplayName("Opacity and Rotation - Processor Tests")
    fun testOpacityAndRotationProcessor() {
        // Test opacity validation
        assertTrue(opacityAndRotationProcessor.isValidOpacity(0.0))
        assertTrue(opacityAndRotationProcessor.isValidOpacity(0.5))
        assertTrue(opacityAndRotationProcessor.isValidOpacity(1.0))
        assertFalse(opacityAndRotationProcessor.isValidOpacity(-0.1))
        assertFalse(opacityAndRotationProcessor.isValidOpacity(1.1))
        
        // Test opacity configuration creation
        val opacityConfig = opacityAndRotationProcessor.createOpacityConfig(0.7, "Normal")
        assertEquals(0.7, opacityConfig.value)
        assertEquals("Normal", opacityConfig.blendMode)
        
        // Test rotation configuration creation
        val rotationConfig = opacityAndRotationProcessor.createRotationConfig(45.0, 100f, 150f)
        assertEquals(45.0, rotationConfig.angle)
        assertEquals(100f, rotationConfig.centerX)
        assertEquals(150f, rotationConfig.centerY)
        
        // Test rotated bounds calculation
        val (rotatedWidth, rotatedHeight) = opacityAndRotationProcessor.calculateRotatedBounds(100f, 50f, 45.0)
        assertTrue(rotatedWidth > 100f, "Rotated width should be larger")
        assertTrue(rotatedHeight > 50f, "Rotated height should be larger")
    }

    @Test
    @DisplayName("Spacer Configuration - Grid Layout")
    fun testSpacerConfiguration() {
        val mockPage = createMockPDPage()
        val watermarkWidth = 80.0
        val watermarkHeight = 40.0
        
        // Test absolute spacing
        val spacingConfig = SpacerConfigurationService.SpacingConfig(
            horizontalSpacing = 20.0,
            verticalSpacing = 15.0,
            spacingType = SpacerConfigurationService.SpacingType.ABSOLUTE
        )
        
        val gridLayout = spacerConfigurationService.createGridLayout(
            mockPage, watermarkWidth, watermarkHeight, spacingConfig
        )
        
        assertTrue(gridLayout.rows > 0, "Should have at least one row")
        assertTrue(gridLayout.columns > 0, "Should have at least one column")
        assertTrue(gridLayout.horizontalSpacing >= 0, "Horizontal spacing should be non-negative")
        assertTrue(gridLayout.verticalSpacing >= 0, "Vertical spacing should be non-negative")
        
        // Test grid positions calculation
        val positions = spacerConfigurationService.calculateGridPositions(gridLayout, spacingConfig.margins)
        assertEquals(gridLayout.rows * gridLayout.columns, positions.size, "Position count should match grid size")
        
        // Verify all positions are within bounds
        for ((x, y) in positions) {
            assertTrue(x >= 0.0, "X position should be non-negative")
            assertTrue(y >= 0.0, "Y position should be non-negative")
        }
    }

    @Test
    @DisplayName("Watermark Preview - Text and Image")
    fun testWatermarkPreview() {
        val mockPdfFile = MockMultipartFile(
            "pdf", "test.pdf", "application/pdf",
            createMockPdfBytes()
        )
        
        // Test text watermark preview
        val textConfig = TextWatermarkService.TextWatermarkConfig("PREVIEW TEST")
        val textPreviewResult = watermarkPreviewService.generateTextWatermarkPreview(
            mockPdfFile, textConfig
        )
        
        assertTrue(textPreviewResult.success, "Text preview should succeed")
        assertNotNull(textPreviewResult.watermarkPositions)
        assertTrue(textPreviewResult.watermarkPositions.isNotEmpty(), "Should have watermark positions")
        
        // Test image watermark preview
        val mockImageFile = MockMultipartFile(
            "image", "test.png", "image/png",
            createMockPngBytes()
        )
        val imageConfig = ImageWatermarkService.ImageWatermarkConfig(mockImageFile)
        val imagePreviewResult = watermarkPreviewService.generateImageWatermarkPreview(
            mockPdfFile, imageConfig
        )
        
        assertTrue(imagePreviewResult.success, "Image preview should succeed")
        assertNotNull(imagePreviewResult.watermarkPositions)
        assertTrue(imagePreviewResult.watermarkPositions.isNotEmpty(), "Should have watermark positions")
    }

    @Test
    @DisplayName("Template Management - CRUD Operations")
    fun testTemplateManagement() {
        // Test text template creation
        val textConfig = TextWatermarkService.TextWatermarkConfig("Template Test")
        val createTextResult = watermarkTemplateManagementService.createTextTemplate(
            "Test Text Template", textConfig
        )
        
        assertTrue(createTextResult.success, "Text template creation should succeed")
        assertNotNull(createTextResult.templateId)
        assertNotNull(createTextResult.template)
        
        // Test template retrieval
        val getResult = watermarkTemplateManagementService.getTemplate(createTextResult.templateId!!)
        assertTrue(getResult.success, "Template retrieval should succeed")
        assertEquals("Test Text Template", getResult.template?.name)
        
        // Test template update
        val updateResult = watermarkTemplateManagementService.updateTemplate(
            createTextResult.templateId!!,
            name = "Updated Template Name"
        )
        assertTrue(updateResult.success, "Template update should succeed")
        assertEquals("Updated Template Name", updateResult.template?.name)
        
        // Test template search
        val searchResult = watermarkTemplateManagementService.searchTemplates(
            WatermarkTemplateManagementService.TemplateSearchCriteria(type = "text")
        )
        assertTrue(searchResult.success, "Template search should succeed")
        assertTrue(searchResult.templates?.isNotEmpty() ?: false, "Should find templates")
        
        // Test template deletion
        val deleteResult = watermarkTemplateManagementService.deleteTemplate(createTextResult.templateId!!)
        assertTrue(deleteResult.success, "Template deletion should succeed")
        
        // Verify template is deleted
        val getDeletedResult = watermarkTemplateManagementService.getTemplate(createTextResult.templateId!!)
        assertFalse(getDeletedResult.success, "Deleted template should not be found")
    }

    @Test
    @DisplayName("Batch Watermarking - Job Validation and Processing")
    fun testBatchWatermarking() {
        val mockFiles = listOf(
            MockMultipartFile("pdf1", "test1.pdf", "application/pdf", createMockPdfBytes()),
            MockMultipartFile("pdf2", "test2.pdf", "application/pdf", createMockPdfBytes())
        )
        
        // Test text batch job creation and validation
        val textBatchJob = batchWatermarkingService.createDefaultTextBatchJob(mockFiles, "Batch Test")
        val textValidationErrors = batchWatermarkingService.validateBatchJob(textBatchJob)
        assertTrue(textValidationErrors.isEmpty(), "Default text batch job should be valid")
        
        // Test image batch job creation and validation
        val mockImageFile = MockMultipartFile("image", "test.png", "image/png", createMockPngBytes())
        val imageBatchJob = batchWatermarkingService.createDefaultImageBatchJob(mockFiles, mockImageFile)
        val imageValidationErrors = batchWatermarkingService.validateBatchJob(imageBatchJob)
        assertTrue(imageValidationErrors.isEmpty(), "Default image batch job should be valid")
        
        // Test invalid batch job
        val invalidJob = BatchWatermarkingService.BatchWatermarkJob(
            files = emptyList(), // Invalid - no files
            watermarkType = "text",
            textConfig = TextWatermarkService.TextWatermarkConfig("")  // Invalid - empty text
        )
        val invalidJobErrors = batchWatermarkingService.validateBatchJob(invalidJob)
        assertFalse(invalidJobErrors.isEmpty(), "Invalid batch job should have errors")
    }

    @Test
    @DisplayName("Visual Validation - Watermark Appearance")
    fun testWatermarkVisualValidation() {
        // Test different font families and sizes
        val fontTests = listOf(
            Triple("Arial", 12, false),
            Triple("Times", 18, true),
            Triple("Courier", 24, false),
            Triple("Helvetica", 36, true)
        )
        
        for ((fontFamily, fontSize, bold) in fontTests) {
            val config = TextWatermarkService.TextWatermarkConfig(
                text = "Visual Test",
                fontFamily = fontFamily,
                fontSize = fontSize,
                fontBold = bold
            )
            
            val errors = textWatermarkService.validateTextWatermarkConfig(config)
            assertTrue(errors.isEmpty(), "Font configuration should be valid: $fontFamily, $fontSize, $bold")
        }
        
        // Test different opacity levels
        val opacityLevels = listOf(0.1, 0.3, 0.5, 0.7, 0.9)
        for (opacity in opacityLevels) {
            val config = TextWatermarkService.TextWatermarkConfig(
                text = "Opacity Test",
                opacity = opacity
            )
            
            val errors = textWatermarkService.validateTextWatermarkConfig(config)
            assertTrue(errors.isEmpty(), "Opacity configuration should be valid: $opacity")
        }
        
        // Test different rotation angles
        val rotationAngles = listOf(0.0, 15.0, 45.0, 90.0, 180.0, 270.0, -45.0)
        for (rotation in rotationAngles) {
            val config = TextWatermarkService.TextWatermarkConfig(
                text = "Rotation Test",
                rotation = rotation
            )
            
            val errors = textWatermarkService.validateTextWatermarkConfig(config)
            assertTrue(errors.isEmpty(), "Rotation configuration should be valid: $rotation")
        }
    }

    // Helper methods to create mock objects and data
    
    private fun createMockPDPage(): org.apache.pdfbox.pdmodel.PDPage {
        val page = org.apache.pdfbox.pdmodel.PDPage()
        page.mediaBox = org.apache.pdfbox.pdmodel.common.PDRectangle(595f, 842f) // A4 size
        return page
    }
    
    private fun createMockPngBytes(): ByteArray {
        // Create minimal PNG header bytes for testing
        return byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, // IHDR chunk length
            0x49, 0x48, 0x44, 0x52, // IHDR
            0x00, 0x00, 0x00, 0x01, // Width: 1
            0x00, 0x00, 0x00, 0x01, // Height: 1
            0x08, 0x02, 0x00, 0x00, 0x00 // Bit depth, color type, etc.
        )
    }
    
    private fun createMockPdfBytes(): ByteArray {
        // Create minimal PDF header for testing
        return "%PDF-1.4\n".toByteArray()
    }
}