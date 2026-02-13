package org.xvm.lsp.index

import org.slf4j.LoggerFactory
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.treesitter.XtcParser
import org.xvm.lsp.treesitter.XtcQueryEngine
import java.io.Closeable
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.time.measureTimedValue

/**
 * Background workspace scanner that builds and maintains the [WorkspaceIndex].
 *
 * Uses a dedicated thread pool (not ForkJoinPool) to avoid starving LSP message handlers.
 * Converts tree-sitter parsed [SymbolInfo] to flat [IndexedSymbol] entries for indexing.
 *
 * **Thread safety:** Tree-sitter's native `Parser` is NOT thread-safe, so all calls to
 * [parser] and [queryEngine] are serialized via [parseLock]. File I/O (reading file content)
 * can still happen concurrently on the thread pool; only the parse+query step is serialized.
 *
 * @param index the workspace index to populate
 * @param parser the tree-sitter parser (shared with the adapter)
 * @param queryEngine the query engine (shared with the adapter)
 */
@Suppress("LoggingSimilarMessage")
class WorkspaceIndexer(
    private val index: WorkspaceIndex,
    private val parser: XtcParser,
    private val queryEngine: XtcQueryEngine,
) : Closeable {
    private val logger = LoggerFactory.getLogger(WorkspaceIndexer::class.java)

    /** Serializes access to the non-thread-safe tree-sitter parser and query engine. */
    private val parseLock = Any()

    private val threadPool: ExecutorService =
        Executors.newFixedThreadPool(
            minOf(Runtime.getRuntime().availableProcessors(), 4),
        )

    /**
     * Scan all `*.x` files in the given workspace folders in parallel.
     *
     * @param folders list of workspace folder paths (file system paths, not URIs)
     * @param progressReporter optional callback for progress reporting: (message, percentComplete)
     * @return a future that completes when scanning is done
     */
    fun scanWorkspace(
        folders: List<String>,
        progressReporter: ((String, Int) -> Unit)? = null,
    ): CompletableFuture<Unit> =
        CompletableFuture.supplyAsync({
            val (_, elapsed) =
                measureTimedValue {
                    val files = collectXtcFiles(folders)
                    logger.info("[WorkspaceIndexer] found {} .x files to index", files.size)

                    if (files.isEmpty()) {
                        progressReporter?.invoke("No .x files found", 100)
                        return@supplyAsync
                    }

                    val indexed = AtomicInteger(0)
                    val total = files.size

                    // Process files in parallel batches using the dedicated thread pool
                    val futures =
                        files.map { file ->
                            CompletableFuture.runAsync({
                                indexFile(file)
                                val done = indexed.incrementAndGet()
                                if (done % 50 == 0 || done == total) {
                                    val percent = (done * 100) / total
                                    progressReporter?.invoke("Indexing: $done/$total files", percent)
                                    logger.info("[WorkspaceIndexer] progress: {}/{} files ({}%)", done, total, percent)
                                }
                            }, threadPool)
                        }

                    // Wait for all to complete
                    CompletableFuture.allOf(*futures.toTypedArray()).join()
                    progressReporter?.invoke("Indexing complete", 100)
                }

            logger.info(
                "[WorkspaceIndexer] workspace scan complete: {} symbols in {} files ({})",
                index.symbolCount,
                index.fileCount,
                elapsed,
            )
        }, threadPool)

    /**
     * Re-index a single file. Called after compile() updates a file.
     * Removes old symbols and re-indexes from the provided content.
     */
    fun reindexFile(
        uri: String,
        content: String,
    ) {
        val symbols = parseAndExtractSymbols(uri, content)
        index.addSymbols(uri, symbols)
        logger.debug("[WorkspaceIndexer] reindexed {}: {} symbols", uri.substringAfterLast('/'), symbols.size)
    }

    /**
     * Remove all symbols for a file (e.g., when it's deleted).
     */
    fun removeFile(uri: String) {
        index.removeSymbolsForUri(uri)
        logger.info("[WorkspaceIndexer] removed symbols for {}", uri.substringAfterLast('/'))
    }

    private fun indexFile(path: Path) {
        val uri = path.toUri().toString()
        try {
            val content = path.readText()
            val symbols = parseAndExtractSymbols(uri, content)
            index.addSymbols(uri, symbols)
        } catch (e: Exception) {
            logger.warn("[WorkspaceIndexer] failed to index {}: {}", path, e.message)
        }
    }

    /**
     * Parse a file and extract flat [IndexedSymbol] entries.
     * Flattens the [SymbolInfo] tree so nested declarations are also indexed.
     *
     * Synchronized on [parseLock] because tree-sitter's Parser is not thread-safe.
     */
    private fun parseAndExtractSymbols(
        uri: String,
        content: String,
    ): List<IndexedSymbol> =
        synchronized(parseLock) {
            val tree = parser.parse(content)
            try {
                val symbols = queryEngine.findAllDeclarations(tree, uri)
                flattenSymbols(symbols, uri, null)
            } finally {
                tree.close()
            }
        }

    private fun flattenSymbols(
        symbols: List<SymbolInfo>,
        uri: String,
        containerName: String?,
    ): List<IndexedSymbol> =
        buildList {
            for (symbol in symbols) {
                add(IndexedSymbol.fromSymbolInfo(symbol, uri, containerName))
                if (symbol.children.isNotEmpty()) {
                    addAll(flattenSymbols(symbol.children, uri, symbol.name))
                }
            }
        }

    private fun collectXtcFiles(folders: List<String>): List<Path> =
        buildList {
            for (folder in folders) {
                val path = Path.of(folder)
                if (!Files.isDirectory(path)) {
                    logger.warn("[WorkspaceIndexer] not a directory: {}", folder)
                    continue
                }
                Files.walkFileTree(
                    path,
                    object : SimpleFileVisitor<Path>() {
                        override fun visitFile(
                            file: Path,
                            attrs: BasicFileAttributes,
                        ): FileVisitResult {
                            if (file.extension == "x") {
                                add(file)
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFileFailed(
                            file: Path,
                            exc: java.io.IOException,
                        ): FileVisitResult {
                            logger.warn("[WorkspaceIndexer] cannot visit {}: {}", file, exc.message)
                            return FileVisitResult.CONTINUE
                        }
                    },
                )
            }
        }

    override fun close() {
        threadPool.shutdown()
        if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
            threadPool.shutdownNow()
        }
        logger.info("[WorkspaceIndexer] closed")
    }
}
