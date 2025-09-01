package org.example.pdfwrangler.exception

/**
 * Operation-specific exception classes for different PDF operations.
 * Each exception provides specialized context and error handling for its operation type.
 */

/**
 * Exception for PDF merge operation failures
 */
open class MergeException(
    message: String,
    cause: Throwable? = null,
    val inputFiles: List<String> = emptyList(),
    val mergeStrategy: String? = null,
    val sortingOption: String? = null,
    val currentFileIndex: Int? = null,
    val totalFiles: Int? = null,
    val preserveBookmarks: Boolean = true,
    val preserveMetadata: Boolean = true,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_MERGE_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.MERGE,
    operationContext = buildMap {
        put("inputFileCount", inputFiles.size)
        if (inputFiles.isNotEmpty()) put("inputFiles", inputFiles.take(10)) // Limit for logging
        mergeStrategy?.let { put("mergeStrategy", it) }
        sortingOption?.let { put("sortingOption", it) }
        currentFileIndex?.let { put("currentFileIndex", it) }
        totalFiles?.let { put("totalFiles", it) }
        put("preserveBookmarks", preserveBookmarks)
        put("preserveMetadata", preserveMetadata)
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Specialized merge exception for file access issues
 */
class MergeFileAccessException(
    message: String,
    cause: Throwable? = null,
    val problematicFile: String,
    val accessType: String = "READ"
) : MergeException(
    message = message,
    cause = cause,
    inputFiles = listOf(problematicFile),
    recoverable = false,
    retryable = false
)

/**
 * Exception for signature removal failures during merge
 */
class MergeSignatureRemovalException(
    message: String,
    cause: Throwable? = null,
    val fileWithSignature: String,
    val signatureType: String? = null
) : MergeException(
    message = message,
    cause = cause,
    inputFiles = listOf(fileWithSignature),
    recoverable = true,
    retryable = false
)

/**
 * Exception for PDF split operation failures
 */
open class SplitException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val splitStrategy: String? = null,
    val pageRanges: List<String> = emptyList(),
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val outputPattern: String? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_SPLIT_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.SPLIT,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        splitStrategy?.let { put("splitStrategy", it) }
        if (pageRanges.isNotEmpty()) put("pageRanges", pageRanges)
        currentPage?.let { put("currentPage", it) }
        totalPages?.let { put("totalPages", it) }
        outputPattern?.let { put("outputPattern", it) }
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for invalid page range specifications
 */
class SplitInvalidPageRangeException(
    message: String,
    val invalidRange: String,
    totalPagesCount: Int
) : SplitException(
    message = message,
    pageRanges = listOf(invalidRange),
    totalPages = totalPagesCount,
    recoverable = false,
    retryable = false
)

/**
 * Exception for file size threshold split failures
 */
class SplitFileSizeThresholdException(
    message: String,
    cause: Throwable? = null,
    val targetSizeMB: Long,
    val actualSizeMB: Long,
    sourceFileName: String
) : SplitException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    splitStrategy = "FILE_SIZE",
    recoverable = true,
    retryable = false
)

/**
 * Exception for PDF conversion operation failures
 */
open class ConversionException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val sourceFormat: String? = null,
    val targetFormat: String? = null,
    val conversionStage: String? = null,
    val qualitySettings: Map<String, Any> = emptyMap(),
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_CONVERSION_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.CONVERSION,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        sourceFormat?.let { put("sourceFormat", it) }
        targetFormat?.let { put("targetFormat", it) }
        conversionStage?.let { put("conversionStage", it) }
        if (qualitySettings.isNotEmpty()) put("qualitySettings", qualitySettings)
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for unsupported format conversions
 */
class ConversionUnsupportedFormatException(
    message: String,
    val unsupportedFormat: String,
    val supportedFormats: List<String>
) : ConversionException(
    message = message,
    sourceFormat = unsupportedFormat,
    recoverable = false,
    retryable = false
)

/**
 * Exception for image conversion failures
 */
class ConversionImageException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    targetFormatName: String,
    val dpi: Int? = null,
    val colorMode: String? = null
) : ConversionException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    targetFormat = targetFormatName,
    conversionStage = "IMAGE_CONVERSION"
)

/**
 * Exception for PDF watermarking operation failures
 */
open class WatermarkException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val watermarkType: String? = null,
    val watermarkContent: String? = null,
    val position: String? = null,
    val opacity: Float? = null,
    val rotation: Float? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_WATERMARK_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.WATERMARKING,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        watermarkType?.let { put("watermarkType", it) }
        watermarkContent?.let { put("watermarkContent", it.take(50)) } // Truncate for logging
        position?.let { put("position", it) }
        opacity?.let { put("opacity", it) }
        rotation?.let { put("rotation", it) }
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for text watermark failures
 */
class WatermarkTextException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val text: String,
    val fontSize: Int? = null,
    val fontFamily: String? = null,
    val color: String? = null
) : WatermarkException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    watermarkType = "TEXT",
    watermarkContent = text
)

/**
 * Exception for image watermark failures
 */
class WatermarkImageException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val watermarkImageFile: String,
    val scalingFactor: Float? = null
) : WatermarkException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    watermarkType = "IMAGE",
    watermarkContent = watermarkImageFile
)

/**
 * Exception for PDF page manipulation operation failures
 */
open class PageManipulationException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val operation: String? = null,
    val pageNumbers: List<Int> = emptyList(),
    val totalPages: Int? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_PAGE_MANIPULATION_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.MANIPULATION,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        operation?.let { put("operation", it) }
        if (pageNumbers.isNotEmpty()) put("pageNumbers", pageNumbers)
        totalPages?.let { put("totalPages", it) }
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for page rotation failures
 */
class PageRotationException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    pages: List<Int>,
    val rotation: Int
) : PageManipulationException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    operation = "ROTATION",
    pageNumbers = pages
)

/**
 * Exception for page reordering failures
 */
class PageReorderException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val originalOrder: List<Int>,
    val newOrder: List<Int>
) : PageManipulationException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    operation = "REORDER",
    pageNumbers = originalOrder
)

/**
 * Exception for PDF extraction operation failures
 */
open class ExtractionException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val extractionType: String? = null,
    val pageRange: String? = null,
    val outputFormat: String? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_EXTRACTION_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.EXTRACTION,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        extractionType?.let { put("extractionType", it) }
        pageRange?.let { put("pageRange", it) }
        outputFormat?.let { put("outputFormat", it) }
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for text extraction failures
 */
class ExtractionTextException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val textLength: Int? = null,
    val encoding: String? = null
) : ExtractionException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    extractionType = "TEXT"
)

/**
 * Exception for image extraction failures
 */
class ExtractionImageException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val imageCount: Int? = null,
    val imageFormat: String? = null
) : ExtractionException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    extractionType = "IMAGE"
)

/**
 * Exception for table extraction failures
 */
class ExtractionTableException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val tableCount: Int? = null,
    val currentTable: Int? = null
) : ExtractionException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    extractionType = "TABLE",
    outputFormat = "CSV"
)

/**
 * Exception for PDF optimization operation failures
 */
open class OptimizationException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val optimizationType: String? = null,
    val originalSizeMB: Long? = null,
    val targetSizeMB: Long? = null,
    val compressionLevel: String? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_OPTIMIZATION_ERROR",
    severity = OperationSeverity.MEDIUM,
    operationType = PdfOperationType.OPTIMIZATION,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        optimizationType?.let { put("optimizationType", it) }
        originalSizeMB?.let { put("originalSizeMB", it) }
        targetSizeMB?.let { put("targetSizeMB", it) }
        compressionLevel?.let { put("compressionLevel", it) }
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for compression failures
 */
class OptimizationCompressionException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    compressionLevelValue: String,
    val algorithm: String? = null
) : OptimizationException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    optimizationType = "COMPRESSION",
    compressionLevel = compressionLevelValue
)

/**
 * Exception for image optimization failures
 */
class OptimizationImageException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val imageCount: Int,
    val currentImage: Int? = null,
    val qualityReduction: Int? = null
) : OptimizationException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    optimizationType = "IMAGE_OPTIMIZATION"
)

/**
 * Exception for PDF analysis operation failures
 */
open class AnalysisException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val analysisType: String? = null,
    val analysisStage: String? = null,
    recoverable: Boolean = true,
    retryable: Boolean = true
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_ANALYSIS_ERROR",
    severity = OperationSeverity.LOW,
    operationType = PdfOperationType.ANALYSIS,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        analysisType?.let { put("analysisType", it) }
        analysisStage?.let { put("analysisStage", it) }
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for document structure analysis failures
 */
class AnalysisStructureException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val elementType: String? = null,
    val elementCount: Int? = null
) : AnalysisException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    analysisType = "STRUCTURE"
)

/**
 * Exception for quality assessment failures
 */
class AnalysisQualityException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val qualityMetric: String? = null,
    val threshold: Double? = null,
    val actualValue: Double? = null
) : AnalysisException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    analysisType = "QUALITY_ASSESSMENT"
)

/**
 * Exception for PDF metadata operation failures
 */
open class MetadataException(
    message: String,
    cause: Throwable? = null,
    val sourceFile: String? = null,
    val operation: String? = null,
    val metadataFields: List<String> = emptyList(),
    recoverable: Boolean = true,
    retryable: Boolean = false
) : PdfOperationException(
    message = message,
    cause = cause,
    errorCode = "PDF_METADATA_ERROR",
    severity = OperationSeverity.LOW,
    operationType = PdfOperationType.METADATA,
    operationContext = buildMap {
        sourceFile?.let { put("sourceFile", it) }
        operation?.let { put("operation", it) }
        if (metadataFields.isNotEmpty()) put("metadataFields", metadataFields)
    },
    recoverable = recoverable,
    retryable = retryable
)

/**
 * Exception for metadata extraction failures
 */
class MetadataExtractionException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    metadataFieldList: List<String>
) : MetadataException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    operation = "EXTRACTION",
    metadataFields = metadataFieldList
)

/**
 * Exception for metadata modification failures
 */
class MetadataModificationException(
    message: String,
    cause: Throwable? = null,
    sourceFileName: String,
    val modifications: Map<String, String>,
    val failedField: String? = null
) : MetadataException(
    message = message,
    cause = cause,
    sourceFile = sourceFileName,
    operation = "MODIFICATION",
    metadataFields = modifications.keys.toList()
)