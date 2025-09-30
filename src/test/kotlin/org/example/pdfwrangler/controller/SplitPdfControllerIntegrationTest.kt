package org.example.pdfwrangler.controller

import org.example.pdfwrangler.service.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals

/**
 * Integration tests for Split PDF Controller with ZIP archive creation.
 * Tests the complete flow from split request to ZIP download.
 */
@SpringBootTest
@ActiveProfiles("test")
class SplitPdfControllerIntegrationTest {

    @Autowired
    private lateinit var pageRangeSplitService: PageRangeSplitService

    @Autowired
    private lateinit var splitPdfController: SplitPdfController

    @Test
    @DisplayName("Split PDF by page ranges and create ZIP archive - General endpoint")
    fun testSplitPdfWithZipCreation() {
        // Create a test PDF with multiple pages
        val testPdfBytes = createTestPdfWithPages(5)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_document.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request using the general endpoint
        val request = org.example.pdfwrangler.dto.SplitRequest(
            file = mockPdfFile,
            splitStrategy = "pageRanges",
            pageRanges = listOf("1-2", "3-5"),
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation through the general endpoint (this is what failed in production)
        println("[DEBUG_LOG] Starting split operation via general endpoint")
        val response = splitPdfController.splitPdf(request)

        // Verify response
        assertNotNull(response, "Response should not be null")
        assertNotNull(response.body, "Response body should not be null")
        assertTrue(response.statusCode.is2xxSuccessful, "Response should be successful")

        // Verify the resource is a valid file
        val resource = response.body!!
        assertTrue(resource.exists(), "ZIP resource should exist")
        assertTrue(resource.contentLength() > 0, "ZIP file should have content")

        println("[DEBUG_LOG] Split operation completed successfully")
        println("[DEBUG_LOG] ZIP file size: ${resource.contentLength()} bytes")
    }

    @Test
    @DisplayName("Split PDF with custom output pattern")
    fun testSplitPdfWithCustomPattern() {
        // Create a test PDF with multiple pages
        val testPdfBytes = createTestPdfWithPages(5)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_document.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request with custom pattern
        val request = org.example.pdfwrangler.dto.SplitRequest(
            file = mockPdfFile,
            splitStrategy = "pageRanges",
            pageRanges = listOf("1-2", "3-5"),
            outputFileNamePattern = "{original}_part_{index}",
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation
        println("[DEBUG_LOG] Starting split operation with custom pattern")
        val response = splitPdfController.splitPdf(request)

        // Verify response
        assertNotNull(response, "Response should not be null")
        assertNotNull(response.body, "Response body should not be null")
        assertTrue(response.statusCode.is2xxSuccessful, "Response should be successful")

        // Verify the resource is a valid file
        val resource = response.body!!
        assertTrue(resource.exists(), "ZIP resource should exist")
        assertTrue(resource.contentLength() > 0, "ZIP file should have content")

        println("[DEBUG_LOG] Split with custom pattern completed successfully")
        println("[DEBUG_LOG] ZIP file size: ${resource.contentLength()} bytes")
    }

    @Test
    @DisplayName("Split PDF service creates valid SplitResponse")
    fun testSplitByPageRangesService() {
        // Create a test PDF with multiple pages
        val testPdfBytes = createTestPdfWithPages(5)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_document.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create page range split request
        val request = org.example.pdfwrangler.dto.PageRangeSplitRequest(
            file = mockPdfFile,
            pageRanges = listOf("1-2", "3-5"),
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation
        println("[DEBUG_LOG] Starting page range split service")
        val splitResponse = pageRangeSplitService.splitByPageRanges(request)

        // Verify split response
        assertTrue(splitResponse.success, "Split should be successful")
        assertTrue(splitResponse.outputFiles.isNotEmpty(), "Should have output files")
        println("[DEBUG_LOG] Split response: success=${splitResponse.success}, files=${splitResponse.outputFiles.size}")

        // Log output file details
        splitResponse.outputFiles.forEach { outputFile ->
            println("[DEBUG_LOG] Output file: ${outputFile.fileName}, pages=${outputFile.pageCount}, size=${outputFile.fileSizeBytes}")
        }

        // Try to create ZIP from split response
        println("[DEBUG_LOG] Creating ZIP from split response")
        val zipResource = pageRangeSplitService.createZipFromSplitResponse(splitResponse)

        // Verify ZIP creation
        assertNotNull(zipResource, "ZIP resource should not be null")
        assertTrue(zipResource.exists(), "ZIP resource should exist")
        assertTrue(zipResource.contentLength() > 0, "ZIP file should have content")

        println("[DEBUG_LOG] ZIP creation successful, size: ${zipResource.contentLength()} bytes")
    }

    @Test
    @DisplayName("Verify ZIP archive contains correct PDF files with correct page counts")
    fun testZipContentsValidation() {
        // Create a test PDF with 10 pages
        val testPdfBytes = createTestPdfWithPages(10)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_document.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request with multiple ranges
        val request = org.example.pdfwrangler.dto.PageRangeSplitRequest(
            file = mockPdfFile,
            pageRanges = listOf("1-3", "4-6", "7-10"),
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation
        println("[DEBUG_LOG] Starting split operation for ZIP content validation")
        val splitResponse = pageRangeSplitService.splitByPageRanges(request)

        // Verify split response
        assertTrue(splitResponse.success, "Split should be successful")
        assertEquals(3, splitResponse.outputFiles.size, "Should have 3 output files")

        // Create ZIP from split response
        println("[DEBUG_LOG] Creating ZIP from split response")
        val zipResource = pageRangeSplitService.createZipFromSplitResponse(splitResponse)

        // Verify ZIP creation
        assertNotNull(zipResource, "ZIP resource should not be null")
        assertTrue(zipResource.exists(), "ZIP resource should exist")
        assertTrue(zipResource.contentLength() > 0, "ZIP file should have content")

        println("[DEBUG_LOG] ZIP created with size: ${zipResource.contentLength()} bytes")

        // Extract and validate ZIP contents
        val zipBytes = zipResource.inputStream.readBytes()
        val zipInputStream = ZipInputStream(ByteArrayInputStream(zipBytes))
        
        var entryCount = 0
        val extractedFiles = mutableMapOf<String, ByteArray>()
        
        var zipEntry = zipInputStream.nextEntry
        while (zipEntry != null) {
            entryCount++
            val entryName = zipEntry.name
            val entryBytes = zipInputStream.readBytes()
            extractedFiles[entryName] = entryBytes
            
            println("[DEBUG_LOG] Extracted ZIP entry: $entryName, size: ${entryBytes.size} bytes")
            zipInputStream.closeEntry()
            zipEntry = zipInputStream.nextEntry
        }
        zipInputStream.close()

        // Verify correct number of files in ZIP
        assertEquals(3, entryCount, "ZIP should contain 3 files")
        assertEquals(3, extractedFiles.size, "Should have extracted 3 files")

        // Verify each PDF file has correct page count
        val expectedPageCounts = mapOf(
            "test_document_pages_1-3.pdf" to 3,
            "test_document_pages_4-6.pdf" to 3,
            "test_document_pages_7-10.pdf" to 4
        )

        for ((fileName, expectedPages) in expectedPageCounts) {
            assertTrue(extractedFiles.containsKey(fileName), "ZIP should contain $fileName")
            
            val pdfBytes = extractedFiles[fileName]!!
            val pdfDocument = Loader.loadPDF(pdfBytes)
            try {
                val actualPages = pdfDocument.numberOfPages
                assertEquals(expectedPages, actualPages, "$fileName should have $expectedPages pages")
                println("[DEBUG_LOG] Verified $fileName: $actualPages pages (expected: $expectedPages)")
            } finally {
                pdfDocument.close()
            }
        }

        println("[DEBUG_LOG] All ZIP contents validated successfully")
    }

    @Test
    @DisplayName("Test ZIP creation with single page range")
    fun testZipWithSingleRange() {
        // Create a test PDF with 5 pages
        val testPdfBytes = createTestPdfWithPages(5)
        val mockPdfFile = MockMultipartFile(
            "file",
            "single_range.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request with single range
        val request = org.example.pdfwrangler.dto.PageRangeSplitRequest(
            file = mockPdfFile,
            pageRanges = listOf("2-4"),
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation
        println("[DEBUG_LOG] Testing single range split")
        val splitResponse = pageRangeSplitService.splitByPageRanges(request)

        // Verify split response
        assertTrue(splitResponse.success, "Split should be successful")
        assertEquals(1, splitResponse.outputFiles.size, "Should have 1 output file")

        // Create ZIP
        val zipResource = pageRangeSplitService.createZipFromSplitResponse(splitResponse)

        // Verify ZIP
        assertNotNull(zipResource, "ZIP resource should not be null")
        assertTrue(zipResource.contentLength() > 0, "ZIP file should have content")

        // Extract and verify
        val zipBytes = zipResource.inputStream.readBytes()
        val zipInputStream = ZipInputStream(ByteArrayInputStream(zipBytes))
        
        var entryCount = 0
        zipInputStream.nextEntry?.let {
            entryCount++
            val pdfBytes = zipInputStream.readBytes()
            val pdfDocument = Loader.loadPDF(pdfBytes)
            try {
                assertEquals(3, pdfDocument.numberOfPages, "PDF should have 3 pages (2-4)")
                println("[DEBUG_LOG] Single range PDF verified: 3 pages")
            } finally {
                pdfDocument.close()
            }
        }
        zipInputStream.close()

        assertEquals(1, entryCount, "ZIP should contain exactly 1 file")
        println("[DEBUG_LOG] Single range test completed successfully")
    }

    @Test
    @DisplayName("Test ZIP creation with multiple non-contiguous ranges")
    fun testZipWithNonContiguousRanges() {
        // Create a test PDF with 20 pages
        val testPdfBytes = createTestPdfWithPages(20)
        val mockPdfFile = MockMultipartFile(
            "file",
            "multi_range.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request with non-contiguous ranges
        val request = org.example.pdfwrangler.dto.PageRangeSplitRequest(
            file = mockPdfFile,
            pageRanges = listOf("1-2", "5-7", "10-12", "18-20"),
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation
        println("[DEBUG_LOG] Testing non-contiguous ranges split")
        val splitResponse = pageRangeSplitService.splitByPageRanges(request)

        // Verify split response
        assertTrue(splitResponse.success, "Split should be successful")
        assertEquals(4, splitResponse.outputFiles.size, "Should have 4 output files")

        // Create ZIP
        val zipResource = pageRangeSplitService.createZipFromSplitResponse(splitResponse)

        // Verify ZIP
        assertNotNull(zipResource, "ZIP resource should not be null")
        assertTrue(zipResource.contentLength() > 0, "ZIP file should have content")

        // Extract and verify all files
        val zipBytes = zipResource.inputStream.readBytes()
        val zipInputStream = ZipInputStream(ByteArrayInputStream(zipBytes))
        
        val expectedPageCounts = listOf(2, 3, 3, 3)
        var fileIndex = 0
        
        var zipEntry = zipInputStream.nextEntry
        while (zipEntry != null) {
            val pdfBytes = zipInputStream.readBytes()
            val pdfDocument = Loader.loadPDF(pdfBytes)
            try {
                val actualPages = pdfDocument.numberOfPages
                val expectedPages = expectedPageCounts[fileIndex]
                assertEquals(expectedPages, actualPages, "File ${fileIndex + 1} should have $expectedPages pages")
                println("[DEBUG_LOG] File ${fileIndex + 1} verified: $actualPages pages")
            } finally {
                pdfDocument.close()
            }
            
            fileIndex++
            zipInputStream.closeEntry()
            zipEntry = zipInputStream.nextEntry
        }
        zipInputStream.close()

        assertEquals(4, fileIndex, "ZIP should contain exactly 4 files")
        println("[DEBUG_LOG] Non-contiguous ranges test completed successfully")
    }

    @Test
    @DisplayName("Test split with fileSize strategy through general endpoint - REPRODUCES BUG")
    fun testSplitPdfWithFileSizeStrategy() {
        // Create a test PDF with 10 pages
        val testPdfBytes = createTestPdfWithPages(10)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_filesize.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request using fileSize strategy
        val request = org.example.pdfwrangler.dto.SplitRequest(
            file = mockPdfFile,
            splitStrategy = "fileSize",
            pageRanges = emptyList(),
            fileSizeThresholdMB = 1,
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation through the general endpoint
        println("[DEBUG_LOG] Starting split operation with fileSize strategy")
        try {
            val response = splitPdfController.splitPdf(request)
            
            // Verify response
            assertNotNull(response, "Response should not be null")
            assertNotNull(response.body, "Response body should not be null")
            assertTrue(response.statusCode.is2xxSuccessful, "Response should be successful")
            
            println("[DEBUG_LOG] fileSize strategy split completed successfully")
        } catch (e: Exception) {
            println("[DEBUG_LOG] ERROR: fileSize strategy failed with: ${e.message}")
            throw e
        }
    }

    @Test
    @DisplayName("Test split with documentSection strategy through general endpoint - REPRODUCES BUG")
    fun testSplitPdfWithDocumentSectionStrategy() {
        // Create a test PDF with 10 pages
        val testPdfBytes = createTestPdfWithPages(10)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_section.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request using documentSection strategy
        val request = org.example.pdfwrangler.dto.SplitRequest(
            file = mockPdfFile,
            splitStrategy = "documentSection",
            pageRanges = emptyList(),
            fileSizeThresholdMB = null,
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation through the general endpoint
        println("[DEBUG_LOG] Starting split operation with documentSection strategy")
        try {
            val response = splitPdfController.splitPdf(request)
            
            // Verify response
            assertNotNull(response, "Response should not be null")
            assertNotNull(response.body, "Response body should not be null")
            assertTrue(response.statusCode.is2xxSuccessful, "Response should be successful")
            
            println("[DEBUG_LOG] documentSection strategy split completed successfully")
        } catch (e: Exception) {
            println("[DEBUG_LOG] ERROR: documentSection strategy failed with: ${e.message}")
            throw e
        }
    }

    @Test
    @DisplayName("Test split with EMPTY page ranges - should auto-split into single pages")
    fun testSplitPdfWithEmptyPageRanges() {
        // Create a test PDF with 5 pages
        val testPdfBytes = createTestPdfWithPages(5)
        val mockPdfFile = MockMultipartFile(
            "file",
            "test_empty_ranges.pdf",
            "application/pdf",
            testPdfBytes
        )

        // Create split request with pageRanges strategy but EMPTY pageRanges list
        // This should auto-split into single pages (one page per file)
        val request = org.example.pdfwrangler.dto.SplitRequest(
            file = mockPdfFile,
            splitStrategy = "pageRanges",
            pageRanges = emptyList(),  // Empty list - should trigger auto-split to single pages
            fileSizeThresholdMB = null,
            outputFileNamePattern = null,
            preserveBookmarks = true,
            preserveMetadata = true
        )

        // Execute split operation through the general endpoint
        println("[DEBUG_LOG] Starting split operation with EMPTY pageRanges - expecting auto-split to single pages")
        val response = splitPdfController.splitPdf(request)
        
        // Verify response is successful
        assertNotNull(response, "Response should not be null")
        assertNotNull(response.body, "Response body should not be null")
        assertTrue(response.statusCode.is2xxSuccessful, "Response should be successful")
        
        // Verify ZIP resource
        val zipResource = response.body!!
        assertTrue(zipResource.exists(), "ZIP resource should exist")
        assertTrue(zipResource.contentLength() > 0, "ZIP file should have content")
        
        println("[DEBUG_LOG] ZIP created with size: ${zipResource.contentLength()} bytes")
        
        // Extract and validate ZIP contents
        val zipBytes = zipResource.inputStream.readBytes()
        val zipInputStream = ZipInputStream(ByteArrayInputStream(zipBytes))
        
        var entryCount = 0
        val extractedFiles = mutableMapOf<String, ByteArray>()
        
        var zipEntry = zipInputStream.nextEntry
        while (zipEntry != null) {
            entryCount++
            val entryName = zipEntry.name
            val entryBytes = zipInputStream.readBytes()
            extractedFiles[entryName] = entryBytes
            
            println("[DEBUG_LOG] Extracted ZIP entry: $entryName, size: ${entryBytes.size} bytes")
            zipInputStream.closeEntry()
            zipEntry = zipInputStream.nextEntry
        }
        zipInputStream.close()

        // Verify correct number of files in ZIP (should be 5 - one per page)
        assertEquals(5, entryCount, "ZIP should contain 5 files (one per page)")
        assertEquals(5, extractedFiles.size, "Should have extracted 5 files")

        // Verify each PDF file has exactly 1 page
        for ((fileName, pdfBytes) in extractedFiles) {
            val pdfDocument = Loader.loadPDF(pdfBytes)
            try {
                val actualPages = pdfDocument.numberOfPages
                assertEquals(1, actualPages, "$fileName should have exactly 1 page")
                println("[DEBUG_LOG] Verified $fileName: $actualPages page (expected: 1)")
            } finally {
                pdfDocument.close()
            }
        }

        println("[DEBUG_LOG] Auto-split to single pages completed successfully - all validations passed")
    }

    /**
     * Helper method to create a test PDF with specified number of pages.
     */
    private fun createTestPdfWithPages(pageCount: Int): ByteArray {
        val document = PDDocument()
        try {
            // Add pages to the document
            for (i in 1..pageCount) {
                val page = PDPage()
                document.addPage(page)
            }

            // Save to byte array
            val outputStream = ByteArrayOutputStream()
            document.save(outputStream)
            return outputStream.toByteArray()
        } finally {
            document.close()
        }
    }
}
