package org.xtclang.idea.playbook.probe

import com.google.gson.JsonObject
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.SimpleColoredComponent
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.ServerStatus
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunMode
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Drives the IDE for the tour, the guided recording of the plugin's features: it lays the IDE's window out, opens
 * files at a piece of their text, performs actions as their shortcuts do, types, fills and presses dialogs, sets
 * breakpoints, and captures the screen with what it shows, popups and dialogs included, and where on it the step's
 * subject is, for the recording's highlight. Every method works on the one open project; methods that touch the UI run
 * on the EDT themselves, so the driver calls them from any thread.
 */
object TourUi {
    /** The plugin's language server, CompilerSettings.SERVER_ID, which is internal to the plugin. */
    private const val SERVER_ID = "xtcLanguageServer"

    private val log = logger<TourUi>()

    private fun project(): Project = ProjectManager.getInstance().openProjects.single()

    /**
     * Runs [block] later on the EDT, in [modality]; a failure is a warning in the log, not an error, which would end
     * the driven IDE's test, so one step that fails leaves the tour's others to run.
     */
    private fun later(
        modality: ModalityState = ModalityState.nonModal(),
        block: () -> Unit,
    ) {
        ApplicationManager.getApplication().invokeLater(
            { runCatching(block).onFailure { log.warn("Tour: a step's action failed", it) } },
            modality,
        )
    }

    /** Runs [block] on the EDT when no dialog is modal, where model changes may be made. */
    private fun <T> onEdt(block: () -> T): T = onEdt(ModalityState.nonModal(), block)

    /** Runs [block] on the EDT also while a dialog is modal, to read the UI or press a dialog's button. */
    private fun <T> anyEdt(block: () -> T): T = onEdt(ModalityState.any(), block)

    private fun <T> onEdt(
        modality: ModalityState,
        block: () -> T,
    ): T {
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait({ result = runCatching(block) }, modality)
        return result!!.getOrThrow()
    }

    private fun editor(): Editor = checkNotNull(FileEditorManager.getInstance(project()).selectedTextEditor) { "No editor is selected" }

    private fun frame(): Frame = checkNotNull(WindowManager.getInstance().getFrame(project())) { "No IDE frame" }

    private fun file(path: String): VirtualFile =
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))) { "No file $path" }

    /**
     * Lays the IDE out for the recording: its window at the top left of the screen, [width] by [height], the dark
     * theme named [theme], the editor's font at [fontSize], and the main toolbar without the trial's button.
     */
    @JvmStatic
    fun prepare(
        width: Int,
        height: Int,
        theme: String,
        fontSize: Int,
    ): String =
        onEdt {
            val lafs = LafManager.getInstance()
            val chosen = lafs.installedThemes.firstOrNull { it.name == theme } ?: lafs.installedThemes.first { it.isDark }
            if (lafs.currentUIThemeLookAndFeel?.id != chosen.id) {
                lafs.setCurrentLookAndFeel(chosen, false)
                lafs.updateUI()
            }
            EditorColorsManager.getInstance().globalScheme.editorFontSize = fontSize
            val trial = hideTrial()
            val window = frame()
            window.extendedState = Frame.NORMAL
            window.setBounds(0, 0, width, height)
            window.toFront()
            JsonObject()
                .apply {
                    addProperty("theme", chosen.name)
                    addProperty("trialHidden", trial)
                }.toString()
        }

    /** Takes the unlicensed IDE's Start Free Trial button, the TrialStateWidget action, off the main toolbar. */
    private fun hideTrial(): Boolean {
        val actions = ActionManager.getInstance()
        val trial = actions.getAction("TrialStateWidget") ?: return false
        val groups =
            listOfNotNull(
                actions.getAction("MainToolbarRight") as? DefaultActionGroup,
                CustomActionsSchema.getInstance().getCorrectedAction("MainToolbarRight") as? DefaultActionGroup,
            ).distinct()
        groups.forEach { it.remove(trial) }
        ActivityTracker.getInstance().inc()
        return groups.isNotEmpty()
    }

    /** The titles of the background tasks that run, such as indexing or a Gradle import; none once the IDE is idle. */
    @JvmStatic
    fun busy(): String =
        onEdt {
            val bar = WindowManager.getInstance().getStatusBar(project()) as? StatusBarEx
            val tasks = bar?.backgroundProcessModels.orEmpty().map { it.first.title }
            (if (DumbService.isDumb(project())) tasks + "indexing" else tasks).joinToString("; ")
        }

    /** True once the project is open in its window. */
    @JvmStatic
    fun projectOpen(): Boolean =
        ProjectManager
            .getInstance()
            .openProjects
            .singleOrNull()
            ?.let { WindowManager.getInstance().getFrame(it)?.isShowing } == true

    /** True once the Ecstasy language server has started. */
    @JvmStatic
    fun ready(): Boolean =
        LanguageServiceAccessor
            .getInstance(project())
            .startedServers
            .any { it.serverDefinition.id == SERVER_ID && it.serverStatus == ServerStatus.started }

    /** Opens the file at [path] in an editor, with the caret at the start of its [line] (zero-based), and focuses it. */
    @JvmStatic
    fun open(
        path: String,
        line: Int,
    ): Unit =
        onEdt {
            val editor =
                checkNotNull(
                    FileEditorManager.getInstance(project()).openTextEditor(OpenFileDescriptor(project(), file(path), line, 0), true),
                ) {
                    "$path did not open in a text editor"
                }
            editor.scrollingModel.scrollTo(LogicalPosition(line, 0), ScrollType.CENTER)
            IdeFocusManager.getInstance(project()).requestFocus(editor.contentComponent, true)
        }

    /**
     * Puts the selected editor's caret on the [occurrence] (zero-based) of [text], [delta] characters into it, and
     * scrolls it to the middle; with [select], selects the text instead. Answers the caret's line and column.
     */
    @JvmStatic
    fun caret(
        text: String,
        occurrence: Int,
        delta: Int,
        select: Boolean,
    ): String =
        onEdt {
            val editor = editor()
            val start = offset(editor, text, occurrence)
            editor.caretModel.removeSecondaryCarets()
            if (select) {
                editor.selectionModel.setSelection(start, start + text.length)
                editor.caretModel.moveToOffset(start + text.length)
            } else {
                editor.selectionModel.removeSelection()
                editor.caretModel.moveToOffset(start + delta)
            }
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            IdeFocusManager.getInstance(project()).requestFocus(editor.contentComponent, true)
            val position = editor.caretModel.logicalPosition
            JsonObject()
                .apply {
                    addProperty("line", position.line)
                    addProperty("column", position.column)
                }.toString()
        }

    private fun offset(
        editor: Editor,
        text: String,
        occurrence: Int,
    ): Int {
        var start = -1
        repeat(occurrence + 1) {
            start = editor.document.text.indexOf(text, start + 1)
            check(start >= 0) { "No occurrence $occurrence of \"$text\" in ${editor.virtualFile?.name}" }
        }
        return start
    }

    /**
     * Performs the action [id] as its shortcut does in the selected editor, or in the focused component when there is
     * no editor. It runs later on the EDT, so an action that opens a modal dialog does not hold the driver.
     */
    @JvmStatic
    fun action(id: String) {
        val action = checkNotNull(ActionManager.getInstance().getAction(id)) { "No action $id" }
        later {
            val component =
                FileEditorManager.getInstance(project()).selectedTextEditor?.contentComponent
                    ?: IdeFocusManager.getInstance(project()).focusOwner
            val context = DataManager.getInstance().getDataContext(component)
            ActionUtil.performAction(
                action,
                AnActionEvent.createEvent(action, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null),
            )
        }
    }

    /** Types [text] into the selected editor a character at a time, as the keyboard does, typing handlers included. */
    @JvmStatic
    fun type(text: String): Unit =
        onEdt {
            val editor = editor()
            val context = DataManager.getInstance().getDataContext(editor.contentComponent)
            text.forEach { char ->
                if (char == '\n') {
                    val enter = ActionManager.getInstance().getAction("EditorEnter")
                    ActionUtil.performAction(
                        enter,
                        AnActionEvent.createEvent(enter, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null),
                    )
                } else {
                    TypedAction.getInstance().actionPerformed(editor, char, context)
                }
            }
        }

    /** Replaces the selected editor's text from the [occurrence] of [from] on with [to], as one undoable command. */
    @JvmStatic
    fun replace(
        from: String,
        occurrence: Int,
        to: String,
    ): Unit =
        onEdt {
            val editor = editor()
            val start = offset(editor, from, occurrence)
            WriteCommandAction.runWriteCommandAction(project(), "Tour edit", null, {
                editor.document.replaceString(start, start + from.length, to)
            })
            editor.caretModel.moveToOffset(start + to.length)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }

    /**
     * Makes the file at [path] hold [text], in its editor's document and on disk, unless it already does both; a step
     * that edits, and a build or run that saves, leave the project as it was. Answers whether anything changed.
     */
    @JvmStatic
    fun restore(
        path: String,
        text: String,
    ): Boolean =
        onEdt {
            val file = file(path)
            val documents = FileDocumentManager.getInstance()
            val document = checkNotNull(documents.getDocument(file)) { "No document for $path" }
            val changed = document.text != text || documents.isDocumentUnsaved(document) || String(file.contentsToByteArray()) != text
            if (changed) {
                WriteCommandAction.runWriteCommandAction(project(), "Tour restore", null, { document.setText(text) })
                documents.saveDocument(document)
            }
            changed
        }

    /** Deletes the file at [path], such as one that a step's refactoring made. */
    @JvmStatic
    fun delete(path: String): Unit =
        onEdt {
            val file = file(path)
            WriteCommandAction.runWriteCommandAction(project(), "Tour delete", null, { file.delete(this) })
        }

    /** Presses and releases [key], a java.awt.event.KeyEvent VK_ name such as ESCAPE, as the keyboard does. */
    @JvmStatic
    fun key(key: String) {
        val code = KeyEvent::class.java.getField("VK_$key").getInt(null)
        Robot().apply {
            autoDelay = 30
            keyPress(code)
            keyRelease(code)
        }
    }

    /** Closes popups, hints and the completion lookup. */
    @JvmStatic
    fun dismiss(): Unit =
        anyEdt {
            FileEditorManager.getInstance(project()).selectedTextEditor?.let { LookupManager.getActiveLookup(it)?.hideLookup(true) }
            JBPopupFactory.getInstance().getChildPopups(frame()).forEach { it.cancel() }
            HintManager.getInstance().hideAllHints()
        }

    /**
     * Closes the dialogs that show, newest first, as their Cancel does: a DialogWrapper's cancel action, or else a
     * click on a Cancel or Close button.
     */
    @JvmStatic
    fun closeDialogs(): Unit =
        anyEdt {
            Window
                .getWindows()
                .filter { it is JDialog && it.isShowing }
                .reversed()
                .forEach { dialog ->
                    // a click on Cancel closes Settings, whose wrapper's cancel action did not
                    val content = (dialog as JDialog).contentPane
                    val cancel =
                        descendants(dialog)
                            .filterIsInstance<AbstractButton>()
                            .firstOrNull { it.isShowing && it.text?.replace("_", "") in setOf("Cancel", "Close") }
                    if (cancel != null) {
                        later(ModalityState.stateForComponent(cancel)) { cancel.doClick() }
                    } else {
                        val wrapper = DialogWrapper.findInstance(content) ?: DialogWrapper.findInstance(dialog.rootPane)
                        wrapper?.let { later(ModalityState.stateForComponent(content)) { it.doCancelAction() } }
                    }
                }
        }

    /** The text that the newest popup, hint or dialog shows, its HTML as plain text; empty when none shows. */
    @JvmStatic
    fun popupText(): String =
        anyEdt {
            // a balloon, such as the breakpoint's editor, or a hint is in the frame's layered pane, beside any window
            listOfNotNull(newestWindow(), lightweightPopup()).joinToString("\n", transform = ::textOf)
        }

    /** The completion lookup's items, by their lookup strings, as the list shows them. */
    @JvmStatic
    fun lookupItems(): String =
        anyEdt {
            FileEditorManager
                .getInstance(project())
                .selectedTextEditor
                ?.let { LookupManager.getActiveLookup(it) }
                ?.items
                .orEmpty()
                .joinToString("\n") { it.lookupString }
        }

    /** The text that the tool window [id] shows: its trees' rows, lists, tables, consoles and labels. */
    @JvmStatic
    fun toolWindowText(id: String): String =
        anyEdt {
            ToolWindowManager
                .getInstance(project())
                .getToolWindow(id)
                ?.component
                ?.takeIf { it.isShowing }
                ?.let(::textOf)
                .orEmpty()
        }

    /** The errors and warnings that the editor of the file at [path] marks, each as `SEVERITY code: message`. */
    @JvmStatic
    fun diagnostics(path: String): String =
        onEdt {
            val document = FileDocumentManager.getInstance().getDocument(file(path)) ?: return@onEdt ""
            DaemonCodeAnalyzerImpl
                .getHighlights(document, HighlightSeverity.WARNING, project())
                .joinToString("\n") { "${it.severity.name} ${it.description}" }
        }

    /** The text of the file at [path], as its editor's document has it. */
    @JvmStatic
    fun fileText(path: String): String =
        onEdt {
            FileDocumentManager
                .getInstance()
                .getDocument(file(path))
                ?.text
                .orEmpty()
        }

    /** The selected editor's file name, its caret's zero-based line, and its selected text, as JSON. */
    @JvmStatic
    fun editorState(): String =
        onEdt {
            val editor = FileEditorManager.getInstance(project()).selectedTextEditor
            JsonObject()
                .apply {
                    addProperty("file", editor?.virtualFile?.name)
                    addProperty("line", editor?.caretModel?.logicalPosition?.line)
                    addProperty("selection", editor?.selectionModel?.selectedText)
                    addProperty("writable", editor?.document?.isWritable)
                }.toString()
        }

    /** Starts recording test results for [testResults], dropping those of earlier runs. */
    @JvmStatic
    fun watchTests(): Unit = onEdt { TestRunUi.reset(project()) }

    /** The results of the tests run since [watchTests], by their locations: each with its `state` and a failure's `message`. */
    @JvmStatic
    fun testResults(): String = TestRunUi.results()

    /** The text a component shows, with its descendants': labels, text, colored fragments, and tree, list and table rows. */
    private fun textOf(root: Component): String =
        descendants(root)
            .filter { it.isShowing }
            .mapNotNull { component ->
                when (component) {
                    is JEditorPane -> htmlText(component)

                    is JTextComponent -> component.text

                    is SimpleColoredComponent -> component.getCharSequence(false).toString()

                    is JLabel -> component.text?.let(::stripHtml)

                    is AbstractButton -> component.text

                    is JTree -> (0 until component.rowCount).joinToString("\n") { row -> treeRow(component, row) }

                    is JList<*> -> (0 until component.model.size).joinToString("\n") { component.model.getElementAt(it).toString() }

                    is JTable ->
                        (0 until component.rowCount).joinToString("\n") { row ->
                            (0 until component.columnCount).joinToString(" ") { column ->
                                textOf(component.prepareRenderer(component.getCellRenderer(row, column), row, column))
                            }
                        }

                    is JComboBox<*> -> component.selectedItem?.toString()

                    else -> null
                }
            }.filter { it.isNotBlank() }
            .joinToString("\n")

    private fun treeRow(
        tree: JTree,
        row: Int,
    ): String {
        val path = tree.getPathForRow(row) ?: return ""
        val rendered =
            tree.cellRenderer.getTreeCellRendererComponent(tree, path.lastPathComponent, false, tree.isExpanded(row), false, row, false)
        return textOf(rendered).ifBlank { path.lastPathComponent.toString() }
    }

    private fun htmlText(pane: JEditorPane): String =
        runCatching { pane.document.getText(0, pane.document.length) }.getOrElse { stripHtml(pane.text.orEmpty()) }

    private fun stripHtml(text: String): String = text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    /** Whether a dialog shows. */
    @JvmStatic
    fun dialogShowing(): Boolean = anyEdt { Window.getWindows().any { it is JDialog && it.isShowing } }

    /** Whether the newest window's button labeled [text] shows and is enabled, as a dialog enables OK once its input is valid. */
    @JvmStatic
    fun buttonEnabled(text: String): Boolean =
        anyEdt {
            Window
                .getWindows()
                .filter { it.isShowing }
                .reversed()
                .firstNotNullOfOrNull { window ->
                    descendants(window).filterIsInstance<AbstractButton>().firstOrNull {
                        it.isShowing && it.text?.replace("_", "") == text
                    }
                }?.isEnabled == true
        }

    /** Whether the file at [path], as its editor's document has it, contains [text]. */
    @JvmStatic
    fun fileHas(
        path: String,
        text: String,
    ): Boolean =
        onEdt {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path)) ?: return@onEdt false
            FileDocumentManager
                .getInstance()
                .getDocument(file)
                ?.text
                ?.contains(text) == true
        }

    /**
     * Sets the name field of the newest dialog, such as Rename's, to [text], as typing it does: its editor's text,
     * which the dialog checks before it enables its button.
     */
    @JvmStatic
    fun fillDialog(text: String) {
        val field =
            anyEdt {
                Window
                    .getWindows()
                    .filter { it is JDialog && it.isShowing }
                    .reversed()
                    .firstNotNullOfOrNull { window ->
                        descendants(window).filterIsInstance<EditorComponentImpl>().firstOrNull { it.isShowing }
                    }
            }
        checkNotNull(field) { "No dialog with a text field" }
        onEdt(ModalityState.stateForComponent(field)) {
            WriteCommandAction.runWriteCommandAction(project(), "Tour fill", null, { field.editor.document.setText(text) })
            field.editor.caretModel.moveToOffset(text.length)
        }
    }

    /** Shows or hides the tool window [id], such as Project, Run, Debug or Problems View. */
    @JvmStatic
    fun toolWindow(
        id: String,
        visible: Boolean,
    ): Unit =
        onEdt {
            val window = checkNotNull(ToolWindowManager.getInstance(project()).getToolWindow(id)) { "No tool window $id" }
            if (visible) window.show() else window.hide()
        }

    /** Whether the subject [focus] names (see [capture]) shows. */
    @JvmStatic
    fun showing(focus: String): Boolean = anyEdt { focus == "none" || subject(focus) != null }

    /**
     * Sets the focused text field's text to [text], such as a dialog's or a search popup's, as typing it over its
     * selection does.
     */
    @JvmStatic
    fun fill(text: String) {
        val owner = anyEdt { IdeFocusManager.getInstance(project()).focusOwner }
        when (owner) {
            is JTextComponent -> anyEdt { owner.text = text }

            is EditorComponentImpl ->
                onEdt(ModalityState.stateForComponent(owner)) {
                    WriteCommandAction.runWriteCommandAction(project(), "Tour fill", null, { owner.editor.document.setText(text) })
                    owner.editor.caretModel.moveToOffset(text.length)
                }

            else -> error("The focused ${owner?.javaClass?.name} takes no text")
        }
    }

    /** Presses the button labeled [text] in the newest window that shows one, such as a dialog's OK. */
    @JvmStatic
    fun click(text: String) {
        val button =
            anyEdt {
                Window
                    .getWindows()
                    .filter { it.isShowing }
                    .reversed()
                    .firstNotNullOfOrNull { window ->
                        descendants(window).filterIsInstance<AbstractButton>().firstOrNull {
                            it.isShowing && it.isEnabled && it.text?.replace("_", "") == text
                        }
                    }
            }
        checkNotNull(button) { "No button $text" }
        later(ModalityState.stateForComponent(button)) { button.doClick() }
    }

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    /** Chooses the row of the showing list popup, such as Show Intention Actions, that contains [text]. */
    @JvmStatic
    fun choose(text: String): Boolean = onEdt { PopupChoices.chooseListRow(text) }

    /** The rows of the showing list popup, or none. */
    @JvmStatic
    fun rows(): String = anyEdt { PopupChoices.listRows().orEmpty().joinToString("\n") }

    /**
     * Opens Settings at the page with the configurable [id], such as `xtc.build`, or the display name [id]; it runs
     * later, as the dialog is modal.
     */
    @JvmStatic
    fun settings(id: String) {
        later {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project(),
                { (it as? SearchableConfigurable)?.id == id || it.displayName == id },
                null,
            )
        }
    }

    /** The live template contexts that apply at the selected editor's caret, by their names, as the template list sees them. */
    @JvmStatic
    fun templateContexts(): String =
        onEdt {
            val editor = editor()
            val file = checkNotNull(PsiDocumentManager.getInstance(project()).getPsiFile(editor.document)) { "No PSI file" }
            val context = TemplateActionContext.expanding(file, editor)
            val contexts = TemplateManagerImpl.getApplicableContextTypes(context).joinToString { it.presentableName }
            val ours = TemplateSettings.getInstance().templates.count { it.groupName == "Ecstasy" }
            val applicable = TemplateManagerImpl.listApplicableTemplates(context).size
            "PSI ${file.language.id}, file type ${file.originalFile.virtualFile?.fileType?.name}; contexts: $contexts; " +
                "$ours Ecstasy templates, $applicable applicable"
        }

    /**
     * Clicks the gutter icon on the selected editor's [line] (zero-based) whose tooltip starts with [tooltip], such as
     * a Run or test icon, with the caret on that line, as a click there does.
     */
    @JvmStatic
    fun gutter(
        line: Int,
        tooltip: String,
    ): Unit =
        onEdt {
            val editor = editor()
            val renderer =
                checkNotNull(
                    (
                        editor.markupModel.allHighlighters.toList() +
                            DocumentMarkupModel.forDocument(editor.document, project(), false)?.allHighlighters.orEmpty()
                    ).filter { it.isValid && editor.document.getLineNumber(it.startOffset) == line }
                        .mapNotNull { it.gutterIconRenderer }
                        .firstOrNull { it.tooltipText?.startsWith(tooltip) == true },
                ) { "No gutter icon \"$tooltip\" on line $line" }
            editor.caretModel.moveToLogicalPosition(LogicalPosition(line, 0))
            val action = checkNotNull(renderer.clickAction) { "The gutter icon \"$tooltip\" has no click action" }
            val context = DataManager.getInstance().getDataContext(editor.contentComponent)
            ActionUtil.performAction(
                action,
                AnActionEvent.createEvent(action, context, null, ActionPlaces.EDITOR_GUTTER, ActionUiKind.NONE, null),
            )
        }

    /** The tooltips of the gutter icons on the selected editor's lines, as "tooltip@line". */
    @JvmStatic
    fun gutterIcons(): String =
        onEdt {
            val editor = editor()
            (
                editor.markupModel.allHighlighters.toList() +
                    DocumentMarkupModel.forDocument(editor.document, project(), false)?.allHighlighters.orEmpty()
            ).filter { it.isValid }
                .mapNotNull { highlighter ->
                    highlighter.gutterIconRenderer?.let { "${it.tooltipText}@${editor.document.getLineNumber(highlighter.startOffset)}" }
                }.joinToString("\n")
        }

    /**
     * Sets a line breakpoint on the selected editor's [line] (zero-based), as a click in the gutter does, with the
     * [condition] it stops on, unless that is empty.
     */
    @JvmStatic
    fun breakpoint(
        line: Int,
        condition: String,
    ) {
        val file = onEdt { checkNotNull(editor().virtualFile) { "The editor has no file" } }
        onEdt { XDebuggerUtil.getInstance().toggleLineBreakpoint(project(), file, line) }
        val deadline = System.nanoTime() + 10_000_000_000L
        var breakpoint: XLineBreakpoint<*>? = null
        while (breakpoint == null && System.nanoTime() < deadline) {
            breakpoint =
                onEdt {
                    XDebuggerManager
                        .getInstance(project())
                        .breakpointManager.allBreakpoints
                        .filterIsInstance<XLineBreakpoint<*>>()
                        .firstOrNull { it.fileUrl == file.url && it.line == line }
                }
            if (breakpoint == null) Thread.sleep(100)
        }
        checkNotNull(breakpoint) { "No breakpoint was set on line $line" }
        if (condition.isNotEmpty()) {
            onEdt {
                breakpoint.conditionExpression =
                    XDebuggerUtil.getInstance().createExpression(condition, null, null, EvaluationMode.EXPRESSION)
            }
        }
    }

    /** Removes every line breakpoint. */
    @JvmStatic
    fun clearBreakpoints(): Unit =
        onEdt {
            val breakpoints = XDebuggerManager.getInstance(project()).breakpointManager
            breakpoints.allBreakpoints.filterIsInstance<XLineBreakpoint<*>>().forEach(breakpoints::removeBreakpoint)
        }

    /** The current debug session: whether there is one, whether it is `suspended`, and where, as `file` and `line` (1-based). */
    @JvmStatic
    fun debugState(): String =
        anyEdt {
            val session = XDebuggerManager.getInstance(project()).currentSession
            JsonObject()
                .apply {
                    addProperty("session", session != null && !session.isStopped)
                    addProperty("suspended", session?.isSuspended == true)
                    session?.currentPosition?.let { position ->
                        addProperty("file", position.file.name)
                        addProperty("line", position.line + 1)
                    }
                }.toString()
        }

    /** Selects the current debug session's tab whose name contains [name], such as Console, as a click on it does. */
    @JvmStatic
    fun debugTab(name: String): Unit =
        onEdt {
            val session = checkNotNull(XDebuggerManager.getInstance(project()).currentSession) { "No debug session" }
            val layout = session.ui ?: error("The debug session has no UI")
            val content =
                checkNotNull(layout.contents.firstOrNull { it.displayName?.contains(name, ignoreCase = true) == true }) {
                    "No debug tab $name: ${layout.contents.joinToString { it.displayName.orEmpty() }}"
                }
            layout.selectAndFocus(content, true, false)
        }

    /** Whether the selected run, such as a test run, has ended. */
    @JvmStatic
    fun runEnded(): Boolean =
        onEdt {
            RunContentManager
                .getInstance(project())
                .selectedContent
                ?.processHandler
                ?.isProcessTerminated == true
        }

    /** Sends [text] to the program of the selected run, as typing it in the Run console does. */
    @JvmStatic
    fun input(text: String) {
        val handler =
            checkNotNull(onEdt { RunContentManager.getInstance(project()).selectedContent?.processHandler }) { "No run is selected" }
        val input = checkNotNull(handler.processInput) { "The run takes no input" }
        input.write(text.toByteArray())
        input.flush()
    }

    /** The text of the selected run's console. */
    @JvmStatic
    fun console(): String =
        onEdt {
            val console = RunContentManager.getInstance(project()).selectedContent?.executionConsole as? ConsoleViewImpl
            console?.flushDeferredText()
            console?.text.orEmpty()
        }

    /** Sets the project's build mode, `EMBEDDED` or `GRADLE`, and answers the one it had. */
    @JvmStatic
    fun buildMode(value: String): String = onEdt { EmbeddedExecutionUi.mode(project(), value) }

    /** Sets what the selected run configuration runs with, an XtcRunMode name, and answers the configuration's name. */
    @JvmStatic
    fun runWith(value: String): String =
        onEdt {
            val settings = checkNotNull(RunManager.getInstance(project()).selectedConfiguration) { "No run configuration is selected" }
            val configuration =
                settings.configuration as? XtcRunConfiguration ?: error("${settings.name} is not an Ecstasy run configuration")
            configuration.runWith = XtcRunMode.valueOf(value)
            settings.name
        }

    /** Stops every run and debug session. */
    @JvmStatic
    fun stopAll(): Unit =
        onEdt {
            XDebuggerManager.getInstance(project()).debugSessions.forEach { it.stop() }
            ExecutionManager.getInstance(project()).getRunningProcesses().forEach { it.destroyProcess() }
        }

    /**
     * Captures the IDE's window, and everything over it, into the PNG [file], and answers where on it the subject
     * [focus] names is, as `focus` (x, y, width, height) and `cursor` (x, y): `popup`, the newest window over the
     * IDE's, such as a documentation popup or a dialog; `lookup`, the completion list; `caret`; `line:<n>`, a
     * zero-based line of the editor, or `line` the caret's; `gutter:<n>` or `gutter`, its gutter; `toolwindow:<id>`;
     * `editor`; or `none`.
     */
    @JvmStatic
    fun capture(
        file: String,
        focus: String,
    ): String {
        val (bounds, subject) =
            anyEdt {
                val window = frame()
                Rectangle(window.locationOnScreen, window.size) to subject(focus)
            }
        val image = Robot().createScreenCapture(bounds)
        check(ImageIO.write(image, "png", Path.of(file).toFile())) { "No PNG writer" }
        return JsonObject()
            .apply {
                addProperty("width", bounds.width)
                addProperty("height", bounds.height)
                subject?.let { (area, cursor) ->
                    add("focus", rectangle(area, bounds.location))
                    add(
                        "cursor",
                        JsonObject().apply {
                            addProperty("x", cursor.x - bounds.x)
                            addProperty("y", cursor.y - bounds.y)
                        },
                    )
                }
            }.toString()
    }

    /** Where the subject [focus] names is on the screen, and where the pointer would be on it. */
    private fun subject(focus: String): Pair<Rectangle, Point>? {
        val editor = FileEditorManager.getInstance(project()).selectedTextEditor
        val kind = focus.substringBefore(':')
        val argument = focus.substringAfter(':', "")
        val area: Rectangle =
            when (kind) {
                "none" -> return null

                "popup" ->
                    (newestWindow()?.let { Rectangle(it.locationOnScreen, it.size) } ?: lightweightPopup()?.let(::screenBounds))
                        ?: return null

                "lookup" ->
                    editor?.let { LookupManager.getActiveLookup(it)?.component }?.takeIf { it.isShowing }?.let(::screenBounds)
                        ?: return null

                "caret" -> {
                    val point = checkNotNull(editor).visualPositionToXY(editor.caretModel.visualPosition)
                    SwingUtilities.convertPointToScreen(point, editor.contentComponent)
                    Rectangle(point.x - 4, point.y, 8, editor.lineHeight)
                }

                "line", "gutter" -> {
                    val shown = checkNotNull(editor)
                    val line = argument.toIntOrNull() ?: shown.caretModel.logicalPosition.line
                    lineArea(shown, line, gutter = kind == "gutter")
                }

                "toolwindow" ->
                    ToolWindowManager
                        .getInstance(project())
                        .getToolWindow(argument)
                        ?.component
                        ?.takeIf { it.isShowing }
                        ?.let(::screenBounds) ?: return null

                "editor" -> screenBounds(checkNotNull(editor).component)

                else -> throw IllegalArgumentException("Unknown focus $focus")
            }
        val cursor =
            if (kind == "caret" || kind == "line") {
                val point = checkNotNull(editor).visualPositionToXY(editor.caretModel.visualPosition)
                SwingUtilities.convertPointToScreen(point, editor.contentComponent)
                Point(point.x, point.y + editor.lineHeight / 2)
            } else {
                Point(area.x + area.width / 2, area.y + area.height / 2)
            }
        return area to cursor
    }

    private fun lineArea(
        editor: Editor,
        line: Int,
        gutter: Boolean,
    ): Rectangle {
        val y = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
        val component: Component = if (gutter) (editor as EditorEx).gutterComponentEx else editor.contentComponent
        val origin = Point(0, y)
        SwingUtilities.convertPointToScreen(origin, component)
        val width = if (gutter) component.width else editor.scrollingModel.visibleArea.width
        val left = if (gutter) origin.x else screenBounds(editor.contentComponent).x
        return Rectangle(left, origin.y, width, editor.lineHeight)
    }

    /** The newest window that shows over the IDE's frame: a popup, a hint or a dialog. */
    private fun newestWindow(): Window? =
        Window
            .getWindows()
            .filter { it.isShowing && it !== frame() && it.width > 1 && it.height > 1 }
            .lastOrNull()

    /** A popup or hint that the IDE shows in its window's layered pane, such as parameter info, rather than in a window of its own. */
    private fun lightweightPopup(): Component? {
        val layers = (frame() as? RootPaneContainer)?.layeredPane ?: return null
        return layers.components
            .filter { it.isShowing && it.width > 1 && it.height > 1 && layers.getLayer(it) >= JLayeredPane.POPUP_LAYER }
            .maxByOrNull { layers.getLayer(it) }
    }

    private fun screenBounds(component: Component): Rectangle = Rectangle(component.locationOnScreen, component.size)

    private fun rectangle(
        area: Rectangle,
        origin: Point,
    ) = JsonObject().apply {
        addProperty("x", area.x - origin.x)
        addProperty("y", area.y - origin.y)
        addProperty("width", area.width)
        addProperty("height", area.height)
    }
}
