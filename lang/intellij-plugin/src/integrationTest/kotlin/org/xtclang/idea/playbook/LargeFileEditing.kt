package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

// TODO LSP4IJ: UP17 — the underlying IntelliJ interval tree scales poorly when many ranges are
// invalidated together. Keep this diagnostic and freeze gate until the platform repair is verified.

/** Opt-in freeze investigation; receipts survive even if the IDE's freeze detector fails the run. */
fun Driver.largeFileEditing(
    fixtures: Map<String, String>,
    shared: SharedScenarios,
) {
    val report = Path.of(singleProject().getBasePath()).parent.resolve("large-file-editing.jsonl")

    fun record(value: String) {
        Files.writeString(report, value + "\n", CREATE, APPEND)
        println("IntelliJ large-file probe: $value")
    }

    listOf(0, 20_000, 40_000, 80_000).forEach { markers ->
        record(Gson().toJson(mapOf("phase" to "plain-document-start", "markers" to markers)))
        record(withContext(OnDispatcher.EDT) { utility(LargeFileProbe::class).plain(markers, false) })
    }
    record(Gson().toJson(mapOf("phase" to "plain-document-start", "markers" to 80_000, "bulk" to true)))
    record(withContext(OnDispatcher.EDT) { utility(LargeFileProbe::class).plain(80_000, true) })
    ParityWorkspace(this, "LARGE_FILE", fixtures, shared).use { workspace ->
        fun workload(methods: Int) =
            "module LargeFile {\n    static Int value = 1;\n" +
                (0 until methods).joinToString("\n") { "    Int read$it() { return value; }" } +
                "\n}\n"

        workspace.write("LargeFile.x", workload(20_000))
        workspace.configure(
            listOf(SharedScenarios.SourceModule("LargeFile", workspace.uri("LargeFile.x"), emptyList())),
        )
        val document = workspace.open("LargeFile.x")
        workspace.clean(document)
        check(!workspace.query("textDocument/hover", document, document.at("value")).isJsonNull)
        val file = document.editor.editor.getVirtualFile()
        focusEditor(document.editor)
        // A server reply does not mean the IDE has applied its semantic highlighting yet, and the
        // daemon installs it a part at a time: a replacement of a partly decorated document measures
        // less than the decorated case. The decoration is complete once LSP4IJ holds the semantic
        // tokens of the current text, IntelliJ has finished highlighting the file, and a highlighter
        // with semantic attributes is installed for each token.
        val decoration =
            awaitUi(
                "large-file semantic highlighting is fully installed before replacement",
                HANG_LIMIT,
                getter = {
                    JsonParser
                        .parseString(
                            withContext(OnDispatcher.EDT) { utility(LargeFileProbe::class).decoration(singleProject(), file) },
                        ).asJsonObject
                },
                checker = { it.complete() && it["highlightingDone"].asBoolean },
            )
        check(decoration["semanticTokens"].asInt > 0) { "The server gave the large file no semantic tokens: $decoration" }
        record(withContext(OnDispatcher.EDT) { utility(LargeFileProbe::class).snapshot(singleProject(), file) })
        val replacement =
            withContext(OnDispatcher.EDT) {
                utility(LargeFileProbe::class).replace(singleProject(), file, workload(5_000))
            }
        record(replacement)
        // The record names the highlighters installed when the replacement started, against the tokens.
        val started =
            JsonParser
                .parseString(replacement)
                .asJsonObject["before"]
                .asJsonObject["decoration"]
                .asJsonObject
        check(started.complete()) { "The replacement started on a partly decorated editor: $started" }
        workspace.settle(document)
        workspace.clean(document)
    }
    println("IntelliJ large-file editing receipt: $report")
}

/** A highlighter with semantic attributes is installed for each semantic token of the file's current text. */
private fun JsonObject.complete(): Boolean = get("semanticTokens")?.asInt?.let { get("semanticHighlighters").asInt >= it } == true

/** Measures native editor work with and without language decorations. */
@Remote("org.xtclang.idea.playbook.probe.LargeFileProbe", plugin = "org.xtclang.playbook.probe")
interface LargeFileProbe {
    fun plain(
        markers: Int,
        bulk: Boolean,
    ): String

    fun decoration(
        project: Project,
        file: VirtualFile,
    ): String

    fun snapshot(
        project: Project,
        file: VirtualFile,
    ): String

    fun replace(
        project: Project,
        file: VirtualFile,
        text: String,
    ): String
}
