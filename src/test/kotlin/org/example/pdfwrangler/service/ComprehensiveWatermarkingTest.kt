package org.example.pdfwrangler.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.example.pdfwrangler.TestPdfWranglerApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@SpringBootTest(classes = [TestPdfWranglerApplication::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
class ComprehensiveWatermarkingTest {

    @Autowired
    private lateinit var textWatermarkService: TextWatermarkService
    
    @Autowired
    private lateinit var imageWatermarkService: ImageWatermarkService
    
    @Autowired
    private lateinit var watermarkPositioningService: WatermarkPositioningService
    
    @Autowired
    private lateinit var opacityAndRotationProcessor: OpacityAndRotationProcessor
    
    @Autowired
    private lateinit var spacerConfigurationService: SpacerConfigurationService
    
    @Autowired
    private lateinit var watermarkPreviewService: WatermarkPreviewService
    
    @Autowired
    private lateinit var batchWatermarkingService: BatchWatermarkingService
    
    @Autowired
    private lateinit var watermarkTemplateManagementService: WatermarkTemplateManagementService

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test text watermark application with various configurations`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        // Test basic text watermark
        val basicConfig = TextWatermarkService.TextWatermarkConfig(
            text = "CONFIDENTIAL",
            fontSize = 24,
            color = "#FF0000",
            position = "center"
        )
        
        val result = textWatermarkService.applyTextWatermark(listOf(pdfFile), basicConfig)
        
        assertTrue(result.success, "Text watermark should be applied successfully")
        assertNotNull(result.outputFileName, "Output filename should be provided")
        assertTrue(result.processedFiles > 0, "Should process at least one file")
        
        // Verify watermark was actually applied by checking file size increase
        assertTrue(result.fileSize!! > testPdf.size, "Watermarked file should be larger")
    }
    
    @Test
    fun `test text watermark with different positions`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        val positions = listOf("center", "top-left", "top-right", "bottom-left", "bottom-right")
        
        positions.forEach { position ->
            val config = TextWatermarkService.TextWatermarkConfig(
                text = "TEST",
                position = position
            )
            
            val result = textWatermarkService.applyTextWatermark(listOf(pdfFile), config)
            assertTrue(result.success, "Text watermark with position $position should succeed")
        }
    }
    
    @Test
    fun `test text watermark with transparency and rotation`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        val config = TextWatermarkService.TextWatermarkConfig(
            text = "DRAFT",
            opacity = 0.5,
            rotation = 45.0,
            fontSize = 48
        )
        
        val result = textWatermarkService.applyTextWatermark(listOf(pdfFile), config)
        
        assertTrue(result.success, "Transparent rotated watermark should be applied successfully")
        assertNotNull(result.outputFileName)
    }
    
    @Test
    fun `test image watermark application`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        val watermarkImage = createTestWatermarkImage()
        
        val config = ImageWatermarkService.ImageWatermarkConfig(
            watermarkImage = watermarkImage,
            scale = 1.0,
            opacity = 0.7,
            position = "top-right"
        )
        
        val result = imageWatermarkService.applyImageWatermark(listOf(pdfFile), config)
        
        assertTrue(result.success, "Image watermark should be applied successfully")
        assertNotNull(result.outputFileName)
        assertTrue(result.processedFiles > 0)
    }
    
    @Test
    fun `test image watermark with scaling and positioning`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        val watermarkImage = createTestWatermarkImage()
        
        val scales = listOf(0.5, 1.0, 1.5)
        val positions = listOf("center", "top-left", "bottom-right")
        
        scales.forEach { scale ->
            positions.forEach { position ->
                val config = ImageWatermarkService.ImageWatermarkConfig(
                    watermarkImage = watermarkImage,
                    scale = scale,
                    position = position
                )
                
                val result = imageWatermarkService.applyImageWatermark(listOf(pdfFile), config)
                assertTrue(result.success, "Image watermark with scale $scale and position $position should succeed")
            }
        }
    }
    
    @Test
    fun `test watermark positioning service`() {
        val dimensions = WatermarkPositioningService.WatermarkDimensions(
            pageWidth = 612f,  // Standard letter size
            pageHeight = 792f,
            watermarkWidth = 100f,
            watermarkHeight = 50f
        )
        
        // Test all standard positions
        val positions = mapOf(
            "center" to Pair(256f, 371f),
            "top-left" to Pair(50f, 717f),
            "top-right" to Pair(512f, 717f),
            "bottom-left" to Pair(50f, 25f),
            "bottom-right" to Pair(512f, 25f)
        )
        
        positions.forEach { (position, expected) ->
            val result = watermarkPositioningService.calculatePosition(position, dimensions)
            
            // Allow for small floating point differences
            assertTrue(
                kotlin.math.abs(result.x - expected.first) < 5f,
                "X position for $position should be approximately ${expected.first}, got ${result.x}"
            )
            assertTrue(
                kotlin.math.abs(result.y - expected.second) < 5f,
                "Y position for $position should be approximately ${expected.second}, got ${result.y}"
            )
        }
    }
    
    @Test
    fun `test custom positioning`() {
        val dimensions = WatermarkPositioningService.WatermarkDimensions(
            pageWidth = 612f,
            pageHeight = 792f,
            watermarkWidth = 100f,
            watermarkHeight = 50f
        )
        
        val customX = 200f
        val customY = 300f
        
        val result = watermarkPositioningService.calculateCustomPosition(customX, customY, dimensions)
        
        assertEquals(customX, result.x, "Custom X position should match input")
        assertEquals(customY, result.y, "Custom Y position should match input")
    }
    
    @Test
    fun `test opacity and rotation processor`() {
        val testPdf = createSimpleTestPdf()
        val document = Loader.loadPDF(testPdf)
        
        try {
            val page = document.getPage(0)
            
            // Test various opacity values
            val opacities = listOf(0.1, 0.5, 0.9, 1.0)
            opacities.forEach { opacity ->
                val result = opacityAndRotationProcessor.applyOpacityAndRotation(
                    document, page, opacity, 0.0
                )
                assertTrue(result, "Opacity $opacity should be applied successfully")
            }
            
            // Test various rotation angles
            val rotations = listOf(0.0, 45.0, 90.0, 180.0, 270.0)
            rotations.forEach { rotation ->
                val result = opacityAndRotationProcessor.applyOpacityAndRotation(
                    document, page, 1.0, rotation
                )
                assertTrue(result, "Rotation $rotation should be applied successfully")
            }
            
        } finally {
            document.close()
        }
    }
    
    @Test
    fun `test spacer configuration service`() {
        val config = SpacerConfigurationService.SpacerConfig(
            horizontalSpacing = 100.0,
            verticalSpacing = 50.0,
            pattern = "grid"
        )
        
        val pageSize = SpacerConfigurationService.PageSize(612f, 792f)
        val watermarkSize = SpacerConfigurationService.WatermarkSize(100f, 50f)
        
        val positions = spacerConfigurationService.calculateSpacedPositions(config, pageSize, watermarkSize)
        
        assertNotNull(positions, "Spaced positions should be calculated")
        assertTrue(positions.isNotEmpty(), "Should generate at least one position")
        
        // Verify positions are within page bounds
        positions.forEach { position ->
            assertTrue(position.x >= 0 && position.x <= pageSize.width, "X position should be within page bounds")
            assertTrue(position.y >= 0 && position.y <= pageSize.height, "Y position should be within page bounds")
        }
    }
    
    @Test
    fun `test watermark preview functionality`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        val previewRequest = WatermarkPreviewService.PreviewRequest(
            files = listOf(pdfFile),
            watermarkType = "text",
            text = "PREVIEW TEST",
            previewPage = 1
        )
        
        val preview = watermarkPreviewService.generatePreview(previewRequest)
        
        assertTrue(preview.success, "Preview should be generated successfully")
        assertNotNull(preview.previewImageBase64, "Preview image should be provided")
        assertTrue(preview.previewImageBase64!!.isNotEmpty(), "Preview image data should not be empty")
    }
    
    @Test
    fun `test batch watermarking service`() {
        val testPdf1 = createSimpleTestPdf()
        val testPdf2 = createSimpleTestPdf()
        val files = listOf(
            MockMultipartFile("file1", "test1.pdf", "application/pdf", testPdf1),
            MockMultipartFile("file2", "test2.pdf", "application/pdf", testPdf2)
        )
        
        val batchConfig = BatchWatermarkingService.BatchConfig(
            watermarkType = "text",
            text = "BATCH TEST",
            applyToAllPages = true
        )
        
        val result = batchWatermarkingService.processBatch(files, batchConfig)
        
        assertTrue(result.success, "Batch watermarking should succeed")
        assertEquals(2, result.processedFiles, "Should process both files")
        assertTrue(result.results.size == 2, "Should have results for both files")
        
        // Verify all individual results are successful
        result.results.forEach { individualResult ->
            assertTrue(individualResult.success, "Each individual watermarking should succeed")
        }
    }
    
    @Test
    fun `test watermark template management`() {
        // Create a template
        val template = WatermarkTemplateManagementService.WatermarkTemplate(
            id = "test-template-1",
            name = "Test Template",
            type = "text",
            configuration = mapOf(
                "text" to "TEMPLATE TEST",
                "fontSize" to 24,
                "color" to "#0000FF",
                "position" to "center"
            )
        )
        
        val createResult = watermarkTemplateManagementService.createTemplate(template)
        assertTrue(createResult.success, "Template creation should succeed")
        
        // Retrieve the template
        val retrievedTemplate = watermarkTemplateManagementService.getTemplate("test-template-1")
        assertNotNull(retrievedTemplate, "Template should be retrievable")
        assertEquals(template.name, retrievedTemplate!!.name, "Template name should match")
        
        // List templates
        val templates = watermarkTemplateManagementService.listTemplates()
        assertTrue(templates.any { it.id == "test-template-1" }, "Template should appear in list")
        
        // Update template
        val updatedTemplate = template.copy(name = "Updated Template")
        val updateResult = watermarkTemplateManagementService.updateTemplate(updatedTemplate)
        assertTrue(updateResult.success, "Template update should succeed")
        
        // Delete template
        val deleteResult = watermarkTemplateManagementService.deleteTemplate("test-template-1")
        assertTrue(deleteResult.success, "Template deletion should succeed")
        
        // Verify deletion
        val deletedTemplate = watermarkTemplateManagementService.getTemplate("test-template-1")
        assertNull(deletedTemplate, "Deleted template should not be retrievable")
    }
    
    @Test
    fun `test watermark validation and error handling`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        // Test with invalid configuration
        val invalidConfig = TextWatermarkService.TextWatermarkConfig(
            text = "", // Empty text should be invalid
            fontSize = -1, // Negative font size should be invalid
            opacity = 2.0 // Opacity > 1.0 should be invalid
        )
        
        val result = textWatermarkService.applyTextWatermark(listOf(pdfFile), invalidConfig)
        assertFalse(result.success, "Invalid configuration should result in failure")
        assertTrue(result.message.contains("validation") || result.message.contains("invalid"), 
                  "Error message should indicate validation failure")
    }
    
    @Test
    fun `test watermarking with specific page ranges`() {
        val testPdf = createMultiPageTestPdf(5) // Create 5-page PDF
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        val config = TextWatermarkService.TextWatermarkConfig(
            text = "PAGE SPECIFIC",
            position = "center"
        )
        
        // Apply watermark only to pages 2 and 4
        val pageNumbers = listOf(2, 4)
        val result = textWatermarkService.applyTextWatermark(listOf(pdfFile), config, pageNumbers)
        
        assertTrue(result.success, "Page-specific watermarking should succeed")
        assertEquals(5, result.totalPages, "Should preserve total page count")
    }
    
    @Test
    fun `test visual validation of watermarks`() {
        val testPdf = createSimpleTestPdf()
        val pdfFile = MockMultipartFile("file", "test.pdf", "application/pdf", testPdf)
        
        val config = TextWatermarkService.TextWatermarkConfig(
            text = "VISUAL TEST",
            fontSize = 36,
            color = "#FF0000",
            position = "center",
            opacity = 0.8
        )
        
        val result = textWatermarkService.applyTextWatermark(listOf(pdfFile), config)
        assertTrue(result.success, "Watermarking should succeed")
        
        // Load the watermarked PDF and render it to check visual changes
        val watermarkedPdf = File(result.outputFileName!!)
        assertTrue(watermarkedPdf.exists(), "Watermarked PDF should exist")
        
        val document = Loader.loadPDF(watermarkedPdf)
        try {
            val renderer = PDFRenderer(document)
            val renderedImage = renderer.renderImageWithDPI(0, 150f)
            
            // Basic visual validation - check that the image has some color variation
            // (indicating watermark was applied)
            val hasVariation = hasSignificantColorVariation(renderedImage)
            assertTrue(hasVariation, "Watermarked page should have color variation indicating watermark presence")
            
        } finally {
            document.close()
            watermarkedPdf.delete() // Cleanup
        }
    }
    
    private fun createSimpleTestPdf(): ByteArray {
        val document = PDDocument()
        try {
            document.addPage(org.apache.pdfbox.pdmodel.PDPage())
            
            val baos = ByteArrayOutputStream()
            document.save(baos)
            return baos.toByteArray()
        } finally {
            document.close()
        }
    }
    
    private fun createMultiPageTestPdf(pageCount: Int): ByteArray {
        val document = PDDocument()
        try {
            repeat(pageCount) {
                document.addPage(org.apache.pdfbox.pdmodel.PDPage())
            }
            
            val baos = ByteArrayOutputStream()
            document.save(baos)
            return baos.toByteArray()
        } finally {
            document.close()
        }
    }
    
    private fun createTestWatermarkImage(): MockMultipartFile {
        // Create a simple test image
        val image = BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        
        g2d.color = Color.BLUE
        g2d.fillRect(0, 0, 100, 50)
        g2d.color = Color.WHITE
        g2d.drawString("TEST", 30, 30)
        g2d.dispose()
        
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        
        return MockMultipartFile("image", "watermark.png", "image/png", baos.toByteArray())
    }
    
    private fun hasSignificantColorVariation(image: BufferedImage): Boolean {
        val width = image.width
        val height = image.height
        val sampleSize = minOf(100, width * height / 10) // Sample up to 100 pixels
        
        val colors = mutableSetOf<Int>()
        
        for (i in 0 until sampleSize) {
            val x = (i * width) / sampleSize % width
            val y = (i * height) / sampleSize / width
            colors.add(image.getRGB(x, y) and 0xFFFFFF) // Mask out alpha channel
        }
        
        // If we have more than 5 distinct colors, consider it significant variation
        return colors.size > 5
    }
}