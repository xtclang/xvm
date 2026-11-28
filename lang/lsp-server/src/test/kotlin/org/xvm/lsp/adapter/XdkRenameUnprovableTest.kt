package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

/**
 * A rename's proof where the semantic model cannot prove every binding, such as OuterType in Public.OuterType, a formal
 * type's child that no constraint declares, which the compiler knows by its name alone: the rename is proven, unless
 * such a site spells the name it changes. An occurrence that the model leaves unbound is compared the same way.
 */
class XdkRenameUnprovableTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a rename is proven in a module with a binding the model cannot prove`() {
        val uri = directory.source("Pairs", PAIRS)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, PAIRS).diagnostics).isEmpty()
            val at = PAIRS.positionOf("Int total", "total")
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "width")) { "No rename of total" }
            assertThat(apply(PAIRS, edit.changes.getValue(uri))).isEqualTo(PAIRS.replace("total", "width"))
        }
    }

    @Test
    fun `a rename to a name that an unprovable binding spells is refused, as nothing shows it is not captured`() {
        val uri = directory.source("Pairs", PAIRS)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, PAIRS).diagnostics).isEmpty()
            val at = PAIRS.positionOf("Int total", "total")
            assertThat(adapter.rename(uri, at.line, at.column, "OuterType")).isNull()
        }
    }

    private companion object {
        // OuterType in Public.OuterType names no declaration, as Public's constraint is Object
        const val PAIRS =
            "module Pairs { class Allocator<Public> { Int allocate(Public.OuterType? outer) = 0; } " +
                "Int total() = 2; Int use() = total(); }"
    }
}
