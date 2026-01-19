package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Parser
import io.github.treesitter.jtreesitter.Tree
import org.slf4j.LoggerFactory
import java.io.Closeable
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
    fun parse(source: String, oldTree: XtcTree?): XtcTree {
        check(!closed) { "Parser has been closed" }

        val tree = if (oldTree != null) {
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

        /**
         * Load the XTC tree-sitter language from native library.
         *
         * The library is expected to be in the native resources directory:
         * - darwin-aarch64/libtree-sitter-xtc.dylib
         * - darwin-x86_64/libtree-sitter-xtc.dylib
         * - linux-x86_64/libtree-sitter-xtc.so
         */
        private fun loadXtcLanguage(): Language {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val (platform, extension) = when {
                osName.contains("mac") || osName.contains("darwin") -> {
                    val arch = if (osArch.contains("aarch64") || osArch.contains("arm64")) "darwin-aarch64" else "darwin-x86_64"
                    arch to ".dylib"
                }
                osName.contains("linux") -> {
                    val arch = if (osArch.contains("aarch64") || osArch.contains("arm64")) "linux-aarch64" else "linux-x86_64"
                    arch to ".so"
                }
                osName.contains("windows") -> "windows-x86_64" to ".dll"
                else -> throw IllegalStateException("Unsupported platform: $osName/$osArch")
            }

            val libraryFileName = "lib$GRAMMAR_LIBRARY_NAME$extension"
            val resourcePath = "/native/$platform/$libraryFileName"

            logger.debug("Loading XTC grammar from: {}", resourcePath)

            // Try to load from resources (bundled in JAR)
            XtcParser::class.java.getResourceAsStream(resourcePath)?.use { inputStream ->
                val tempFile = Files.createTempFile(GRAMMAR_LIBRARY_NAME, extension)
                tempFile.toFile().deleteOnExit()
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)

                return loadLanguageFromPath(tempFile)
            }

            // Fallback: try to load from system library path
            logger.debug("Trying to load XTC grammar from system library path")
            try {
                return loadLanguageFromSystemPath()
            } catch (e: Exception) {
                logger.error(
                    "Failed to load XTC tree-sitter grammar. " +
                    "The native library is not available. " +
                    "Run './gradlew :lang:dsl:buildTreeSitterLibrary' to compile it."
                )
                throw IllegalStateException(
                    "XTC tree-sitter grammar not available. Build it with: " +
                    "./gradlew :lang:dsl:buildTreeSitterLibrary",
                    e
                )
            }
        }

        private fun loadLanguageFromPath(path: Path): Language {
            // Use System.load() to load the native library, then get the language
            System.load(path.toAbsolutePath().toString())
            // The language function should be available as tree_sitter_xtc
            // For now, throw an error indicating manual setup is needed
            throw UnsupportedOperationException(
                "Tree-sitter grammar loading from path not yet implemented. " +
                "This requires native library setup. See PLAN_TREE_SITTER.md for details."
            )
        }

        private fun loadLanguageFromSystemPath(): Language {
            throw UnsupportedOperationException(
                "Tree-sitter grammar loading from system path not yet implemented. " +
                "This requires native library setup. See PLAN_TREE_SITTER.md for details."
            )
        }
    }
}
