package org.xvm.lsp.adapter

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared base class for all tree-sitter adapter tests.
 *
 * Provides adapter lifecycle management (setup, teardown, native-library
 * availability check) and common helper methods so that each focused test
 * class only contains the tests themselves.
 *
 * All tests are skipped (not failed) when the tree-sitter native library
 * is unavailable, making this safe to run in any environment.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TreeSitterTestBase {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)
    protected var adapter: TreeSitterAdapter? = null
    private val uriCounter = AtomicInteger(0)

    /** Shorthand accessor -- safe because [assumeAvailable] guards every test. */
    protected val ts: TreeSitterAdapter get() = adapter!!

    /**
     * Returns a unique URI per call so each test gets a fresh parse tree.
     * Re-using the same URI across tests would trigger incremental parsing against
     * a stale tree whose byte offsets don't match the new source, causing
     * [StringIndexOutOfBoundsException] inside the native parser.
     */
    protected fun freshUri(): String = "file:///t1st${uriCounter.incrementAndGet()}.x"

    /** Log and return a value -- use to trace adapter responses during test runs. */
    protected fun <T> logged(
        test: String,
        value: T,
    ): T {
        logger.info("[TEST] {} -> {}", test, value)
        return value
    }

    @BeforeAll
    fun setUpAdapter() {
        adapter = runCatching { TreeSitterAdapter() }.getOrNull()
    }

    /** Skip (not fail) every test when the native library isn't loadable. */
    @BeforeEach
    fun assumeAvailable() {
        Assumptions.assumeTrue(adapter != null, "Tree-sitter native library not available")
    }

    @AfterAll
    fun tearDown() {
        adapter?.close()
    }
}
