package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal fun Driver.semanticSupport(editor: JEditorUiComponent): NativeSemanticSupport =
    withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
        val file =
            requireNotNull(
                service<PsiManager>(singleProject()).findFile(editor.editor.getVirtualFile()),
            )
        cast(utility(LspFileSupport::class).getSupport(file), NativeSemanticSupport::class)
    }

/**
 * Follow every native chooser entry and verify its file/caret, even if navigation retires its
 * cache.
 */
internal fun Driver.nativeLocations(
    document: ParityWorkspace.Document,
    at: Int,
    kind: String,
    expected: List<JsonObject>,
) {
    val action =
        when (kind) {
            "definition" -> "GotoDeclaration"
            "typeDefinition" -> "LSP.GotoTypeDefinition"
            "implementation" -> "LSP.GotoImplementation"
            else -> error("Unsupported native location feature $kind")
        }

    fun invoke() {
        dismissPopups()
        val editor = document.editor
        focusEditor(editor)
        withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
        editor.scrollToCaretNow()
        invokeAction(action, component = editor.component)
    }

    // TODO LSP4IJ: UP80 — a Go to request that ends unanswered, as one cancelled by the closing of its file, shows "No …
    //  found", and once its editor is gone in a modal dialog (redhat-developer/lsp4ij#2093), which held every later case.
    //  Its task hands its answer to the EDT before its progress ends, so the check waits for that and leaves no request
    //  behind it. The caret alone does not wait when a target is where the lookup starts, as an implementation that is
    //  the declaration it is asked at.
    val name = Path.of(document.file).fileName.toString()

    fun settle() = awaitUi("native $kind's request in $name ends", HANG_LIMIT) { !goToRuns(name) }
    if (expected.isEmpty()) {
        val support = semanticSupport(document.editor)
        val feature =
            when (kind) {
                "definition" -> support.getDefinitionSupport()
                "typeDefinition" -> support.getTypeDefinitionSupport()
                else -> support.getImplementationSupport()
            }
        invoke()
        awaitUi("native $kind receives no targets", HANG_LIMIT) {
            val future = feature.getValidLSPFuture()
            future != null &&
                future.isDone() &&
                !future.isCompletedExceptionally() &&
                future.get().isEmpty()
        }
        settle()
        dismissPopups()
        return
    }
    val expectedPoints =
        expected.map { target ->
            Triple(
                editorPath(target.string("uri")),
                target.getAsJsonObject("range").getAsJsonObject("start").int("line"),
                target.getAsJsonObject("range").getAsJsonObject("start").int("character"),
            )
        }
    val opened =
        expected.indices.map { index ->
            val inspection = PopupInspection(this, document.editor, ::invoke)
            invoke()
            if (expected.size > 1) {
                // The row count check and the choice are one IDE step, so a chooser that closes in between chooses nothing.
                val choices = utility(NativePopupChoices::class)
                awaitUi("native $kind chooser has ${expected.size} targets", HANG_LIMIT) {
                    inspection.recover()
                    withContext(OnDispatcher.EDT) { choices.chooseTableRow(index, expected.size) }
                }
                awaitUi("native $kind chooser closes", HANG_LIMIT) { withContext(OnDispatcher.EDT) { choices.tableRows() } == null }
            }
            awaitUi(
                message = "$kind opens an exact source target",
                timeout = HANG_LIMIT,
                getter = {
                    withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                        service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let { selected ->
                            val position =
                                ParityWorkspace.position(
                                    selected.getDocument().getText(),
                                    selected.getCaretModel().getOffset(),
                                )
                            Triple(
                                selected.getVirtualFile().getPath(),
                                position.getValue("line"),
                                position.getValue("character"),
                            )
                        }
                    }
                },
                checker = { it in expectedPoints },
            ).also { settle() }
        }
    check(opened.toSet() == expectedPoints.toSet()) {
        "Expected $expectedPoints; native destinations: $opened"
    }
}

/**
 * Exercise Quick Documentation; read the native request's hover instead of issuing another hover.
 */
internal fun Driver.nativeHover(
    document: ParityWorkspace.Document,
    at: Int,
    pattern: Regex,
) {
    dismissPopups()
    focusEditor(document.editor)
    withContext(OnDispatcher.EDT) {
        document.editor.editor
            .getCaretModel()
            .moveToOffset(at)
    }
    val support = semanticSupport(document.editor).getHoverSupport()
    document.editor.scrollToCaretNow()
    invokeAction("QuickJavaDoc", component = document.editor.component)
    val protocol = ClientProtocol(this)
    awaitUi("native documentation contains $pattern", HANG_LIMIT) {
        val future = support.getValidLSPFuture()
        future != null &&
            future.isDone() &&
            !future.isCompletedExceptionally() &&
            future.get().any { pattern.containsMatchIn(protocol.copy(it.hover()).toString()) } &&
            ui.x("//div[@class='DocumentationPopupPane']").present()
    }
    dismissPopups()
}

internal fun Driver.nativeHierarchy(
    document: ParityWorkspace.Document,
    at: Int,
    kind: String,
    names: List<String>,
) {
    focusEditor(document.editor)
    withContext(OnDispatcher.EDT) {
        document.editor.editor
            .getCaretModel()
            .moveToOffset(at)
    }
    document.editor.scrollToCaretNow()
    invokeAction(
        if (kind == "type") "TypeHierarchy" else "CallHierarchy",
        component = document.editor.component,
    )
    // TODO LSP4IJ: UP88 — an Ecstasy file's Call Hierarchy browser is the plugin's XtcCallHierarchyBrowser, a subclass of
    //  LSP4IJ's that computes the callers again when a write action ends LSP4IJ's wait for them (redhat-developer/lsp4ij#2278),
    //  and a component's @class is its own class's simple name. Take LSPCallHierarchyBrowser alone again once a release we
    //  ship has the fix and the workaround goes
    val classNames =
        if (kind == "type") listOf("LSPTypeHierarchyBrowser") else listOf("LSPCallHierarchyBrowser", "XtcCallHierarchyBrowser")
    val browser = ui.x("//div[${classNames.joinToString(" or ") { "@class='$it'" }}]")
    awaitUi("native $kind hierarchy contains $names", HANG_LIMIT) {
        browser.present() &&
            browser.tree().collectExpandedPaths().let { rows ->
                names.all { name -> rows.any { path -> path.path.last().contains(name) } }
            }
    }
}

internal fun targetNames(
    locations: List<JsonObject>,
    openText: (String) -> String?,
): List<String> =
    locations.map { location ->
        val uri = location.string("uri")
        val text = openText(uri) ?: Files.readString(Path.of(URI(uri)))
        val at =
            ParityWorkspace.offset(text, location.getAsJsonObject("range").getAsJsonObject("start"))
        Regex("[\\p{L}_$][\\p{L}\\p{N}_$]*").find(text, at)?.takeIf { it.range.first == at }?.value
            ?: error("Target does not select a declaration: $location")
    }

/** Locates native location, hover, token and inlay support for an editor file. */
@Remote("com.redhat.devtools.lsp4ij.LSPFileSupport", plugin = "com.redhat.devtools.lsp4ij")
interface NativeSemanticSupport {
    fun getDefinitionSupport(): NativeLocationsSupport

    fun getTypeDefinitionSupport(): NativeLocationsSupport

    fun getImplementationSupport(): NativeLocationsSupport

    fun getHoverSupport(): NativeHoverSupport

    fun getSemanticTokensSupport(): NativeTokensSupport

    fun getInlayHintsSupport(): NativeInlaysSupport
}

/** Uses the installed client's location feature for navigation assertions. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeLocationsSupport {
    fun getValidLSPFuture(): NativeLocationsFuture?
}

/** Polls a native navigation request and exposes its completed locations. */
@Remote("java.util.concurrent.CompletableFuture")
interface NativeLocationsFuture : ClientFuture {
    fun get(): List<NativeLocation>
}

/** Reads a native navigation target's URI and source range. */
@Remote("com.redhat.devtools.lsp4ij.usages.LocationData", plugin = "com.redhat.devtools.lsp4ij")
interface NativeLocation {
    fun location(): ClientValue
}

/**
 * Whether an LSP4IJ Go to request in the file [name] runs: the progress of its task, which LSP4IJ titles
 * `Find implementation(s) of 'Name.x' at '42'`, and so on for declarations, type definitions and references
 * (`lsp.goto.*.progress.title`, in the playbook's English). Read without the EDT, which a modal dialog may hold.
 */
private fun Driver.goToRuns(name: String): Boolean =
    utility(NativeProgress::class).getCurrentIndicators().any { indicator ->
        // a progress that is not a task's has no title
        runCatching { indicator.getTitle() }.getOrNull()?.let { it.startsWith("Find ") && "(s) of '$name' at '" in it } == true
    }

/** IntelliJ's running progress, among it that of LSP4IJ's Go to requests. */
@Remote("com.intellij.openapi.progress.impl.CoreProgressManager")
interface NativeProgress {
    fun getCurrentIndicators(): List<NativeTitledIndicator>
}

/** Reads the title of a task from its progress. */
@Remote("com.intellij.openapi.progress.util.TitledIndicator")
interface NativeTitledIndicator {
    fun getTitle(): String?
}

/** Requests hover through the installed client's feature support. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.documentation.LSPHoverSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeHoverSupport {
    fun getValidLSPFuture(): NativeHoverFuture?
}

/** Polls the native hover result without blocking the UI thread. */
@Remote("java.util.concurrent.CompletableFuture")
interface NativeHoverFuture : ClientFuture {
    fun get(): List<NativeHover>
}

/** Exposes the hover contents received by the IDE client. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.documentation.HoverData",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeHover {
    fun hover(): ClientValue
}

/** Requests semantic tokens through the installed client. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokensSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeTokensSupport {
    fun getValidLSPFuture(): NativeTokensFuture?
}

/** Polls the native semantic-token result. */
@Remote("java.util.concurrent.CompletableFuture")
interface NativeTokensFuture : ClientFuture {
    fun get(): NativeTokens?
}

/** Exposes the encoded semantic-token data received by the client. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensData",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeTokens {
    fun getSemanticTokens(): ClientValue
}

/** Requests inlay hints through the installed client. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeInlaysSupport {
    fun getValidLSPFuture(): NativeInlaysFuture?
}

/** Polls the native inlay-hint result. */
@Remote("java.util.concurrent.CompletableFuture")
interface NativeInlaysFuture : ClientFuture {
    fun get(): List<NativeInlayData>
}

/** Reads an inlay hint retained by the client for rendering. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.inlayhint.InlayHintData",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeInlayData {
    fun inlayHint(): ClientValue
}

/** Locates the actual editor inlay model for painted-hint assertions. */
@Remote("com.intellij.openapi.editor.Editor")
interface NativeInlayEditor {
    fun getInlayModel(): NativeInlayModel
}

/** Enumerates rendered inlays in the editor's source range. */
@Remote("com.intellij.openapi.editor.InlayModel")
interface NativeInlayModel {
    fun getInlineElementsInRange(
        start: Int,
        end: Int,
    ): List<NativeInlay>
}

/** Exposes a rendered inlay's offset for comparison with its protocol position. */
@Remote("com.intellij.openapi.editor.Inlay")
interface NativeInlay {
    fun getOffset(): Int

    fun getWidthInPixels(): Int

    fun isValid(): Boolean
}
