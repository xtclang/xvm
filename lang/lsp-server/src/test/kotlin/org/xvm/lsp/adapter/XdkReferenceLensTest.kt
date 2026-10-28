package org.xvm.lsp.adapter

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

class XdkReferenceLensTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `shared lenses count closed consumers exclude declarations and distinguish overloads`() {
        val data = scenario("X279")

        fun text(key: String) = data[key].asString
        val file =
            directory
                .resolve(text("file"))
                .toFile()
                .canonicalFile
                .apply { writeText(text("source")) }
        val consumer =
            directory.resolve(text("consumerFile")).toFile().canonicalFile.apply {
                writeText(text("consumer"))
            }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule(text("module"), uri),
                    XdkSourceModule(text("consumerModule"), consumer.toURI().toString(), setOf(text("module"))),
                ),
            )
            assertThat(adapter.compile(uri, text("source")).diagnostics).isEmpty()

            fun lenses() = adapter.getCodeLenses(uri).filter { it.command?.command == "xtc.showReferences" }
            val initial = lenses()
            assertThat(initial.map { it.range.start.line }).containsExactly(1, 2, 3, 4)
            assertThat(initial.map { it.command!!.title }).containsExactly(
                text("initialTitle"),
                "0 references",
                "0 references",
                "0 references",
            )
            val command = initial.first().command!!
            val arguments = Gson().toJsonTree(command.arguments).asJsonArray
            assertThat(arguments[0].asString).isEqualTo(uri)
            assertThat(arguments[1].asJsonObject["line"].asInt).isEqualTo(1)
            val locations = arguments[2].asJsonArray
            assertThat(locations.map { it.asJsonObject["uri"].asString })
                .containsExactlyInAnyOrder(uri, consumer.toURI().toString())
            assertThat(
                locations.map { location ->
                    location.asJsonObject["range"]
                        .asJsonObject["start"]
                        .asJsonObject["line"]
                        .asInt
                },
            ).containsExactlyInAnyOrder(2, 4)
            assertThat(adapter.getCodeLensesAsync(uri, false).join().map { it.command!!.command })
                .containsExactly("xtc.runModule")
            assertThat(adapter.compile(consumer.toURI().toString(), text("changedConsumer")).diagnostics).isEmpty()
            assertThat(lenses().first().command!!.title).isEqualTo(text("changedTitle"))
            adapter.compile(consumer.toURI().toString(), text("consumer").replace("lib.read(2)", "missing()"))
            assertThat(lenses()).isEmpty()
            assertThat(adapter.getCodeLenses(uri).map { it.command!!.command }).containsExactly("xtc.runModule")
        }
    }

    @Test
    fun `shared documentation action works in the configured project without changing disk`() {
        val data = scenario("X278")
        val source = data["source"].asString
        val file =
            directory
                .resolve(data["file"].asString)
                .toFile()
                .canonicalFile
                .apply { writeText(source) }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("Documentation", uri)))
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val action =
                adapter
                    .getCodeActions(uri, Range(Position(1, 10), Position(1, 10)), emptyList())
                    .single { it.title == data["title"].asString }
            val edit =
                action.edit!!
                    .changes
                    .getValue(uri)
                    .single()
            val changed = source.replace("    <T>", edit.newText + "    <T>")
            assertThat(changed).isEqualTo(data["expected"].asString)
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(file.readText()).isEqualTo(source)
        }
    }

    private fun scenario(id: String) =
        JsonParser
            .parseString(
                Path
                    .of(
                        System.getProperty("xtc.composite.root"),
                        "lang/test-fixtures/compiler-playbook/scenarios.json",
                    ).toFile()
                    .readText(),
            ).asJsonObject["cases"]
            .asJsonObject[id]
            .asJsonObject["values"]
            .asJsonObject
}
