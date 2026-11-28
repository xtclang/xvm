package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import org.eclipse.lsp4j.InlineCompletionTriggerKind
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Path

/**
 * A bundled library source that Go to Declaration opens: the server's virtual document, which LSP4IJ opens read-only
 * from `workspace/textDocumentContent`, keeps while the compiler inputs change, and retires with the server.
 */
internal fun ParityWorkspace.nativeLibraryContent(
    source: ParityWorkspace.Document,
    at: Int,
    target: JsonObject,
) = with(driver) {
    val uri = target.string("uri")
    val start = target["range"].asJsonObject["start"].asJsonObject
    val manager = service<FileEditorManager>(singleProject())

    fun navigate(): NativeLibraryFile {
        val editor = source.editor
        focusEditor(editor)
        withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
        invokeAction("GotoDeclaration", component = editor.component)
        awaitUi("native definition opens virtual library $uri", HANG_LIMIT) {
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
    check(
        protocol
            .query(
                "textDocument/formatting",
                mapOf("textDocument" to mapOf("uri" to uri), "options" to mapOf("tabSize" to 4, "insertSpaces" to true)),
            ).rows()
            .isEmpty(),
    )

    // A library document's URI carries its library's and its source's revisions, so its text can't change: replacing
    // the compiler inputs leaves the open view valid, read-only and as it was. Each library type needs an actual
    // settings change, including later iterations of X254.
    configure(null)
    configure(listOf(SharedScenarios.SourceModule("LibraryViews", source.uri, emptyList())))
    // the server answers after the configuration changes before it; waiting on the source would select its tab
    val served = protocol.query("workspace/textDocumentContent", mapOf("uri" to uri)).asJsonObject.string("text")
    check(served == text) { "The server's text of $uri changed" }
    check(file.isValid()) { "The compiler inputs' change retired the library view $uri" }
    check(!file.isWritable()) { "The library view $uri became writable" }
    val shown = withContext(OnDispatcher.EDT) { requireNotNull(manager.getSelectedTextEditor()).getDocument().getText() }
    check(shown == text) { "The library view $uri changed" }

    source.editor
    val previous = protocol.server().getCurrentProcessId()
    protocol.server().restart()
    awaitUi("restart retires the old virtual library", HANG_LIMIT) { !file.isValid() }
    awaitUi("replacement server owns library navigation", HANG_LIMIT) {
        protocol.server().getCurrentProcessId()?.let { it != previous } == true
    }
    // Retained diagnostic paint is not readiness for the replacement process. Match the
    // initial navigation's definition query before checking the new native editor target.
    // LSP4IJ reopens the source in the replacement server with a new version, which can retire an analysis that a
    // query started: the server then answers that the document changed (ContentModified), and the query is asked again
    val definition =
        awaitUiNotNull("replacement server answers the definition of $uri", HANG_LIMIT) {
            try {
                query("textDocument/definition", source, at).rows().single()
            } catch (failure: ClientRequestFailure) {
                if (failure.code != ResponseErrorCode.ContentModified.value) throw failure
                null
            }
        }
    check(definition.string("uri") == uri)
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
        HANG_LIMIT,
        errorMessage = {
            "Visible inline text: '${visible()}'; requests: ${requests()}"
        },
    ) { visible() in setOf("other", "swer") }
    // The trace lags the editor. LSP4IJ moves traced messages from its queue to the console in a batch 500 ms after
    // a message, and the console shows a batch 200 ms after it is printed, so a request just sent can be in neither.
    awaitUi(
        "explicit inline invocation is traced as Invoked",
        HANG_LIMIT,
        errorMessage = { "Missing Invoked request; requests: ${requests()}" },
    ) { requests().any { it["context"].asJsonObject.int("triggerKind") == InlineCompletionTriggerKind.Invoked.value } }
    val first = visible()
    invokeAction("NextInlineCompletionSuggestionAction", component = editor.component)
    awaitUi(
        "native inline cycling displays the other alternative",
        HANG_LIMIT,
    ) { visible() in setOf("other", "swer") && visible() != first }
    if (visible() != "swer") {
        invokeAction("NextInlineCompletionSuggestionAction", component = editor.component)
        awaitUi("answer alternative is selected", HANG_LIMIT) { visible() == "swer" }
    }
    invokeAction("InsertInlineCompletionAction", component = editor.component)
    val expected = source.replaceRange(offset - 2, offset, "answer")
    awaitUi("native alternative acceptance preserves the suffix", HANG_LIMIT) { document.text == expected }
    clean(document)
    invokeAction("\$Undo", component = editor.component)
    awaitUi("inline Undo restores the ambiguous prefix", HANG_LIMIT) { document.text == source }
    settle(document)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(offset) }

    invokeAction("CodeCompletion", component = editor.component)
    val lookupManager = utility(EditorLookupManager::class).getInstance(singleProject())
    awaitUi("native popup offers both values", HANG_LIMIT, errorMessage = {
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
        HANG_LIMIT,
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

/**
 * Ctrl+Click on a document link, as Go to Declaration at the link does: LSP4IJ opens a web target in a browser, which
 * the probe records instead of starting one.
 */
internal fun ParityWorkspace.nativeLinkClick(
    document: ParityWorkspace.Document,
    link: JsonObject,
    url: String,
) = with(driver) {
    check(link.has("data") && !link.has("target")) { "The server leaves the link's target to documentLink/resolve: $link" }
    val browser = utility(BrowserRequestsProbe::class)
    val editor = document.editor
    val at = ParityWorkspace.offset(document.text, link["range"].asJsonObject["start"].asJsonObject) + 1
    val resolves = trace.requests("documentLink/resolve").size
    browser.record()
    try {
        focusEditor(editor)
        withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
        invokeAction("GotoDeclaration", component = editor.component)
        awaitUi("Ctrl+Click opens the link's resolved URL", HANG_LIMIT, errorMessage = { "Opened: ${browser.opened()}" }) {
            url in browser.opened().lines()
        }
        // the console's trace can show the request a little after its answer
        awaitUi("Ctrl+Click resolves the link", HANG_LIMIT, errorMessage = { "Ctrl+Click opened $url without documentLink/resolve" }) {
            trace.requests("documentLink/resolve").size > resolves
        }
    } finally {
        browser.restore()
    }
}

/** Records, in the IDE, the URLs it is asked to open in a browser, instead of starting one. */
@Remote("org.xtclang.idea.playbook.probe.BrowserRequests", plugin = "org.xtclang.playbook.probe")
internal interface BrowserRequestsProbe {
    fun record()

    fun opened(): String

    fun restore()
}

/** Checks that virtual library documents remain valid and read-only in the IDE. */
@Remote("com.intellij.openapi.vfs.VirtualFile")
internal interface NativeLibraryFile {
    fun isValid(): Boolean

    fun isWritable(): Boolean

    fun getModificationStamp(): Long
}
