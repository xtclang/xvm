package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import kotlin.time.Duration.Companion.seconds

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
        // A server reply does not mean the IDE has applied its semantic highlighting yet.
        // Replacing an undecorated document would produce a misleading fast result.
        val before =
            awaitUi(
                "large-file highlighting is installed before replacement",
                60.seconds,
                getter = { withContext(OnDispatcher.EDT) { utility(LargeFileProbe::class).snapshot(file) } },
                checker = { value ->
                    JsonParser.parseString(value).asJsonObject["markupModels"].asJsonArray.sumOf {
                        it.asJsonObject["highlighters"].asInt
                    } >= 20_000
                },
            )
        record(before)
        record(
            withContext(OnDispatcher.EDT) {
                utility(LargeFileProbe::class).replace(singleProject(), file, workload(5_000))
            },
        )
        workspace.settle(document)
        workspace.clean(document)
    }
    println("IntelliJ large-file editing receipt: $report")
}

@Remote("org.xtclang.idea.playbook.probe.LargeFileProbe", plugin = "org.xtclang.playbook.probe")
interface LargeFileProbe {
    fun plain(
        markers: Int,
        bulk: Boolean,
    ): String

    fun snapshot(file: VirtualFile): String

    fun replace(
        project: Project,
        file: VirtualFile,
        text: String,
    ): String
}
