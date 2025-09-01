package org.example.pdfwrangler.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * Web UI Controller for serving the main PDF operations interface.
 * 
 * Task 151: Create web UI controller for main operations page
 */
@Controller
class WebUIController {

    /**
     * Serves the main web UI page at the root context path.
     */
    @GetMapping("/")
    fun index(model: Model): String {
        // Define operation categories and their operations
        val operationCategories = mapOf(
            "Basic Operations" to listOf(
                Operation("merge", "Merge PDFs", "Combine multiple PDF files into one", "/api/pdf/merge", "📄"),
                Operation("split", "Split PDF", "Split a PDF into multiple files", "/api/pdf/split", "✂️"),
                Operation("split-ranges", "Split by Page Ranges", "Split PDF by specific page ranges", "/api/pdf/split/page-ranges", "📑"),
                Operation("split-size", "Split by File Size", "Split PDF based on file size limits", "/api/pdf/split/file-size", "💾")
            ),
            "Conversion" to listOf(
                Operation("pdf-to-image", "PDF to Images", "Convert PDF pages to image files", "/api/conversion/pdf-to-images", "🖼️"),
                Operation("image-to-pdf", "Images to PDF", "Convert images to PDF document", "/api/conversion/images-to-pdf", "📷")
            ),
            "Page Operations" to listOf(
                Operation("rotate", "Rotate Pages", "Rotate PDF pages by specified angles", "/api/pages/rotate", "🔄"),
                Operation("rearrange", "Rearrange Pages", "Change the order of PDF pages", "/api/pages/rearrange", "🔀"),
                Operation("scale", "Scale Pages", "Resize PDF pages", "/api/pages/scale", "📏"),
                Operation("crop", "Crop Pages", "Crop PDF pages to specific dimensions", "/api/pages/crop", "✂️"),
                Operation("blank-detection", "Detect Blank Pages", "Identify blank pages in PDF", "/api/pages/detect-blank", "🔍"),
                Operation("page-numbers", "Add Page Numbers", "Add custom page numbering", "/api/pages/add-numbers", "🔢")
            ),
            "Visual Enhancement" to listOf(
                Operation("text-watermark", "Text Watermark", "Add text watermarks to PDF", "/api/watermark/text", "💬"),
                Operation("image-watermark", "Image Watermark", "Add image watermarks to PDF", "/api/watermark/image", "🖼️"),
                Operation("image-overlay", "Image Overlay", "Apply image overlays to PDF pages", "/api/visual/image-overlay", "🎨"),
                Operation("stamp", "Apply Stamps", "Add stamps to PDF documents", "/api/visual/stamp", "📋"),
                Operation("watermark-preview", "Watermark Preview", "Preview watermark before applying", "/api/watermark/preview", "👁️")
            ),
            "Batch Operations" to listOf(
                Operation("batch-merge", "Batch Merge", "Merge multiple sets of PDFs", "/api/pdf/batch-merge", "📚"),
                Operation("batch-split", "Batch Split", "Split multiple PDFs at once", "/api/pdf/batch-split", "📄"),
                Operation("batch-conversion", "Batch Conversion", "Convert multiple files at once", "/api/conversion/batch", "🔄"),
                Operation("batch-watermark", "Batch Watermarking", "Apply watermarks to multiple PDFs", "/api/watermark/batch", "💧")
            )
        )
        
        model.addAttribute("operationCategories", operationCategories)
        return "index"
    }
}

/**
 * Data class representing a PDF operation.
 */
data class Operation(
    val id: String,
    val title: String,
    val description: String,
    val endpoint: String,
    val icon: String
)