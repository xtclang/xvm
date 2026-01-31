package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Parser
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Parser wrapper for XTC sources using Tree-sitter.
 *
 * Provides fast, incremental, error-tolerant parsing for syntax-level intelligence.
 * This parser works even on incomplete or syntactically invalid code, making it ideal
 * for real-time editor features like completion and symbol outline.
 *
 * Note: This requires the compiled tree-sitter-xtc grammar library to be available
 * as a native library. The grammar is generated from XtcLanguage.kt by TreeSitterGenerator.
 */
class XtcParser : Closeable {
    private val parser: Parser
    private val language: Language = loadXtcLanguage()

    @Volatile
    private var closed = false

    init {
        parser = Parser()
        parser.setLanguage(language)
        logger.debug("XtcParser initialized with tree-sitter")
    }

    /**
     * Parse source code into a syntax tree.
     *
     * @param source the XTC source code to parse
     * @return the parsed syntax tree
     * @throws IllegalStateException if the parser has been closed
     */
    fun parse(source: String): XtcTree = parse(source, null)

    /**
     * Parse source code into a syntax tree with incremental parsing.
     *
     * If an old tree is provided, the parser will reuse as much of the old tree
     * as possible, making this operation very fast for small edits.
     *
     * @param source  the XTC source code to parse
     * @param oldTree the previous tree for incremental parsing, or null for full parse
     * @return the parsed syntax tree
     * @throws IllegalStateException if the parser has been closed
     */
    fun parse(
        source: String,
        oldTree: XtcTree?,
    ): XtcTree {
        check(!closed) { "Parser has been closed" }

        val tree =
            if (oldTree != null) {
                parser.parse(source, oldTree.tsTree)
            } else {
                parser.parse(source)
            }.orElseThrow { IllegalStateException("Failed to parse source") }

        return XtcTree(tree, source)
    }

    /**
     * Get the language used by this parser.
     */
    fun getLanguage(): Language = language

    override fun close() {
        if (!closed) {
            closed = true
            parser.close()
            logger.debug("XtcParser closed")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(XtcParser::class.java)
        private const val GRAMMAR_LIBRARY_NAME = "tree-sitter-xtc"
        private const val LANGUAGE_FUNCTION = "tree_sitter_xtc"

        // Arena for native library - must remain open while language is in use
        private val arena: Arena = Arena.global()

        /**
         * Load the XTC tree-sitter language from native library.
         *
         * The library is expected to be in the native resources directory:
         * - darwin-arm64/libtree-sitter-xtc.dylib
         * - darwin-x64/libtree-sitter-xtc.dylib
         * - linux-x64/libtree-sitter-xtc.so
         * - linux-arm64/libtree-sitter-xtc.so
         * - windows-x64/tree-sitter-xtc.dll
         */
        private fun loadXtcLanguage(): Language {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val (platform, extension, libPrefix) =
                when {
                    osName.contains("mac") || osName.contains("darwin") -> {
                        val arch = if (osArch.contains("aarch64") || osArch.contains("arm64")) "darwin-arm64" else "darwin-x64"
                        Triple(arch, ".dylib", "lib")
                    }
                    osName.contains("linux") -> {
                        val arch = if (osArch.contains("aarch64") || osArch.contains("arm64")) "linux-arm64" else "linux-x64"
                        Triple(arch, ".so", "lib")
                    }
                    osName.contains("windows") -> Triple("windows-x64", ".dll", "")
                    else -> throw IllegalStateException("Unsupported platform: $osName/$osArch")
                }

            val libraryFileName = "$libPrefix$GRAMMAR_LIBRARY_NAME$extension"
            val resourcePath = "/native/$platform/$libraryFileName"

            logger.debug("Loading XTC grammar from: {}", resourcePath)

            // Try to load from resources (bundled in JAR)
            XtcParser::class.java.getResourceAsStream(resourcePath)?.use { inputStream ->
                val tempFile = Files.createTempFile(GRAMMAR_LIBRARY_NAME, extension)
                tempFile.toFile().deleteOnExit()
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)

                logger.info("Extracted XTC grammar to: {}", tempFile)
                return loadLanguageFromPath(tempFile)
            }

            // Fallback: try to load from system library path
            logger.debug("Resource not found, trying system library path")
            try {
                return loadLanguageFromSystemPath()
            } catch (e: Exception) {
                logger.error(
                    "Failed to load XTC tree-sitter grammar. " +
                        "The native library is not available. " +
                        "Run './gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate' to compile it.",
                )
                throw IllegalStateException(
                    "XTC tree-sitter grammar not available at $resourcePath. Build it with: " +
                        "./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate",
                    e,
                )
            }
        }

        /**
         * Load the XTC language from a native library at the given path.
         *
         * Uses Java's Foreign Function & Memory API to load the library and
         * look up the tree_sitter_xtc language function symbol.
         */
        private fun loadLanguageFromPath(path: Path): Language {
            logger.debug("Loading language from path: {}", path)

            // Create a symbol lookup for the library
            val symbols = SymbolLookup.libraryLookup(path, arena)

            // Load the language using jtreesitter's Language.load() API
            return Language.load(symbols, LANGUAGE_FUNCTION)
        }

        /**
         * Load the XTC language from the system library path.
         *
         * This assumes the library is installed in a standard system location
         * or in java.library.path.
         */
        private fun loadLanguageFromSystemPath(): Language {
            logger.debug("Loading language from system path")

            // Map to platform-specific library name
            val libraryName = System.mapLibraryName(GRAMMAR_LIBRARY_NAME)

            // Try to load from system library path
            val symbols = SymbolLookup.libraryLookup(libraryName, arena)

            return Language.load(symbols, LANGUAGE_FUNCTION)
        }
    }
}
