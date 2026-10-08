package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import org.eclipse.lsp4j.InlineCompletionTriggerKind
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Native acceptance for an explicitly selected upstream build; shipping expectations stay intact. */
internal fun ParityWorkspace.nativeLibraryContent(
    source: ParityWorkspace.Document,
    at: Int,
    target: JsonObject,
    imported: JsonObject,
    monikerScheme: String,
) = with(driver) {
    val uri = target.string("uri")
    val start = target["range"].asJsonObject["start"].asJsonObject
    val manager = service<FileEditorManager>(singleProject())

    fun navigate(): NativeLibraryFile {
        val editor = source.editor
        focusEditor(editor)
        withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
        invokeAction("GotoDeclaration", component = editor.component)
        awaitUi("native definition opens virtual library $uri", 30.seconds) {
            withContext(OnDispatcher.EDT) { manager.getSelectedTextEditor()?.getVirtualFile()?.getPath() == uri }
        }
        return withContext(OnDispatcher.EDT) {
            val selected = requireNotNull(manager.getSelectedTextEditor())
            val text = selected.getDocument().getText()
            check(
                ParityWorkspace.position(text, selected.getCaretModel().getOffset()) ==
                    mapOf("line" to start.int("line"), "character" to start.int("character")),
            )
            cast(selected.getVirtualFile(), NativeLibraryFile::class)
        }
    }

    val file = navigate()
    check(!file.isWritable() && file.isValid())
    val text = withContext(OnDispatcher.EDT) { requireNotNull(manager.getSelectedTextEditor()).getDocument().getText() }
    val content = protocol.query("workspace/textDocumentContent", mapOf("uri" to uri)).asJsonObject.string("text")
    check(text == content)
    val exported =
        protocol
            .query(
                "textDocument/moniker",
                mapOf("textDocument" to mapOf("uri" to uri), "position" to start),
            ).rows()
            .single()
    check(exported == imported.deepCopy().apply { addProperty("kind", "export") })
    check(exported.string("scheme") == monikerScheme)
    check(
        protocol
            .query(
                "textDocument/formatting",
                mapOf("textDocument" to mapOf("uri" to uri), "options" to mapOf("tabSize" to 4, "insertSpaces" to true)),
            ).rows()
            .isEmpty(),
    )

    val stamp = file.getModificationStamp()
    // Replacing compiler inputs sends a real server-to-client content refresh for fetched views.
    // Each library type needs an actual settings change, including later iterations of X254.
    configure(null)
    configure(listOf(SharedScenarios.SourceModule("LibraryViews", source.uri, emptyList())))
    awaitUi("server refresh reloads the existing virtual library", 30.seconds) { file.getModificationStamp() != stamp }
    check(file.isValid() && !file.isWritable())
    check(withContext(OnDispatcher.EDT) { requireNotNull(manager.getSelectedTextEditor()).getDocument().getText() } == text)

    source.editor
    val previous = protocol.server().getCurrentProcessId()
    protocol.server().restart()
    awaitUi("restart retires the old virtual library", 45.seconds) { !file.isValid() }
    awaitUi("replacement server owns library navigation", 45.seconds) {
        protocol.server().getCurrentProcessId()?.let { it != previous } == true
    }
    // Retained diagnostic paint is not readiness for the replacement process. Match the
    // initial navigation's definition query before checking the new native editor target.
    check(query("textDocument/definition", source, at).rows().single().string("uri") == uri)
    clean(source)
    val reopened = navigate()
    check(reopened.isValid() && !reopened.isWritable() && !file.isValid())
    check(withContext(OnDispatcher.EDT) { requireNotNull(manager.getSelectedTextEditor()).getDocument().getText() } == text)
}

internal fun ParityWorkspace.nativeInlineAlternatives(
    document: ParityWorkspace.Document,
    offset: Int,
    source: String,
) = with(driver) {
    // Partial identifiers can have no diagnostics. Await a real report, not an error
    // that the compiler deliberately suppresses while completion is available.
    trace.diagnostics(document.uri) { true }
    val editor = document.editor
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(offset) }

    fun visible() = withContext(OnDispatcher.EDT) { utility(InlineCompletionProbe::class).visibleText(editor.editor) }

    fun requests() =
        trace.requests("textDocument/inlineCompletion").filter { it["textDocument"].asJsonObject.string("uri") == document.uri }
    invokeAction("CallInlineCompletionAction", component = editor.component)
    awaitUi(
        "explicit inline invocation displays an alternative",
        30.seconds,
        errorMessage = {
            "Visible inline text: '${visible()}'; requests: ${requests()}"
        },
    ) { visible() in setOf("other", "swer") }
    check(requests().any { it["context"].asJsonObject.int("triggerKind") == InlineCompletionTriggerKind.Invoked.value })
    val first = visible()
    invokeAction("NextInlineCompletionSuggestionAction", component = editor.component)
    awaitUi(
        "native inline cycling displays the other alternative",
        15.seconds,
    ) { visible() in setOf("other", "swer") && visible() != first }
    if (visible() != "swer") {
        invokeAction("NextInlineCompletionSuggestionAction", component = editor.component)
        awaitUi("answer alternative is selected", 15.seconds) { visible() == "swer" }
    }
    invokeAction("InsertInlineCompletionAction", component = editor.component)
    val expected = source.replaceRange(offset - 2, offset, "answer")
    awaitUi("native alternative acceptance preserves the suffix", 15.seconds) { document.text == expected }
    clean(document)
    invokeAction("\$Undo", component = editor.component)
    awaitUi("inline Undo restores the ambiguous prefix", 15.seconds) { document.text == source }
    settle(document)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(offset) }

    invokeAction("CodeCompletion", component = editor.component)
    val lookupManager = utility(EditorLookupManager::class).getInstance(singleProject())
    awaitUi("native popup offers both values", 30.seconds, errorMessage = {
        trace.capture(Path.of(System.getProperty("xtc.playbook.reports"), "inline-popup-trace.txt"))
        "Native popup items: ${lookupManager.getActiveLookup()?.getItems()?.map { it.getLookupString() }}"
    }) {
        lookupManager
            .getActiveLookup()
            ?.getItems()
            ?.map { it.getLookupString() }
            ?.containsAll(listOf("answer", "another")) == true
    }
    val lookup = requireNotNull(lookupManager.getActiveLookup())
    // A programmatic selection does not exercise the popup's keyboard selection event.
    // Drive the same bounded list navigation as a user so inline completion sees it.
    repeat(lookup.getItems().size) {
        if (lookup.getCurrentItem()?.getLookupString() != "answer") {
            invokeAction("EditorDown", component = editor.component)
        }
    }
    check(lookup.getCurrentItem()?.getLookupString() == "answer")
    awaitUi(
        "native inline request includes the selected popup replacement",
        30.seconds,
        errorMessage = { "Missing selected popup context; requests: ${requests()}" },
    ) {
        requests().any {
            it["context"]
                .asJsonObject["selectedCompletionInfo"]
                ?.takeIf { value ->
                    value.isJsonObject
                }?.asJsonObject
                ?.let { selected ->
                    selected.string("text") == "answer" &&
                        listOf("start" to offset - 2, "end" to offset).all { (boundary, index) ->
                            val actual = selected["range"].asJsonObject[boundary].asJsonObject
                            val expectedPosition = ParityWorkspace.position(source, index)
                            actual.int("line") == expectedPosition.getValue("line") &&
                                actual.int("character") == expectedPosition.getValue("character")
                        }
                } == true
        }
    }
    check(visible().isEmpty()) { "A complete popup item must not gain an incompatible inline suffix" }
    dismissPopups()
    check(document.text == source)
}

@Remote("com.intellij.openapi.vfs.VirtualFile")
internal interface NativeLibraryFile {
    fun isValid(): Boolean

    fun isWritable(): Boolean

    fun getModificationStamp(): Long
}
