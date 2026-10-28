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
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

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
    if (expected.isEmpty()) {
        val support = semanticSupport(document.editor)
        val feature =
            when (kind) {
                "definition" -> support.getDefinitionSupport()
                "typeDefinition" -> support.getTypeDefinitionSupport()
                else -> support.getImplementationSupport()
            }
        invoke()
        awaitUi("native $kind receives no targets", 45.seconds) {
            val future = feature.getValidLSPFuture()
            future != null &&
                future.isDone() &&
                !future.isCompletedExceptionally() &&
                future.get().isEmpty()
        }
        dismissPopups()
        return
    }
    val expectedPoints =
        expected.map { target ->
            Triple(
                Path.of(URI(target.string("uri"))).toString(),
                target.getAsJsonObject("range").getAsJsonObject("start").int("line"),
                target.getAsJsonObject("range").getAsJsonObject("start").int("character"),
            )
        }
    val opened =
        expected.indices.map { index ->
            val inspection = PopupInspection(this, document.editor, ::invoke)
            invoke()
            if (expected.size > 1) {
                val popup = ui.popup()
                val table = popup.accessibleTable()
                awaitUi("native $kind chooser has ${expected.size} targets", 45.seconds) {
                    inspection.recover()
                    table.present() && table.content().size == expected.size
                }
                chooseNavigationRow(table, index)
                awaitUi("native $kind chooser closes", 15.seconds) { popup.notPresent() }
            }
            awaitUi(
                message = "$kind opens an exact source target",
                timeout = 30.seconds,
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
            )
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
    awaitUi("native documentation contains $pattern", 45.seconds) {
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
    val className = if (kind == "type") "LSPTypeHierarchyBrowser" else "LSPCallHierarchyBrowser"
    val browser = ui.x("//div[@class='$className']")
    awaitUi("native $kind hierarchy contains $names", 45.seconds) {
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

@Remote("com.redhat.devtools.lsp4ij.LSPFileSupport", plugin = "com.redhat.devtools.lsp4ij")
interface NativeSemanticSupport {
    fun getDefinitionSupport(): NativeLocationsSupport

    fun getTypeDefinitionSupport(): NativeLocationsSupport

    fun getImplementationSupport(): NativeLocationsSupport

    fun getHoverSupport(): NativeHoverSupport

    fun getSemanticTokensSupport(): NativeTokensSupport

    fun getInlayHintsSupport(): NativeInlaysSupport
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeLocationsSupport {
    fun getValidLSPFuture(): NativeLocationsFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface NativeLocationsFuture : ClientFuture {
    fun get(): List<NativeLocation>
}

@Remote("com.redhat.devtools.lsp4ij.usages.LocationData", plugin = "com.redhat.devtools.lsp4ij")
interface NativeLocation {
    fun location(): ClientValue
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.documentation.LSPHoverSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeHoverSupport {
    fun getValidLSPFuture(): NativeHoverFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface NativeHoverFuture : ClientFuture {
    fun get(): List<NativeHover>
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.documentation.HoverData",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeHover {
    fun hover(): ClientValue
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokensSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeTokensSupport {
    fun getValidLSPFuture(): NativeTokensFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface NativeTokensFuture : ClientFuture {
    fun get(): NativeTokens?
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensData",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeTokens {
    fun getSemanticTokens(): ClientValue
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeInlaysSupport {
    fun getValidLSPFuture(): NativeInlaysFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface NativeInlaysFuture : ClientFuture {
    fun get(): List<NativeInlayData>
}

@Remote(
    "com.redhat.devtools.lsp4ij.features.inlayhint.InlayHintData",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeInlayData {
    fun inlayHint(): ClientValue
}

@Remote("com.intellij.openapi.editor.Editor")
interface NativeInlayEditor {
    fun getInlayModel(): NativeInlayModel
}

@Remote("com.intellij.openapi.editor.InlayModel")
interface NativeInlayModel {
    fun getInlineElementsInRange(
        start: Int,
        end: Int,
    ): List<NativeInlay>
}

@Remote("com.intellij.openapi.editor.Inlay")
interface NativeInlay {
    fun getOffset(): Int

    fun getWidthInPixels(): Int

    fun isValid(): Boolean
}
