package org.xtclang.idea.playbook.probe

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRenderer
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.DataManager
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AppIcon
import com.intellij.util.indexing.FindSymbolParameters
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.features.workspaceSymbol.LSPWorkspaceGotoSymbolContributor
import com.redhat.devtools.lsp4ij.features.workspaceSymbol.WorkspaceSymbolData
import org.xtclang.idea.lsp.XtcRenameHandler
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities

/** Small IDE-side observations avoid walking the entire Swing tree for every focus poll. */
object EditorUi {
    // Counts switches to another application; IntelliJ shows no editor hint while one is active.
    private val deactivations =
        AtomicInteger().also { count ->
            ApplicationManager.getApplication().messageBus.connect().subscribe(
                ApplicationActivationListener.TOPIC,
                object : ApplicationActivationListener {
                    override fun applicationDeactivated(ideFrame: IdeFrame) {
                        count.incrementAndGet()
                    }
                },
            )
        }

    @JvmStatic
    fun deactivations(): Int = deactivations.get()

    @JvmStatic
    fun renameAvailable(editor: Editor): Boolean =
        XtcRenameHandler()
            .isAvailableOnDataContext(
                DataManager.getInstance().getDataContext(editor.contentComponent),
            )

    @JvmStatic
    fun renameState(editor: Editor): String {
        val project = requireNotNull(editor.project)
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        val available = renameAvailable(editor)
        val servers =
            LanguageServiceAccessor.getInstance(project).startedServers.map { wrapper ->
                "pid=${wrapper.currentProcessId}, rename=${wrapper.serverCapabilitiesSync?.renameProvider}, " +
                    "opened=${wrapper.openedDocuments.map { it.file.path }}, " +
                    "enabled=${file?.let { wrapper.clientFeatures.renameFeature.isEnabled(it) }}, error=${wrapper.serverError}"
            }
        return "available=$available, file=$file, servers=$servers; ${focusState(editor)}"
    }

    @JvmStatic
    fun focusEditor(editor: Editor) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val manager = IdeFocusManager.getInstance(editor.project)
        SwingUtilities.invokeLater {
            if (!editor.isDisposed) {
                // Component focus alone can leave Find Usages as the active tool window;
                // editor actions then use its preview's context instead of this source tab.
                editor.project?.let { ToolWindowManager.getInstance(it).activateEditorComponent() }
                manager.requestFocus(editor.contentComponent, true)
            }
        }
    }

    @JvmStatic
    fun isEditorActive(editor: Editor): Boolean =
        editor.contentComponent.isFocusOwner &&
            editor.project?.let { ToolWindowManager.getInstance(it).activeToolWindowId } == null &&
            DataManager
                .getInstance()
                .getDataContext(editor.contentComponent)
                .getData(CommonDataKeys.EDITOR) === editor

    @JvmStatic
    fun focusState(editor: Editor): String =
        onSwing {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val window = SwingUtilities.getWindowAncestor(editor.contentComponent)
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            "editorDisposed=${editor.isDisposed}, window=${window?.javaClass?.simpleName}, " +
                "windowFocused=${window?.isFocused}, active=${manager.activeWindow?.javaClass?.simpleName}, " +
                "focused=${manager.focusedWindow?.javaClass?.simpleName}, owner=${manager.focusOwner?.javaClass?.name}, " +
                "editorFocused=${editor.contentComponent.isFocusOwner}, " +
                "activeToolWindow=${editor.project?.let { ToolWindowManager.getInstance(it).activeToolWindowId}}"
        }

    /** A disposable, unowned window exercises real focus loss without touching another app. */
    @JvmStatic
    fun interruptFocus(): Window {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return JFrame("Playbook focus interruption").apply {
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            add(JLabel("Testing focus recovery; this window closes automatically."))
            setSize(420, 100)
            isVisible = true
            AppIcon.getInstance().requestFocus(this)
        }
    }

    @JvmStatic
    fun closeCompletion(editor: Editor) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        CompletionServiceImpl.currentCompletionProgressIndicator?.closeAndFinish(true)
        LookupManager.getActiveLookup(editor)?.hideLookup(true)
        // Reopening an unapplied popup after focus loss is a fresh invocation. Retaining the
        // previous phase makes IDEA broaden it to second-invocation word completion instead.
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion)
    }

    @JvmStatic
    fun hasFocus(editor: Editor): Boolean =
        onSwing {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val frame = SwingUtilities.getWindowAncestor(editor.contentComponent) ?: return@onSwing false
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            sequenceOf(manager.activeWindow, manager.focusedWindow).filterNotNull().any { active ->
                generateSequence(active) { it.owner }.any { it === frame }
            }
        }

    @JvmStatic
    fun popupFocus(
        editor: Editor,
        restoring: Boolean,
    ) = onSwing {
        editor.project?.let { PlaybookProgress.focus(it, restoring) }
        if (restoring) {
            val frame = SwingUtilities.getWindowAncestor(editor.contentComponent)
            AppIcon.getInstance().requestFocus(frame)
            AppIcon.getInstance().requestFocus()
        }
    }

    /** UI observation/focus only: never run document or PSI changes through this path. */
    private fun <T> onSwing(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        val task = FutureTask(action)
        SwingUtilities.invokeLater(task)
        return try {
            task.get(HANG_SECONDS, SECONDS)
        } finally {
            task.cancel(false)
        }
    }

    /**
     * The tooltips of the editor's declarative inlays, as IntelliJ shows them when the mouse is over one: each inlay's
     * `offset` and its `tooltip`, an HTML text, or none.
     */
    @JvmStatic
    fun inlayTooltips(editor: Editor): String {
        val tooltips = JsonArray()
        ApplicationManager.getApplication().invokeAndWait {
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength).forEach { inlay ->
                val renderer = inlay.renderer as? DeclarativeInlayRenderer ?: return@forEach
                tooltips.add(
                    JsonObject().apply {
                        addProperty("offset", inlay.offset)
                        addProperty("tooltip", renderer.presentationList.model.tooltip)
                    },
                )
            }
        }
        return tooltips.toString()
    }

    /**
     * Opens the workspace symbol [name] as Go to Symbol does once it is chosen: the item that LSP4IJ's contributor gives
     * for it navigates, and resolves the symbol's range first when its location has none (#1788). Answers whether the
     * item had a `range` before, and the selected editor's `path` and caret `offset` after it. With [nonProject], the
     * scope is Search Everywhere's All Places, whose items include files outside the project, as the libraries' sources
     * are. The contributor asks the server for the pattern that the popup keeps in the project, as its search does, and
     * answers every later lookup of a name from that answer, so the pattern is set first and restored after.
     */
    @JvmStatic
    fun goToSymbol(
        project: Project,
        name: String,
        nonProject: Boolean,
    ): String {
        val contributor =
            ChooseByNameContributor.SYMBOL_EP_NAME.extensionList
                .filterIsInstance<LSPWorkspaceGotoSymbolContributor>()
                .single()
        val items = mutableListOf<NavigationItem>()
        val pattern = project.getUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN)
        project.putUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN, name)
        try {
            val scope = if (nonProject) GlobalSearchScope.everythingScope(project) else GlobalSearchScope.projectScope(project)
            ProgressManager.getInstance().runProcess(
                {
                    contributor.processNames({ true }, scope, null)
                    contributor.processElementsWithName(name, { items.add(it) }, FindSymbolParameters.wrap(name, scope))
                },
                EmptyProgressIndicator(),
            )
        } finally {
            project.putUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN, pattern)
        }
        val item = checkNotNull(items.filterIsInstance<WorkspaceSymbolData>().firstOrNull { it.name == name }) { "No symbol $name: $items" }
        val state = JsonObject().apply { addProperty("range", item.textRange != null) }
        ApplicationManager.getApplication().invokeAndWait {
            item.navigate(true)
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            state.addProperty("path", editor?.virtualFile?.path)
            state.addProperty("offset", editor?.caretModel?.offset)
        }
        return state.toString()
    }

    /**
     * Reformats the given ranges of the editor's file, `[start, end]` offsets, as Reformat Code does for the changed lines
     * of several places: one formatting request for all of them.
     */
    @JvmStatic
    fun reformatRanges(
        editor: Editor,
        ranges: String,
    ) {
        val project = requireNotNull(editor.project)
        val textRanges = JsonParser.parseString(ranges).asJsonArray.map { TextRange(it.asJsonArray[0].asInt, it.asJsonArray[1].asInt) }
        ApplicationManager.getApplication().invokeAndWait {
            val file = requireNotNull(PsiDocumentManager.getInstance(project).getPsiFile(editor.document))
            WriteCommandAction.runWriteCommandAction(project, "Reformat Ranges", null, {
                CodeStyleManager.getInstance(project).reformatText(file, textRanges)
            })
        }
    }

    @JvmStatic
    fun scrollToCaret(editor: Editor) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val model = editor.scrollingModel
        model.disableAnimation()
        try {
            model.scrollToCaret(ScrollType.CENTER)
        } finally {
            model.enableAnimation()
        }
    }
}
