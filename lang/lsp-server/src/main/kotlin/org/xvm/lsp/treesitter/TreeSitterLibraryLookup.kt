package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.NativeLibraryLookup
import org.slf4j.LoggerFactory
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Custom NativeLibraryLookup implementation that loads libtree-sitter from JAR resources.
 *
 * jtreesitter requires the tree-sitter runtime library (libtree-sitter) but doesn't bundle it.
 * This implementation extracts the library from JAR resources to a temp file and loads it.
 *
 * The library is bundled at: /native/<platform>/libtree-sitter.<ext>
 */
class TreeSitterLibraryLookup : NativeLibraryLookup {
    override fun get(arena: Arena): SymbolLookup {
        val libraryPath = extractLibraryToTemp()
        logger.info("loading tree-sitter runtime from: {}", libraryPath)
        return SymbolLookup.libraryLookup(libraryPath, arena)
    }

    private fun extractLibraryToTemp(): java.nio.file.Path {
        val resourcePath = Platform.resourcePath("tree-sitter")
        logger.info("loading tree-sitter runtime from resource: {}", resourcePath)

        javaClass.getResourceAsStream(resourcePath)?.use { inputStream ->
            val tempFile = Files.createTempFile("libtree-sitter", Platform.libExtension)
            tempFile.toFile().deleteOnExit()
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
            logger.info("extracted tree-sitter runtime to: {}", tempFile)
            return tempFile
        }

        throw IllegalStateException(
            "tree-sitter runtime library not found at $resourcePath. " +
                "Build it with: ./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources",
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TreeSitterLibraryLookup::class.java)
    }
}
