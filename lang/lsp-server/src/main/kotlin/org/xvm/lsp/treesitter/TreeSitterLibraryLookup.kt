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
        logger.info("Loading tree-sitter runtime from: {}", libraryPath)
        return SymbolLookup.libraryLookup(libraryPath, arena)
    }

    private fun extractLibraryToTemp(): java.nio.file.Path {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val (platform, extension) =
            when {
                osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) ->
                    "darwin-arm64" to ".dylib"
                osName.contains("mac") ->
                    "darwin-x64" to ".dylib"
                osName.contains("linux") && (osArch.contains("aarch64") || osArch.contains("arm64")) ->
                    "linux-arm64" to ".so"
                osName.contains("linux") ->
                    "linux-x64" to ".so"
                osName.contains("windows") ->
                    "windows-x64" to ".dll"
                else -> throw IllegalStateException("Unsupported platform: $osName/$osArch")
            }

        val resourcePath = "/native/$platform/libtree-sitter$extension"
        logger.debug("Loading tree-sitter runtime from resource: {}", resourcePath)

        javaClass.getResourceAsStream(resourcePath)?.use { inputStream ->
            val tempFile = Files.createTempFile("libtree-sitter", extension)
            tempFile.toFile().deleteOnExit()
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
            logger.info("Extracted tree-sitter runtime to: {}", tempFile)
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
