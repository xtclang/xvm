package org.xvm.lsp.adapter

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

/** Exact same edits and refusals as both editor drivers, before any native UI run. */
class XdkL63SharedActionsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest(name = "X{0} shared refactoring")
    @ValueSource(ints = [221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242])
    fun `shared action produces exact source or a documented refusal`(number: Int) {
        CompilerTestSupport.configure()
        val scenarios = Path.of(System.getProperty("xtc.composite.root"), "lang/test-fixtures/compiler-playbook/scenarios.json")
        val data =
            JsonParser
                .parseString(scenarios.toFile().readText())
                .asJsonObject
                .getAsJsonObject("cases")
                .getAsJsonObject("X$number")
                .getAsJsonObject("values")

        fun write(
            name: String,
            text: String,
        ) = directory.resolve(name).toFile().canonicalFile.apply {
            parentFile.mkdirs()
            writeText(text)
        }
        val text = data["source"].asString
        val file = write(data["file"].asString, text)
        data.getAsJsonArray("files")?.forEach { row ->
            val extra = row.asJsonObject
            write(extra["file"].asString, extra["source"].asString)
        }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            data.getAsJsonArray("sourceModules")?.let { modules ->
                adapter.replaceSourceModules(
                    modules.map { row ->
                        val module = row.asJsonObject
                        XdkSourceModule(
                            module["name"].asString,
                            directory
                                .resolve(module["uri"].asString)
                                .toFile()
                                .toURI()
                                .toString(),
                            module
                                .getAsJsonArray("dependencies")
                                ?.map { it.asString }
                                ?.toSet()
                                .orEmpty(),
                        )
                    },
                )
            }
            val diagnostics = adapter.compile(uri, text).diagnostics
            if (data["initiallyValid"]?.asBoolean == false) {
                assertThat(diagnostics).isNotEmpty()
            } else {
                assertThat(diagnostics).isEmpty()
            }
            val start = data["selectionOffset"]?.asInt ?: text.indexOf(data["selected"].asString)
            val end = start + data["selected"].asString.length
            val range = Range(XdkRename.position(text, start), XdkRename.position(text, end))
            val actions = adapter.getCodeActions(uri, range, diagnostics).filter { it.title == data["title"].asString }
            if (data["refused"]?.asBoolean == true) {
                assertThat(actions).isEmpty()
            } else {
                assertThat(actions).hasSize(1)
                val edit = requireNotNull(actions.single().edit)
                assertThat(edit.versioned).isTrue()

                fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                val changed =
                    edit.changes.getValue(uri).sortedByDescending { offset(it.range.start) }.fold(text) { current, change ->
                        current.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                    }
                assertThat(changed).isEqualTo(data["expected"].asString)
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
    }
}
