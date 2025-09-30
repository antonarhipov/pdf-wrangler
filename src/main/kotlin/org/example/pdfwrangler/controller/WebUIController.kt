package org.example.pdfwrangler.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.http.ResponseEntity

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
                Operation("merge", "Merge PDFs", "Combine multiple PDF files into one", "/operations/merge", "📄"),
                Operation("split", "Split PDF", "Split a PDF into multiple files", "/operations/split", "✂️")
            ),
            "Conversion" to listOf(
                Operation("pdf-to-image", "PDF to Images", "Convert PDF pages to image files", "/operations/pdf-to-image", "🖼️"),
                Operation("image-to-pdf", "Images to PDF", "Convert images to PDF document", "/operations/image-to-pdf", "📷")
            ),
            "Page Operations" to listOf(
                Operation("rotate", "Rotate Pages", "Rotate PDF pages by specified angles", "/operations/rotate", "🔄"),
                Operation("rearrange", "Rearrange Pages", "Change the order of PDF pages", "/operations/rearrange", "🔀"),
                Operation("scale", "Scale Pages", "Resize PDF pages", "/operations/scale", "📏"),
                Operation("crop", "Crop Pages", "Crop PDF pages to specific dimensions", "/operations/crop", "✂️"),
                Operation("blank-detection", "Detect Blank Pages", "Identify blank pages in PDF", "/operations/blank-detection", "🔍"),
                Operation("page-numbers", "Add Page Numbers", "Add custom page numbering", "/operations/page-numbers", "🔢")
            ),
            "Visual Enhancement" to listOf(
                Operation("text-watermark", "Text Watermark", "Add text watermarks to PDF", "/operations/text-watermark", "💬"),
                Operation("image-watermark", "Image Watermark", "Add image watermarks to PDF", "/operations/image-watermark", "🖼️"),
                Operation("image-overlay", "Image Overlay", "Apply image overlays to PDF pages", "/operations/image-overlay", "🎨"),
                Operation("stamp", "Apply Stamps", "Add stamps to PDF documents", "/operations/apply-stamps", "📋"),
                Operation("color-manipulation", "Color Manipulation", "Adjust colors in PDF documents", "/operations/color-manipulation", "🎨")
            ),
            "Batch Operations" to listOf(
                Operation("batch-merge", "Batch Merge", "Merge multiple sets of PDFs", "/operations/batch-merge", "📚"),
                Operation("batch-split", "Batch Split", "Split multiple PDFs at once", "/operations/batch-split", "📄"),
                Operation("batch-conversion", "Batch Conversion", "Convert multiple files at once", "/operations/batch-conversion", "🔄"),
                Operation("batch-watermark", "Batch Watermarking", "Apply watermarks to multiple PDFs", "/operations/batch-watermark", "💧")
            )
        )
        
        model.addAttribute("operationCategories", operationCategories)
        return "index"
    }

    /**
     * Serves the merge PDF operation page.
     */
    @GetMapping("/operations/merge")
    fun mergePage(model: Model): String {
        model.addAttribute("operationTitle", "Merge PDFs")
        model.addAttribute("operationDescription", "Combine multiple PDF files into one document")
        model.addAttribute("apiEndpoint", "/api/pdf/merge")
        return "operations/merge"
    }

    /**
     * Serves the split PDF operation page.
     */
    @GetMapping("/operations/split")
    fun splitPage(model: Model): String {
        model.addAttribute("operationTitle", "Split PDF")
        model.addAttribute("operationDescription", "Split a PDF into multiple files")
        model.addAttribute("apiEndpoint", "/api/pdf/split")
        return "operations/split"
    }

    /**
     * Serves the PDF to image conversion page.
     */
    @GetMapping("/operations/pdf-to-image")
    fun pdfToImagePage(model: Model): String {
        model.addAttribute("operationTitle", "PDF to Images")
        model.addAttribute("operationDescription", "Convert PDF pages to image files")
        model.addAttribute("apiEndpoint", "/api/pdf-to-image/convert")
        return "operations/pdf-to-image"
    }

    /**
     * Serves the images to PDF conversion page.
     */
    @GetMapping("/operations/image-to-pdf")
    fun imageToPdfPage(model: Model): String {
        model.addAttribute("operationTitle", "Images to PDF")
        model.addAttribute("operationDescription", "Convert multiple images to a PDF document")
        model.addAttribute("apiEndpoint", "/api/conversion/images-to-pdf")
        return "operations/image-to-pdf"
    }

    /**
     * Serves the text watermark operation page.
     */
    @GetMapping("/operations/text-watermark")
    fun textWatermarkPage(model: Model): String {
        model.addAttribute("operationTitle", "Text Watermark")
        model.addAttribute("operationDescription", "Add text watermarks to PDF documents")
        model.addAttribute("apiEndpoint", "/api/watermark/text")
        return "operations/text-watermark"
    }

    /**
     * Serves the image watermark operation page.
     */
    @GetMapping("/operations/image-watermark")
    fun imageWatermarkPage(model: Model): String {
        model.addAttribute("operationTitle", "Image Watermark")
        model.addAttribute("operationDescription", "Add image watermarks to PDF documents")
        model.addAttribute("apiEndpoint", "/api/watermark/image")
        return "operations/image-watermark"
    }

    /**
     * Serves the image overlay operation page.
     */
    @GetMapping("/operations/image-overlay")
    fun imageOverlayPage(model: Model): String {
        model.addAttribute("operationTitle", "Image Overlay")
        model.addAttribute("operationDescription", "Apply image overlays to PDF documents")
        model.addAttribute("apiEndpoint", "/api/visual-enhancement/image-overlay")
        return "operations/image-overlay"
    }

    /**
     * Serves the apply stamps operation page.
     */
    @GetMapping("/operations/apply-stamps")
    fun applyStampsPage(model: Model): String {
        model.addAttribute("operationTitle", "Apply Stamps")
        model.addAttribute("operationDescription", "Add stamps to PDF documents")
        model.addAttribute("apiEndpoint", "/api/visual-enhancement/stamp")
        return "operations/apply-stamps"
    }

    /**
     * Serves the color manipulation operation page.
     */
    @GetMapping("/operations/color-manipulation")
    fun colorManipulationPage(model: Model): String {
        model.addAttribute("operationTitle", "Color Manipulation")
        model.addAttribute("operationDescription", "Adjust colors and visual properties of PDF documents")
        model.addAttribute("apiEndpoint", "/api/visual-enhancement/color-manipulation")
        return "operations/color-manipulation"
    }

    /**
     * Serves the rotate pages operation page.
     */
    @GetMapping("/operations/rotate")
    fun rotatePage(model: Model): String {
        model.addAttribute("operationTitle", "Rotate Pages")
        model.addAttribute("operationDescription", "Rotate PDF pages by specified angles")
        model.addAttribute("apiEndpoint", "/api/pages/rotate")
        return "operations/rotate"
    }

    /**
     * Serves the rearrange pages operation page.
     */
    @GetMapping("/operations/rearrange")
    fun rearrangePage(model: Model): String {
        model.addAttribute("operationTitle", "Rearrange Pages")
        model.addAttribute("operationDescription", "Change the order of PDF pages")
        model.addAttribute("apiEndpoint", "/api/pages/rearrange")
        return "operations/rearrange"
    }

    /**
     * Serves the scale pages operation page.
     */
    @GetMapping("/operations/scale")
    fun scalePage(model: Model): String {
        model.addAttribute("operationTitle", "Scale Pages")
        model.addAttribute("operationDescription", "Resize PDF pages")
        model.addAttribute("apiEndpoint", "/api/pages/scale")
        return "operations/scale"
    }

    /**
     * Serves the crop pages operation page.
     */
    @GetMapping("/operations/crop")
    fun cropPage(model: Model): String {
        model.addAttribute("operationTitle", "Crop Pages")
        model.addAttribute("operationDescription", "Crop PDF pages to specific dimensions")
        model.addAttribute("apiEndpoint", "/api/pages/crop")
        return "operations/crop"
    }

    /**
     * Serves the blank page detection operation page.
     */
    @GetMapping("/operations/blank-detection")
    fun blankDetectionPage(model: Model): String {
        model.addAttribute("operationTitle", "Detect Blank Pages")
        model.addAttribute("operationDescription", "Identify blank pages in PDF")
        model.addAttribute("apiEndpoint", "/api/pages/detect-blank")
        return "operations/blank-detection"
    }

    /**
     * Serves the page numbers operation page.
     */
    @GetMapping("/operations/page-numbers")
    fun pageNumbersPage(model: Model): String {
        model.addAttribute("operationTitle", "Add Page Numbers")
        model.addAttribute("operationDescription", "Add custom page numbering")
        model.addAttribute("apiEndpoint", "/api/pages/add-page-numbers")
        return "operations/page-numbers"
    }

    /**
     * Handles browser requests to /favicon.ico to avoid error logs when no favicon is provided.
     * Returns 204 No Content as a minimal response.
     */
    @GetMapping("/favicon.ico")
    fun favicon(): ResponseEntity<Void> {
        return ResponseEntity.noContent().build()
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