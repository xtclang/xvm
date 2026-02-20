package org.xvm.lsp.index

import io.github.treesitter.jtreesitter.Language
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
 * **Thread safety:** This indexer creates its own dedicated [XtcParser] and [XtcQueryEngine]
 * instances, separate from the adapter's instances. This avoids race conditions since
 * tree-sitter's native `Parser` is NOT thread-safe and the adapter's parser is used
 * concurrently on the LSP message thread. All calls to the indexer's parser and query
 * engine are serialized via [parseLock] to protect against concurrent indexer tasks.
 *
 * @param index the workspace index to populate
 * @param language the tree-sitter language, used to create a dedicated parser and query engine
 */
@Suppress("LoggingSimilarMessage")
class WorkspaceIndexer(
    private val index: WorkspaceIndex,
    language: Language,
) : Closeable {
    private val logger = LoggerFactory.getLogger(WorkspaceIndexer::class.java)

    /** Dedicated parser instance for the indexer -- not shared with the adapter. */
    private val parser: XtcParser = XtcParser(language)

    /** Dedicated query engine for the indexer -- not shared with the adapter. */
    private val queryEngine: XtcQueryEngine = XtcQueryEngine(language)

    /** Serializes access to the non-thread-safe tree-sitter parser and query engine. */
    private val parseLock = Any()

    private val threadPoolSize = minOf(Runtime.getRuntime().availableProcessors(), 4).coerceAtLeast(2)

    private val threadPool: ExecutorService =
        Executors.newFixedThreadPool(threadPoolSize).also {
            logger.info(
                "created thread pool with {} threads (available processors: {})",
                threadPoolSize,
                Runtime.getRuntime().availableProcessors(),
            )
        }

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
            logger.info("starting workspace scan: {} folders: {}", folders.size, folders)
            val (_, elapsed) =
                measureTimedValue {
                    val files = collectXtcFiles(folders)
                    logger.info("found {} .x files to index", files.size)

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
                                    logger.info("progress: {}/{} files ({}%)", done, total, percent)
                                }
                            }, threadPool)
                        }

                    // Wait for all to complete
                    CompletableFuture.allOf(*futures.toTypedArray()).join()
                    progressReporter?.invoke("Indexing complete", 100)
                }

            logger.info(
                "workspace scan complete: {} symbols in {} files ({})",
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
        logger.info("reindexed {}: {} symbols", uri.substringAfterLast('/'), symbols.size)
    }

    /**
     * Remove all symbols for a file (e.g., when it's deleted).
     */
    fun removeFile(uri: String) {
        index.removeSymbolsForUri(uri)
        logger.info("removed symbols for {}", uri.substringAfterLast('/'))
    }

    private fun indexFile(path: Path) {
        val uri = path.toUri().toString()
        try {
            val content = path.readText()
            val symbols = parseAndExtractSymbols(uri, content)
            index.addSymbols(uri, symbols)
            logger.info("indexed {}: {} symbols ({} bytes)", path.fileName, symbols.size, content.length)
        } catch (e: Exception) {
            logger.warn("failed to index {}: {}", path, e.message)
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
                    logger.warn("not a directory: {}", folder)
                    continue
                }
                val countBefore = size
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
                            logger.warn("cannot visit {}: {}", file, exc.message)
                            return FileVisitResult.CONTINUE
                        }
                    },
                )
                logger.info("scanned folder {}: {} .x files", folder, size - countBefore)
            }
        }

    override fun close() {
        logger.info("shutting down (index has {} symbols in {} files)", index.symbolCount, index.fileCount)
        threadPool.shutdown()
        if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
            logger.warn("thread pool did not terminate in 5s, forcing shutdown")
            threadPool.shutdownNow()
        }
        queryEngine.close()
        parser.close()
        logger.info("closed")
    }
}
