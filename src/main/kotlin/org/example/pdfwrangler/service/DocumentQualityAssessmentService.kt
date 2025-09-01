package org.example.pdfwrangler.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.Loader
import org.example.pdfwrangler.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt

@Service
class DocumentQualityAssessmentService {
    
    private val logger = LoggerFactory.getLogger(DocumentQualityAssessmentService::class.java)
    
    data class QualityAssessmentConfig(
        val assessmentTypes: List<String> = listOf("resolution", "clarity", "color", "compression"),
        val detailedAnalysis: Boolean = false,
        val samplePages: Int = 5, // Number of pages to analyze for large documents
        val dpi: Float = 150f // DPI for image rendering during analysis
    )
    
    fun assessDocument(request: QualityAssessmentRequest): QualityAssessmentResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info("Starting quality assessment for file: ${request.file.originalFilename}")
            
            val tempFile = File.createTempFile("quality_assessment_", ".pdf")
            request.file.transferTo(tempFile)
            
            val document = Loader.loadPDF(tempFile)
            val config = QualityAssessmentConfig(
                assessmentTypes = request.assessmentTypes,
                detailedAnalysis = request.detailedAnalysis
            )
            
            val assessmentResults = performQualityAssessment(document, config)
            val overallScore = calculateOverallScore(assessmentResults)
            val recommendations = generateRecommendations(assessmentResults)
            
            document.close()
            tempFile.delete()
            
            val processingTime = System.currentTimeMillis() - startTime
            
            return QualityAssessmentResponse(
                success = true,
                message = "Quality assessment completed successfully",
                overallScore = overallScore,
                assessmentResults = assessmentResults,
                recommendations = recommendations,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            logger.error("Error during quality assessment", e)
            
            val processingTime = System.currentTimeMillis() - startTime
            return QualityAssessmentResponse(
                success = false,
                message = "Quality assessment failed: ${e.message}",
                overallScore = 0.0,
                assessmentResults = mapOf(
                    "error" to QualityMetric(
                        name = "error",
                        score = 0.0,
                        details = mapOf("error_message" to (e.message ?: "Unknown error")),
                        issues = listOf("Assessment failed"),
                        suggestions = listOf("Check document format and try again")
                    )
                ),
                recommendations = listOf("Document could not be assessed. Please verify the file is a valid PDF."),
                processingTimeMs = processingTime
            )
        }
    }
    
    private fun performQualityAssessment(
        document: PDDocument, 
        config: QualityAssessmentConfig
    ): Map<String, QualityMetric> {
        val results = mutableMapOf<String, QualityMetric>()
        
        if (config.assessmentTypes.contains("resolution")) {
            results["resolution"] = assessResolution(document, config)
        }
        
        if (config.assessmentTypes.contains("clarity")) {
            results["clarity"] = assessClarity(document, config)
        }
        
        if (config.assessmentTypes.contains("color")) {
            results["color"] = assessColorQuality(document, config)
        }
        
        if (config.assessmentTypes.contains("compression")) {
            results["compression"] = assessCompression(document, config)
        }
        
        if (config.assessmentTypes.contains("text")) {
            results["text"] = assessTextQuality(document, config)
        }
        
        if (config.assessmentTypes.contains("structure")) {
            results["structure"] = assessDocumentStructure(document, config)
        }
        
        return results
    }
    
    private fun assessResolution(document: PDDocument, config: QualityAssessmentConfig): QualityMetric {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()
        
        try {
            val renderer = PDFRenderer(document)
            val pagesToAnalyze = minOf(config.samplePages, document.numberOfPages)
            val resolutions = mutableListOf<Double>()
            
            for (pageIndex in 0 until pagesToAnalyze) {
                val image = renderer.renderImageWithDPI(pageIndex, config.dpi)
                val page = document.getPage(pageIndex)
                
                val pageWidthInches = page.mediaBox.width / 72.0 // Convert points to inches
                val pageHeightInches = page.mediaBox.height / 72.0
                
                val effectiveHorizontalDPI = image.width / pageWidthInches
                val effectiveVerticalDPI = image.height / pageHeightInches
                val averageDPI = (effectiveHorizontalDPI + effectiveVerticalDPI) / 2.0
                
                resolutions.add(averageDPI)
            }
            
            val averageResolution = resolutions.average()
            details["average_resolution_dpi"] = averageResolution
            details["resolution_samples"] = resolutions
            details["pages_analyzed"] = pagesToAnalyze
            
            val score = when {
                averageResolution >= 300 -> 95.0
                averageResolution >= 150 -> 80.0
                averageResolution >= 100 -> 60.0
                averageResolution >= 72 -> 40.0
                else -> 20.0
            }
            
            if (averageResolution < 150) {
                issues.add("Low resolution detected (${averageResolution.toInt()} DPI)")
                suggestions.add("Consider using higher resolution scanning (300+ DPI for text, 600+ DPI for images)")
            }
            
            if (averageResolution < 72) {
                issues.add("Very low resolution may cause readability issues")
                suggestions.add("Re-scan or re-create document with higher resolution")
            }
            
            return QualityMetric(
                name = "resolution",
                score = score,
                details = details.toMap(),
                issues = issues,
                suggestions = suggestions
            )
            
        } catch (e: Exception) {
            logger.warn("Error assessing resolution", e)
            return QualityMetric(
                name = "resolution",
                score = 0.0,
                details = mapOf("error" to (e.message ?: "Unknown error")),
                issues = listOf("Could not assess resolution"),
                suggestions = listOf("Check document format")
            )
        }
    }
    
    private fun assessClarity(document: PDDocument, config: QualityAssessmentConfig): QualityMetric {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()
        
        try {
            val renderer = PDFRenderer(document)
            val pagesToAnalyze = minOf(config.samplePages, document.numberOfPages)
            val clarityScores = mutableListOf<Double>()
            
            for (pageIndex in 0 until pagesToAnalyze) {
                val image = renderer.renderImageWithDPI(pageIndex, config.dpi)
                val clarityScore = calculateImageClarity(image)
                clarityScores.add(clarityScore)
            }
            
            val averageClarity = clarityScores.average()
            details["average_clarity_score"] = averageClarity
            details["clarity_samples"] = clarityScores
            details["pages_analyzed"] = pagesToAnalyze
            
            val score = when {
                averageClarity >= 0.8 -> 90.0
                averageClarity >= 0.6 -> 75.0
                averageClarity >= 0.4 -> 60.0
                averageClarity >= 0.2 -> 40.0
                else -> 20.0
            }
            
            if (averageClarity < 0.5) {
                issues.add("Low image clarity detected")
                suggestions.add("Consider improving scan quality or using denoising filters")
            }
            
            if (clarityScores.any { it < 0.3 }) {
                issues.add("Some pages have very poor clarity")
                suggestions.add("Re-scan affected pages with better settings")
            }
            
            return QualityMetric(
                name = "clarity",
                score = score,
                details = details.toMap(),
                issues = issues,
                suggestions = suggestions
            )
            
        } catch (e: Exception) {
            logger.warn("Error assessing clarity", e)
            return QualityMetric(
                name = "clarity",
                score = 0.0,
                details = mapOf("error" to (e.message ?: "Unknown error")),
                issues = listOf("Could not assess clarity"),
                suggestions = listOf("Check document format")
            )
        }
    }
    
    private fun assessColorQuality(document: PDDocument, config: QualityAssessmentConfig): QualityMetric {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()
        
        try {
            val renderer = PDFRenderer(document)
            val pagesToAnalyze = minOf(config.samplePages, document.numberOfPages)
            var totalColorRichness = 0.0
            var hasColorContent = false
            
            for (pageIndex in 0 until pagesToAnalyze) {
                val image = renderer.renderImageWithDPI(pageIndex, config.dpi)
                val colorAnalysis = analyzeColorDistribution(image)
                
                totalColorRichness += colorAnalysis.colorRichness
                if (colorAnalysis.hasSignificantColor) {
                    hasColorContent = true
                }
            }
            
            val averageColorRichness = totalColorRichness / pagesToAnalyze
            details["average_color_richness"] = averageColorRichness
            details["has_color_content"] = hasColorContent
            details["pages_analyzed"] = pagesToAnalyze
            
            val score = when {
                !hasColorContent && averageColorRichness < 0.1 -> 85.0 // Good for B&W documents
                hasColorContent && averageColorRichness >= 0.7 -> 90.0
                hasColorContent && averageColorRichness >= 0.5 -> 75.0
                hasColorContent && averageColorRichness >= 0.3 -> 60.0
                else -> 45.0
            }
            
            if (hasColorContent && averageColorRichness < 0.4) {
                issues.add("Color content detected but poor color quality")
                suggestions.add("Consider using higher quality color scanning settings")
            }
            
            return QualityMetric(
                name = "color",
                score = score,
                details = details.toMap(),
                issues = issues,
                suggestions = suggestions
            )
            
        } catch (e: Exception) {
            logger.warn("Error assessing color quality", e)
            return QualityMetric(
                name = "color",
                score = 0.0,
                details = mapOf("error" to (e.message ?: "Unknown error")),
                issues = listOf("Could not assess color quality"),
                suggestions = listOf("Check document format")
            )
        }
    }
    
    private fun assessCompression(document: PDDocument, config: QualityAssessmentConfig): QualityMetric {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()
        
        try {
            val pageCount = document.numberOfPages
            val estimatedFileSize = pageCount * 50000 // Rough estimate
            
            // This is a simplified compression assessment
            // In a real implementation, you would analyze the actual PDF structure
            val compressionRatio = if (estimatedFileSize > 0) {
                kotlin.math.min(1.0, 100000.0 / estimatedFileSize) // Simplified calculation
            } else {
                0.5
            }
            
            details["page_count"] = pageCount
            details["estimated_compression_ratio"] = compressionRatio
            
            val score = when {
                compressionRatio >= 0.8 -> 90.0
                compressionRatio >= 0.6 -> 75.0
                compressionRatio >= 0.4 -> 60.0
                compressionRatio >= 0.2 -> 45.0
                else -> 30.0
            }
            
            if (compressionRatio < 0.5) {
                issues.add("Document may be over-compressed")
                suggestions.add("Consider using less aggressive compression settings")
            }
            
            return QualityMetric(
                name = "compression",
                score = score,
                details = details.toMap(),
                issues = issues,
                suggestions = suggestions
            )
            
        } catch (e: Exception) {
            logger.warn("Error assessing compression", e)
            return QualityMetric(
                name = "compression",
                score = 0.0,
                details = mapOf("error" to (e.message ?: "Unknown error")),
                issues = listOf("Could not assess compression"),
                suggestions = listOf("Check document format")
            )
        }
    }
    
    private fun assessTextQuality(document: PDDocument, config: QualityAssessmentConfig): QualityMetric {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()
        
        try {
            var hasSelectableText = false
            var fontCount = 0
            val fontNames = mutableSetOf<String>()
            
            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                
                // Check for text content (simplified)
                val pageText = org.apache.pdfbox.text.PDFTextStripper().apply {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }.getText(document)
                
                if (pageText.isNotBlank()) {
                    hasSelectableText = true
                }
            }
            
            details["has_selectable_text"] = hasSelectableText
            details["font_count"] = fontCount
            details["font_names"] = fontNames.toList()
            
            val score = when {
                hasSelectableText && fontCount > 0 -> 85.0
                hasSelectableText -> 70.0
                else -> 30.0 // Likely scanned document without OCR
            }
            
            if (!hasSelectableText) {
                issues.add("No selectable text found - document may be scanned")
                suggestions.add("Consider applying OCR to make text searchable")
            }
            
            return QualityMetric(
                name = "text",
                score = score,
                details = details.toMap(),
                issues = issues,
                suggestions = suggestions
            )
            
        } catch (e: Exception) {
            logger.warn("Error assessing text quality", e)
            return QualityMetric(
                name = "text",
                score = 0.0,
                details = mapOf("error" to (e.message ?: "Unknown error")),
                issues = listOf("Could not assess text quality"),
                suggestions = listOf("Check document format")
            )
        }
    }
    
    private fun assessDocumentStructure(document: PDDocument, config: QualityAssessmentConfig): QualityMetric {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val details = mutableMapOf<String, Any>()
        
        try {
            val pageCount = document.numberOfPages
            val hasBookmarks = document.documentCatalog.documentOutline != null
            val hasMetadata = document.documentInformation != null
            
            details["page_count"] = pageCount
            details["has_bookmarks"] = hasBookmarks
            details["has_metadata"] = hasMetadata
            
            var structureScore = 50.0
            if (hasBookmarks) structureScore += 20.0
            if (hasMetadata) structureScore += 15.0
            if (pageCount > 1) structureScore += 15.0
            
            val score = minOf(100.0, structureScore)
            
            if (!hasBookmarks && pageCount > 10) {
                issues.add("Large document without bookmarks")
                suggestions.add("Consider adding bookmarks for better navigation")
            }
            
            if (!hasMetadata) {
                issues.add("Missing document metadata")
                suggestions.add("Add title, author, and subject metadata")
            }
            
            return QualityMetric(
                name = "structure",
                score = score,
                details = details.toMap(),
                issues = issues,
                suggestions = suggestions
            )
            
        } catch (e: Exception) {
            logger.warn("Error assessing document structure", e)
            return QualityMetric(
                name = "structure",
                score = 0.0,
                details = mapOf("error" to (e.message ?: "Unknown error")),
                issues = listOf("Could not assess document structure"),
                suggestions = listOf("Check document format")
            )
        }
    }
    
    private fun calculateImageClarity(image: BufferedImage): Double {
        // Simplified clarity calculation using edge detection
        val width = image.width
        val height = image.height
        var edgeCount = 0
        var totalPixels = 0
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = image.getRGB(x, y) and 0xFF
                val right = image.getRGB(x + 1, y) and 0xFF
                val bottom = image.getRGB(x, y + 1) and 0xFF
                
                val horizontalGradient = abs(center - right)
                val verticalGradient = abs(center - bottom)
                val gradient = sqrt((horizontalGradient * horizontalGradient + verticalGradient * verticalGradient).toDouble())
                
                if (gradient > 30) { // Threshold for edge detection
                    edgeCount++
                }
                totalPixels++
            }
        }
        
        return if (totalPixels > 0) edgeCount.toDouble() / totalPixels else 0.0
    }
    
    private data class ColorAnalysis(
        val colorRichness: Double,
        val hasSignificantColor: Boolean
    )
    
    private fun analyzeColorDistribution(image: BufferedImage): ColorAnalysis {
        val width = image.width
        val height = image.height
        var colorVariance = 0.0
        var colorfulPixels = 0
        val sampleStep = maxOf(1, width / 100) // Sample every nth pixel for performance
        
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                
                val colorDiff = maxOf(abs(r - g), abs(g - b), abs(r - b))
                colorVariance += colorDiff
                
                if (colorDiff > 20) { // Threshold for significant color
                    colorfulPixels++
                }
            }
        }
        
        val totalSamples = (height / sampleStep) * (width / sampleStep)
        val averageColorVariance = if (totalSamples > 0) colorVariance / totalSamples else 0.0
        val colorRichness = minOf(1.0, averageColorVariance / 100.0)
        val hasSignificantColor = colorfulPixels.toDouble() / totalSamples > 0.1
        
        return ColorAnalysis(colorRichness, hasSignificantColor)
    }
    
    private fun calculateOverallScore(assessmentResults: Map<String, QualityMetric>): Double {
        if (assessmentResults.isEmpty()) return 0.0
        
        val weights = mapOf(
            "resolution" to 0.25,
            "clarity" to 0.25,
            "color" to 0.15,
            "compression" to 0.1,
            "text" to 0.15,
            "structure" to 0.1
        )
        
        var totalWeightedScore = 0.0
        var totalWeight = 0.0
        
        assessmentResults.forEach { (type, metric) ->
            val weight = weights[type] ?: 0.1
            totalWeightedScore += metric.score * weight
            totalWeight += weight
        }
        
        return if (totalWeight > 0) totalWeightedScore / totalWeight else 0.0
    }
    
    private fun generateRecommendations(assessmentResults: Map<String, QualityMetric>): List<String> {
        val recommendations = mutableListOf<String>()
        
        assessmentResults.values.forEach { metric ->
            recommendations.addAll(metric.suggestions)
        }
        
        // Add general recommendations based on overall analysis
        val averageScore = assessmentResults.values.map { it.score }.average()
        
        when {
            averageScore >= 80 -> recommendations.add("Document quality is excellent")
            averageScore >= 60 -> recommendations.add("Document quality is good with minor improvements possible")
            averageScore >= 40 -> recommendations.add("Document quality is fair - consider improvements")
            else -> recommendations.add("Document quality is poor - significant improvements recommended")
        }
        
        return recommendations.distinct()
    }
}