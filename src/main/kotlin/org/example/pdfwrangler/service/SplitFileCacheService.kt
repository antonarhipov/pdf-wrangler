package org.example.pdfwrangler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared cache service for storing split PDF files during operations.
 * This service provides a centralized cache that all split services can use
 * to store and retrieve files by operation ID, solving the cache isolation problem.
 */
@Service
class SplitFileCacheService {
    
    private val logger = LoggerFactory.getLogger(SplitFileCacheService::class.java)
    
    // Cache for tracking split files during operations (survives thread context switches)
    // Key: operationId, Value: Map of fileName to File
    private val splitFilesCache = ConcurrentHashMap<String, MutableMap<String, File>>()
    
    /**
     * Initializes a cache entry for a new operation.
     */
    fun initializeCache(operationId: String) {
        splitFilesCache[operationId] = mutableMapOf()
        logger.debug("Initialized cache for operation: {}", operationId)
    }
    
    /**
     * Stores a file in the cache for the given operation.
     */
    fun storeFile(operationId: String, fileName: String, file: File) {
        splitFilesCache[operationId]?.put(fileName, file)
        logger.debug("Stored file in cache: {} for operation: {}", fileName, operationId)
    }
    
    /**
     * Retrieves all files for a given operation.
     */
    fun getFiles(operationId: String): Map<String, File>? {
        return splitFilesCache[operationId]
    }
    
    /**
     * Retrieves a specific file for a given operation.
     */
    fun getFile(operationId: String, fileName: String): File? {
        return splitFilesCache[operationId]?.get(fileName)
    }
    
    /**
     * Removes all files for a given operation from the cache.
     */
    fun clearCache(operationId: String) {
        splitFilesCache.remove(operationId)
        logger.debug("Cleared cache for operation: {}", operationId)
    }
    
    /**
     * Checks if cache exists for a given operation.
     */
    fun hasCacheFor(operationId: String): Boolean {
        return splitFilesCache.containsKey(operationId)
    }
    
    /**
     * Gets the number of files cached for a given operation.
     */
    fun getCacheSize(operationId: String): Int {
        return splitFilesCache[operationId]?.size ?: 0
    }
}
