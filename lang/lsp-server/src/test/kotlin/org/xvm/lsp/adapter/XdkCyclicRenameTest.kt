package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkCyclicRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "class Loop(Loop next) delegates Api(next) {} Int use(Loop target) = target.read();",
            "class First(Second next) delegates Api(next) {} class Second(First next) delegates Api(next) {} " +
                "Int use(First target) = target.read();",
            "class Dynamic(Api next) delegates Api(next) {} Int use(Dynamic target) = target.read();",
        ],
    )
    fun `recursive or dynamic delegation renames written contracts without inventing implementations`(body: String) {
        val text = "module App { interface Api { Int read(); } $body }"
        val source = directory.resolve("App.x").toFile().apply { writeText(text) }
        val uri = source.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            listOf(text.indexOf("read"), text.lastIndexOf("read")).forEach { entry ->
                val edit = requireNotNull(adapter.rename(uri, 0, entry, "fetch"))
                val changed =
                    edit.changes
                        .getValue(uri)
                        .sortedByDescending { it.range.start.column }
                        .fold(text) { value, change ->
                            value.replaceRange(change.range.start.column, change.range.end.column, change.newText)
                        }
                assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            }
            assertThat(adapter.findImplementation(uri, 0, text.indexOf("read"))).isEmpty()
            assertThat(source.readText()).isEqualTo(text)
        }
    }
}
