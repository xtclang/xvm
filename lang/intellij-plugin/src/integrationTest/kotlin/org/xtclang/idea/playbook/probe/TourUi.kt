package org.xtclang.idea.playbook.probe

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.codeInsight.codeVision.lensContextIfCreated
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.MainMenuDisplayMode
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings.LanguageServerDefinitionSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ServerTrace
import org.xtclang.idea.XtcFileType
import org.xtclang.idea.lsp.XtcLanguageServer
import org.xtclang.idea.run.XtcAttachConfiguration
import org.xtclang.idea.run.XtcAttachConfigurationType
import org.xtclang.idea.run.XtcBuildSettings
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xtclang.idea.run.XtcRunMode
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JToggleButton
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.MenuSelectionManager
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Drives the IDE for the tour, the guided recording of the plugin's features: it lays the IDE's window out, opens
 * files at a piece of their text, performs actions as their shortcuts and buttons do, types, fills and presses dialogs,
 * sets breakpoints, and captures the screen with what it shows, popups and dialogs included, and where on it the step's
 * subject is, for the recording's highlight. It reads what the IDE shows, as a user sees it: the text that popups,
 * trees, lists and tables render, highlighted ranges, inlay hints, code lenses, the debugger's frames and variables, and
 * the run and debug consoles. Every method works on the one open project; methods that touch the UI run on the EDT
 * themselves, so the driver calls them from any thread.
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
    ): T = boundedEdt(modality, block)

    private fun editor(): Editor = checkNotNull(FileEditorManager.getInstance(project()).selectedTextEditor) { "No editor is selected" }

    /**
     * Scrolls [editor] at once, without the animation that a scroll otherwise plays out over the next frames: a hint or
     * popup that opens meanwhile closes as the editor moves, as Quick Evaluate's did, the tour's next action coming
     * within the animation (run-tour-1213318665870452717).
     */
    private fun scrollNow(
        editor: Editor,
        scroll: ScrollingModel.() -> Unit,
    ) {
        editor.scrollingModel.disableAnimation()
        try {
            editor.scrollingModel.scroll()
        } finally {
            editor.scrollingModel.enableAnimation()
        }
    }

    private fun frame(): Frame = checkNotNull(WindowManager.getInstance().getFrame(project())) { "No IDE frame" }

    private fun file(path: String): VirtualFile =
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))) { "No file $path" }

    /** The current debug session, which the Debug tool window shows. */
    private fun session(): XDebugSession = checkNotNull(XDebuggerManager.getInstance(project()).currentSession) { "No debug session" }

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
            // the main menu as a menu bar of its own, whose menus a click opens: the option in ui.lnf.xml did not take,
            //  and under the hamburger button, the bar it expands opened File, whatever the click on Tools
            val settings = UISettings.getInstance()
            if (settings.mainMenuDisplayMode != MainMenuDisplayMode.SEPARATE_TOOLBAR) {
                settings.mainMenuDisplayMode = MainMenuDisplayMode.SEPARATE_TOOLBAR
                settings.fireUISettingsChanged()
            }
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

    /**
     * What is in progress in the IDE that could still change what a step waits for, empty when nothing is: background
     * tasks and indexing, running processes but a paused debug session's, a debug session that runs or launches, and
     * the daemon's analysis of the selected editor's file; and the server's work.
     */
    @JvmStatic
    fun activity(): String = listOf(ideActivity(), serverActivity()).filter(String::isNotEmpty).joinToString("; ")

    /**
     * The language server's work in hand, as `xtc/busy` answers: the client's requests it has not answered, and its
     * compiler's jobs; empty when it has none, or when no server is started; a server that does not answer is busy.
     */
    private fun serverActivity(): String {
        val server =
            LanguageServiceAccessor
                .getInstance(project())
                .startedServers
                .firstOrNull { it.serverDefinition.id == SERVER_ID }
                ?.languageServer as? XtcLanguageServer ?: return ""
        val busy =
            runCatching { server.busy().get(30, TimeUnit.SECONDS) }.getOrElse { return "the server is not answering" }
        val requests = (busy["requests"] as? Number)?.toInt() ?: 0
        val jobs = (busy["compilerJobs"] as? Number)?.toInt() ?: 0
        return listOfNotNull(
            "the server answering $requests requests".takeIf { requests > 0 },
            "the server compiling, $jobs jobs".takeIf { jobs > 0 },
        ).joinToString("; ")
    }

    private fun ideActivity(): String =
        anyEdt {
            val bar = WindowManager.getInstance().getStatusBar(project()) as? StatusBarEx
            val project = project()
            val sessions = XDebuggerManager.getInstance(project).debugSessions
            // a paused program cannot change anything until the tour resumes it
            val paused = sessions.filter { it.isPaused }.map { it.debugProcess.processHandler }
            val file =
                FileEditorManager
                    .getInstance(project)
                    .selectedTextEditor
                    ?.let { PsiDocumentManager.getInstance(project).getPsiFile(it.document) }
            listOfNotNull(
                // with their text and fraction, which move as a task makes progress
                bar
                    ?.backgroundProcessModels
                    .orEmpty()
                    .joinToString(", ") { (task, model) ->
                        val says = listOfNotNull(model.getText(), model.getDetails()).filter(String::isNotBlank).joinToString(" ")
                        "${task.title} ($says ${(model.getFraction() * 100).toInt()}%)"
                    }.ifEmpty { null },
                "indexing".takeIf { DumbService.isDumb(project) },
                // with its consoles' size, which grows as it prints: a process that runs on prints, or does nothing
                RunContentManager
                    .getInstance(project)
                    .allDescriptors
                    .filter { it.processHandler?.let { handler -> !handler.isProcessTerminated && handler !in paused } == true }
                    .joinToString(", ") { descriptor ->
                        val printed =
                            descriptor.component
                                ?.let {
                                    UIUtil.uiTraverser(it).filter(ConsoleViewImpl::class.java).sumOf { console ->
                                        console.contentSize
                                    }
                                }
                                ?: 0
                        "running ${descriptor.displayName}, $printed characters printed"
                    }.ifEmpty { null },
                sessions.firstOrNull { !it.isStopped && !it.isPaused }?.let { "debugging" },
                // the file's own analysis, not the daemon's: with a debug session paused, the Debug window's expression
                // field restarts the daemon every 300 ms, which then never stands idle; and only while the daemon runs or
                // has a pass to run: in run 16, Player.x stood unfinished for an hour with no pass running or pending
                "analyzing ${file?.name}".takeIf {
                    val daemon = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
                    file != null && !daemon.isErrorAnalyzingFinished(file) && daemon.isRunningOrPending
                },
            ).joinToString("; ")
        }

    /** The IDE's threads, as a thread dump shows them. */
    @JvmStatic
    fun ideThreads(): String = ThreadDumper.dumpThreadsToString()

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

    /** The Ecstasy language server's status, such as `started`, and its process's ID; empty when it has none. */
    @JvmStatic
    fun serverState(): String =
        LanguageServiceAccessor
            .getInstance(project())
            .startedServers
            .firstOrNull { it.serverDefinition.id == SERVER_ID }
            ?.let { wrapper -> "${wrapper.serverStatus} ${runCatching { wrapper.currentProcessId }.getOrNull() ?: ""}".trim() }
            .orEmpty()

    /**
     * Whether LSP4IJ has connected the file at [path] to the started Ecstasy language server, as it does with each open
     * file once a server is up; a request for a file it has not connected yet goes to no server.
     */
    @JvmStatic
    fun serverHasFile(path: String): Boolean =
        LanguageServiceAccessor
            .getInstance(project())
            .startedServers
            .firstOrNull { it.serverDefinition.id == SERVER_ID && it.serverStatus == ServerStatus.started }
            ?.isConnectedTo(Path.of(path).toUri()) == true

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
            scrollNow(editor) { scrollTo(LogicalPosition(line, 0), ScrollType.CENTER) }
            // as a click in the editor does: focus alone can leave a tool window active, whose context the editor's
            // actions, such as Alt+Enter and Undo, then use instead of this editor's
            ToolWindowManager.getInstance(project()).activateEditorComponent()
            IdeFocusManager.getInstance(project()).requestFocus(editor.contentComponent, true)
        }

    /**
     * Selects the editor of the file at [path], opening it if it is not open, as a click on its tab does: unlike [open],
     * it leaves the caret where it was. IntelliJ's first Undo after the caret has left the last change only takes the
     * caret back to it, and undoes nothing.
     */
    @JvmStatic
    fun selectFile(path: String): Unit =
        onEdt {
            checkNotNull(
                FileEditorManager.getInstance(project()).openFile(file(path), true).firstOrNull { it is TextEditor },
            ) { "$path did not open in a text editor" }
        }

    /**
     * Moves the selected editor's caret [delta] characters, within its text, as an arrow key does, without a key: a key
     * pressed while the IDE is busy waits in the queue for whatever opens next.
     */
    @JvmStatic
    fun moveCaret(delta: Int): Unit =
        onEdt {
            val editor = editor()
            editor.caretModel.moveToOffset((editor.caretModel.offset + delta).coerceIn(0, editor.document.textLength))
        }

    /**
     * How the file at [path] stands, as the plugin's file type and language make it: its file type, whether that is the
     * plugin's, with an icon, its editor tab's title, its PSI's language and class, and the token that its editor's
     * highlighter gives the first [word] of its text, which TextMate's grammar colors.
     */
    @JvmStatic
    fun fileState(
        path: String,
        word: String,
    ): String =
        onEdt {
            val file = file(path)
            val psi = PsiManager.getInstance(project()).findFile(file)
            val editor =
                FileEditorManager
                    .getInstance(project())
                    .getEditors(file)
                    .filterIsInstance<TextEditor>()
                    .firstOrNull()
                    ?.editor
            val offset = editor?.document?.text?.indexOf(word) ?: -1
            JsonObject()
                .apply {
                    addProperty("fileType", file.fileType.name)
                    addProperty("plugin", file.fileType === XtcFileType && XtcFileType.icon != null)
                    addProperty("tab", EditorTabPresentationUtil.getEditorTabTitle(project(), file))
                    addProperty("language", psi?.language?.id)
                    addProperty("psi", psi?.javaClass?.simpleName)
                    addProperty("highlighter", editor?.highlighter?.javaClass?.simpleName)
                    addProperty(
                        "token",
                        if (editor != null &&
                            offset >= 0
                        ) {
                            editor.highlighter
                                .createIterator(offset)
                                .tokenType
                                .toString()
                        } else {
                            ""
                        },
                    )
                }.toString()
        }

    /**
     * Runs, with Run, the configuration named [name] that the Project window's context menu of the folder at [path]
     * offers, as Run 'name' there does; answers the configurations it offers, the one it ran first, one to a line.
     */
    @JvmStatic
    fun runFromFolder(
        path: String,
        name: String,
    ): String =
        onEdt {
            val folder = checkNotNull(PsiManager.getInstance(project()).findDirectory(file(path))) { "No folder $path" }
            // the Project view's selection, as its context menu has it: ConfigurationContext finds its location in the
            // selected elements, and not in PSI_ELEMENT, so that a folder without them offers nothing
            val data =
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project())
                    .add(CommonDataKeys.PSI_ELEMENT, folder)
                    .add(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, arrayOf(folder))
                    .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(folder))
                    .build()
            val offered = ConfigurationContext.getFromContext(data, ActionPlaces.PROJECT_VIEW_POPUP).configurationsFromContext.orEmpty()
            // the run manager makes a new configuration's name unique, so the one offered may say more than [name]
            offered.firstOrNull { it.configuration.name.startsWith(name) }?.let { chosen ->
                ExecutionUtil.runConfiguration(chosen.configurationSettings, DefaultRunExecutor.getRunExecutorInstance())
            }
            offered.joinToString("\n") { it.configuration.name }
        }

    /**
     * Shows a modal message dialog with [title] and [message] once the EDT gets to it, as an IDE error dialog shows, and
     * answers at once: the dialog waits for an answer, which closeDialogs gives.
     */
    @JvmStatic
    fun showModalMessage(
        title: String,
        message: String,
    ): Unit = ApplicationManager.getApplication().invokeLater({ Messages.showErrorDialog(project(), message, title) }, ModalityState.any())

    /**
     * Where the keyboard focus is, for a failure's message: the focus owner's class and name, whether it is the
     * selected editor's own, the selected editor's file, whether a lookup shows over it, and the window that has it.
     */
    @JvmStatic
    fun focusState(): String =
        anyEdt {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val editor = FileEditorManager.getInstance(project()).selectedTextEditor
            val window =
                when (val window = owner?.let { SwingUtilities.getWindowAncestor(it) }) {
                    null -> "none"
                    is Dialog -> "dialog '${window.title}'"
                    is Frame -> "frame '${window.title}'"
                    else -> window.javaClass.simpleName
                }
            listOf(
                "owner ${owner?.javaClass?.simpleName ?: "none"}${owner?.name?.let { " '$it'" }.orEmpty()}",
                "the selected editor's: ${editor != null && owner === editor.contentComponent}",
                "selected ${editor?.virtualFile?.name ?: "no editor"}",
                "lookup: ${editor != null && LookupManager.getActiveLookup(editor) != null}",
                "window $window",
            ).joinToString(", ")
        }

    /**
     * Asks for the keyboard focus for the selected editor, as a user's click into it gives it, and answers at once;
     * [editorFocused] says when it has it.
     */
    @JvmStatic
    fun focusEditor(): Unit =
        onEdt {
            ToolWindowManager.getInstance(project()).activateEditorComponent()
            IdeFocusManager.getInstance(project()).requestFocus(editor().contentComponent, true)
        }

    /**
     * How the Undo action stands for the selected editor, for a failure's message: whether it is enabled there, and its
     * text, as its update with the editor's context makes them, and the active keymap's shortcuts for it.
     */
    @JvmStatic
    fun undoActionState(): String =
        onEdt {
            val action = checkNotNull(ActionManager.getInstance().getAction(IdeActions.ACTION_UNDO)) { "No Undo action" }
            val context = DataManager.getInstance().getDataContext(editor().contentComponent)
            val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)
            action.update(event)
            val keymap = KeymapManager.getInstance().activeKeymap
            val keys = keymap.getShortcuts(IdeActions.ACTION_UNDO).joinToString { KeymapUtil.getShortcutText(it) }
            "${if (event.presentation.isEnabled) "enabled" else "disabled"} as '${event.presentation.text}', keymap ${keymap.name}: $keys"
        }

    /**
     * The problems that the Problems window's Project Errors list, as its collector has them, for a failure's message:
     * how many, and each file's with their texts.
     */
    @JvmStatic
    fun projectProblems(): String =
        onEdt {
            val collector = ProblemsCollector.getInstance(project())
            val files =
                collector.getProblemFiles().joinToString("; ") { file ->
                    "${file.name}: ${collector.getFileProblems(file).joinToString { it.text }}"
                }
            "${collector.getProblemCount()} problems${if (files.isEmpty()) "" else ", $files"}"
        }

    /**
     * The menu items that show, window by window, newest first, for a failure's message: each one's text, and whether a
     * click at its middle would hit it, as [clickMenuItem] asks.
     */
    @JvmStatic
    fun menuItems(): String =
        anyEdt {
            Window
                .getWindows()
                .filter { it.isShowing }
                .reversed()
                .mapNotNull { window ->
                    descendants(window)
                        .filterIsInstance<JMenuItem>()
                        .filter { it.isShowing }
                        .joinToString(", ") { item -> "${item.text}${if (hit(item, center(item))) "" else " (hidden)"}" }
                        .takeIf(String::isNotEmpty)
                        ?.let { "${window.javaClass.simpleName}: $it" }
                }.joinToString("; ")
                .ifEmpty { "none" }
        }

    /** The run manager's configurations, each with its type and whether it is temporary, for a failure's message. */
    @JvmStatic
    fun runConfigurations(): String =
        onEdt {
            RunManager.getInstance(project()).allSettings.joinToString("; ") {
                "${it.name} (${it.type.displayName}${if (it.isTemporary) ", temporary" else ""})"
            }
        }

    /**
     * Undoes the selected editor's last change with UndoManager itself, as the Undo action asks it to, later on the EDT,
     * and answers at once: an undo that changed the project asks first, in a modal dialog.
     */
    @JvmStatic
    fun undoDirectly(): Unit = later { UndoManager.getInstance(project()).undo(FileEditorManager.getInstance(project()).selectedEditor) }

    /** Redoes the selected editor's last undone change with UndoManager itself, later on the EDT. */
    @JvmStatic
    fun redoDirectly(): Unit = later { UndoManager.getInstance(project()).redo(FileEditorManager.getInstance(project()).selectedEditor) }

    /**
     * Performs the Undo action as its shortcut does, with the selected editor's context, later on the EDT, as
     * CodeActionUndoTest performs it: the action, without the key's dispatch.
     */
    @JvmStatic
    fun undoByAction(): Unit =
        later {
            val action = checkNotNull(ActionManager.getInstance().getAction(IdeActions.ACTION_UNDO)) { "No Undo action" }
            perform(action, DataManager.getInstance().getDataContext(editor().contentComponent), ActionPlaces.KEYBOARD_SHORTCUT)
        }

    /**
     * The selected editor's highlights, warnings and errors, whose range holds the caret, by their severity and
     * description, as Show Error Description finds the one it shows.
     */
    @JvmStatic
    fun highlightsAtCaret(): String =
        onEdt {
            val editor = editor()
            val offset = editor.caretModel.offset
            DaemonCodeAnalyzerImpl
                .getHighlights(editor.document, HighlightSeverity.WARNING, project())
                .filter { offset in it.startOffset..it.endOffset }
                .joinToString("\n") { "${it.severity.name} ${it.description}" }
        }

    /**
     * How the daemon stands for the file at [path], for a failure's message: whether its error analysis and all its
     * analysis are finished, whether the daemon runs or has a pass pending, and whether it restarts by its timer.
     */
    @JvmStatic
    fun daemonState(path: String): String =
        onEdt {
            val daemon = DaemonCodeAnalyzer.getInstance(project()) as DaemonCodeAnalyzerImpl
            val document = FileDocumentManager.getInstance().getDocument(file(path)) ?: return@onEdt "no document"
            val psi = PsiDocumentManager.getInstance(project()).getPsiFile(document) ?: return@onEdt "no PSI"
            listOf(
                "errors analyzed: ${daemon.isErrorAnalyzingFinished(psi)}",
                "all analyzed: ${daemon.isAllAnalysisFinished(psi)}",
                "running: ${daemon.isRunning}",
                "running or pending: ${daemon.isRunningOrPending}",
                "by its timer: ${daemon.isUpdateByTimerEnabled}",
                "committed: ${PsiDocumentManager.getInstance(project()).isCommitted(document)}",
            ).joinToString(", ")
        }

    /** Closes every menu that is open, the main menu's and a popup menu's, as Escape on each does. */
    @JvmStatic
    fun closeMenus(): Unit = anyEdt { MenuSelectionManager.defaultManager().clearSelectedPath() }

    /** Whether the selected editor has the keyboard focus. */
    @JvmStatic
    fun editorFocused(): Boolean = onEdt { editor().contentComponent.isFocusOwner }

    /** Removes the tool window [id]'s contents, such as the Build window's reports of the builds so far. */
    @JvmStatic
    fun clearToolWindow(id: String): Unit =
        onEdt {
            ToolWindowManager
                .getInstance(project())
                .getToolWindow(id)
                ?.contentManager
                ?.removeAllContents(true)
        }

    /** Closes the editor of the file at [path], as its tab's close button does. */
    @JvmStatic
    fun close(path: String): Unit = onEdt { FileEditorManager.getInstance(project()).closeFile(file(path)) }

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
            scrollNow(editor) { scrollToCaret(ScrollType.CENTER) }
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
        val shortcut = KeymapUtil.getFirstKeyboardShortcutText(action)
        logAction(if (shortcut.isEmpty()) "action" else "key", shortcut.ifEmpty { action.templateText ?: id })
        // with the editor focused, a single stroke's keys, as a user presses them; else the action, as before
        val stroke =
            KeymapUtil
                .getActiveKeymapShortcuts(id)
                .shortcuts
                .filterIsInstance<KeyboardShortcut>()
                .firstOrNull { it.secondKeyStroke == null }
                ?.firstKeyStroke
        if (visible && stroke != null &&
            onEdt {
                FileEditorManager
                    .getInstance(project())
                    .selectedTextEditor
                    ?.contentComponent
                    ?.isFocusOwner == true
            }
        ) {
            press(stroke)
            return
        }
        later {
            val component =
                FileEditorManager.getInstance(project()).selectedTextEditor?.contentComponent
                    ?: IdeFocusManager.getInstance(project()).focusOwner
            perform(action, DataManager.getInstance().getDataContext(component), ActionPlaces.KEYBOARD_SHORTCUT)
        }
    }

    /**
     * Performs the action [id] in the selected editor as a key press there does, as the playbook's Undo and Redo do:
     * the editor takes the focus first, and the action's update decides whether it runs.
     */
    @JvmStatic
    fun editorAction(id: String) {
        val action = checkNotNull(ActionManager.getInstance().getAction(id)) { "No action $id" }
        later {
            val editor = checkNotNull(FileEditorManager.getInstance(project()).selectedTextEditor) { "No editor for $id" }
            ToolWindowManager.getInstance(project()).activateEditorComponent()
            IdeFocusManager.getInstance(project()).requestFocus(editor.contentComponent, true)
            // at once, with the editor's own context: a run deferred until the focus settles waited past a failed
            //  check, and Undo's question then opened after the check's cleanup had looked for dialogs
            ActionManager.getInstance().tryToExecute(action, null, editor.contentComponent, ActionPlaces.KEYBOARD_SHORTCUT, true)
        }
    }

    private fun perform(
        action: AnAction,
        context: DataContext,
        place: String,
    ) {
        ActionUtil.performAction(action, AnActionEvent.createEvent(action, context, null, place, ActionUiKind.NONE, null))
    }

    /**
     * Performs the action [id] in the tool window [window] as its shortcut does there, with the focused component's
     * context, such as Rerun or Rerun Failed Tests in the Run window, or Jump to Source in its test tree.
     */
    @JvmStatic
    fun windowAction(
        window: String,
        id: String,
    ) {
        val action = checkNotNull(ActionManager.getInstance().getAction(id)) { "No action $id" }
        later {
            val shown = checkNotNull(ToolWindowManager.getInstance(project()).getToolWindow(window)) { "No tool window $window" }
            val component =
                IdeFocusManager
                    .getInstance(project())
                    .focusOwner
                    ?.takeIf { SwingUtilities.isDescendingFrom(it, shown.component) }
                    ?: descendants(shown.component).filterIsInstance<JTree>().firstOrNull { it.isShowing }
                    ?: descendants(shown.component).filterIsInstance<EditorComponentImpl>().firstOrNull { it.isShowing }
                    ?: shown.component
            perform(action, DataManager.getInstance().getDataContext(component), ActionPlaces.KEYBOARD_SHORTCUT)
        }
    }

    /**
     * Whether the action [id] is enabled in the tool window [window], as its button there shows, such as Rerun Failed
     * Tests once a test failed.
     */
    @JvmStatic
    fun windowActionEnabled(
        window: String,
        id: String,
    ): Boolean =
        onEdt {
            val action = checkNotNull(ActionManager.getInstance().getAction(id)) { "No action $id" }
            val shown = checkNotNull(ToolWindowManager.getInstance(project()).getToolWindow(window)) { "No tool window $window" }
            val component = descendants(shown.component).filterIsInstance<JTree>().firstOrNull { it.isShowing } ?: shown.component
            val event =
                AnActionEvent.createEvent(
                    action,
                    DataManager.getInstance().getDataContext(component),
                    null,
                    ActionPlaces.UNKNOWN,
                    ActionUiKind.NONE,
                    null,
                )
            ActionUtil.updateAction(action, event)
            event.presentation.isEnabledAndVisible
        }

    /**
     * Clicks the button of the action [id] in the IDE's window, such as the main toolbar's Run or Debug, as the mouse
     * does. It clicks later on the EDT, so an action that opens a dialog does not hold the driver.
     */
    @JvmStatic
    fun toolbar(id: String) {
        val button =
            onEdt {
                val actions = ActionManager.getInstance()
                val buttons = descendants(frame()).filterIsInstance<ActionButton>().filter { it.isShowing }.toList()

                // the run widget's executor buttons may wrap the executor's action, which its text then names
                fun name(button: ActionButton) =
                    actions.getId(button.action) ?: button.presentation.text ?: button.action.javaClass.simpleName
                buttons.firstOrNull { actions.getId(it.action) == id }
                    ?: buttons.firstOrNull { actions.getId(it.action) == null && it.presentation.text?.startsWith(id) == true }
                    ?: error("No button $id; the window's buttons: ${buttons.map(::name)}")
            }
        val at = onEdt { center(button) }
        logAction("click", "click: ${onEdt { button.presentation.text ?: id }}", at)
        if (visible) robotClick(at) else later { button.click() }
    }

    /**
     * How the window's button of the action [id] stands, found as [toolbar] finds it: whether it is enabled, and the
     * text and description that its presentation gives, as its tooltip shows them; `none` when no button shows.
     */
    @JvmStatic
    fun toolbarState(id: String): String =
        onEdt {
            val actions = ActionManager.getInstance()
            val buttons = descendants(frame()).filterIsInstance<ActionButton>().filter { it.isShowing }.toList()
            val button =
                buttons.firstOrNull { actions.getId(it.action) == id }
                    ?: buttons.firstOrNull { actions.getId(it.action) == null && it.presentation.text?.startsWith(id) == true }
            button?.presentation?.let { "enabled=${it.isEnabled} text=${it.text} description=${it.description}" } ?: "none"
        }

    /** The selected run configuration's name, or empty when none is selected, as with Current File. */
    @JvmStatic
    fun selectedConfiguration(): String =
        onEdt {
            RunManager
                .getInstance(project())
                .selectedConfiguration
                ?.name
                .orEmpty()
        }

    /**
     * Whether the tour's actions are made visible for a screen recording: the pointer moves to what a step clicks and
     * clicks it with real input, an editor action's shortcut is pressed as keys, and typing goes at a readable pace. It
     * is presentation only: no check depends on it.
     */
    @Volatile
    private var visible = false

    /** What the tour pressed, clicked and typed, each with its time in epoch milliseconds, for the clips' badges. */
    private val actions = CopyOnWriteArrayList<JsonObject>()

    /** The IDE window's bounds on the screen, as `x,y,width,height`, which a screen recording's clips are cut to. */
    @JvmStatic
    fun frameBounds(): String = onEdt { frame().bounds.let { "${it.x},${it.y},${it.width},${it.height}" } }

    /** Makes the tour's actions visible, or not, for a screen recording; see [visible]. */
    @JvmStatic
    fun showActions(on: Boolean) {
        visible = on
    }

    /** What the tour pressed, clicked and typed: each action's `time`, `kind`, `label`, and a click's screen `x` and `y`. */
    @JvmStatic
    fun actionLog(): String = JsonArray().apply { actions.forEach(::add) }.toString()

    private fun logAction(
        kind: String,
        label: String,
        at: Point? = null,
    ) {
        actions +=
            JsonObject().apply {
                addProperty("time", System.currentTimeMillis())
                addProperty("kind", kind)
                addProperty("label", label)
                at?.let {
                    addProperty("x", it.x)
                    addProperty("y", it.y)
                }
            }
    }

    /**
     * Moves the pointer to [at] on the screen in steps a viewer can follow, and clicks there, as a hand does; or where
     * [aim] then says the target is, when it has moved meanwhile.
     */
    private fun robotClick(
        at: Point,
        aim: () -> Point = { at },
    ) {
        val from = MouseInfo.getPointerInfo()?.location ?: at
        Robot().apply {
            (1..12).forEach { step ->
                mouseMove(from.x + (at.x - from.x) * step / 12, from.y + (at.y - from.y) * step / 12)
                delay(25)
            }
            delay(150)
            val target = aim()
            if (target != at) {
                mouseMove(target.x, target.y)
                delay(150)
            }
            mousePress(InputEvent.BUTTON1_DOWN_MASK)
            delay(60)
            mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        }
    }

    /** Presses [stroke] as keys, its modifiers held, as a hand does. */
    private fun press(stroke: KeyStroke) {
        val modifiers =
            listOf(
                InputEvent.CTRL_DOWN_MASK to KeyEvent.VK_CONTROL,
                InputEvent.SHIFT_DOWN_MASK to KeyEvent.VK_SHIFT,
                InputEvent.ALT_DOWN_MASK to KeyEvent.VK_ALT,
                InputEvent.META_DOWN_MASK to KeyEvent.VK_META,
            ).filter { (mask, _) -> stroke.modifiers and mask != 0 }.map { it.second }
        Robot().apply {
            autoDelay = 30
            modifiers.forEach(::keyPress)
            keyPress(stroke.keyCode)
            keyRelease(stroke.keyCode)
            modifiers.reversed().forEach(::keyRelease)
        }
    }

    /** The middle of [component] on the screen. */
    private fun center(component: Component): Point =
        component.locationOnScreen.let { Point(it.x + component.width / 2, it.y + component.height / 2) }

    /**
     * Presses the shortcut [first], then [second], such as "ctrl alt X" then "L", as keys where the keyboard focus is,
     * which the tour gives the selected editor first, as a user does: the keymap decides what runs, so a shortcut the
     * plugin lost does nothing.
     */
    @JvmStatic
    fun chord(
        first: String,
        second: String,
    ) {
        val strokes = listOf(first, second).map { checkNotNull(KeyStroke.getKeyStroke(it)) { "No key stroke $it" } }
        logAction("key", strokes.joinToString(", ") { KeymapUtil.getKeystrokeText(it) })
        strokes.forEach(::press)
    }

    /** Opens the IDE's main menu, as a click on its menu bar's [menu], or on the Main Menu button, does. */
    @JvmStatic
    fun openMenu(menu: String) {
        val target =
            onEdt {
                val bar = descendants(frame()).filterIsInstance<JMenuBar>().firstOrNull { it.isShowing }
                bar?.let { descendants(it).filterIsInstance<JMenuItem>().firstOrNull { item -> item.isShowing && item.text == menu } }
                    ?: descendants(frame()).filterIsInstance<ActionButton>().firstOrNull {
                        it.isShowing &&
                            it.presentation.text == "Main Menu"
                    }
                    ?: error("No menu bar or Main Menu button")
            }
        val at = onEdt { center(target) }
        logAction("click", "click: ${onEdt { (target as? JMenuItem)?.text ?: "Main Menu" }}", at)
        robotClick(at)
    }

    /**
     * Clicks the menu item [text], of the main menu, a menu or a popup's list, as a hand does, newest window first; false
     * while none shows where a click would hit it: the new UI keeps the frame's menu bar, with its items, behind its
     * Main Menu button, where a click at an item lands on the toolbar instead.
     */
    @JvmStatic
    fun clickMenuItem(text: String): Boolean {
        val at =
            anyEdt {
                val windows = Window.getWindows().filter { it.isShowing }.reversed()
                // a menu's items first, in every window, then a list popup's rows: a renderer that cannot draw an
                // element it was not made for answers nothing rather than failing the search
                windows.firstNotNullOfOrNull { window ->
                    descendants(window)
                        .filterIsInstance<JMenuItem>()
                        .firstOrNull { it.isShowing && it.text?.replace("_", "") == text && hit(it, center(it)) }
                        ?.let(::center)
                } ?: windows.firstNotNullOfOrNull { window ->
                    descendants(window).filterIsInstance<JList<*>>().filter { it.isShowing }.firstNotNullOfOrNull { list ->
                        (0 until list.model.size)
                            .firstOrNull { row -> runCatching { listRowText(list, row) }.getOrNull() == text }
                            ?.let { row -> list.getCellBounds(row, row) }
                            ?.let { bounds ->
                                Point(bounds.centerX.toInt(), bounds.centerY.toInt()).also { SwingUtilities.convertPointToScreen(it, list) }
                            }?.takeIf { hit(list, it) }
                    }
                }
            } ?: return false
        logAction("click", "click: $text", at)
        robotClick(at)
        return true
    }

    /** Whether a click at the screen point [at] lands on [component] or inside it, not on what covers it. */
    private fun hit(
        component: Component,
        at: Point,
    ): Boolean {
        val window = SwingUtilities.getWindowAncestor(component) ?: return false
        val local = Point(at).also { SwingUtilities.convertPointFromScreen(it, window) }
        val top = SwingUtilities.getDeepestComponentAt(window, local.x, local.y) ?: return false
        return top === component || SwingUtilities.isDescendingFrom(top, component)
    }

    /** The text that [list] shows on its row [row], as its renderer draws it, such as an action popup's item's text. */
    @Suppress("UNCHECKED_CAST")
    private fun listRowText(
        list: JList<*>,
        row: Int,
    ): String {
        val renderer = list.cellRenderer as ListCellRenderer<Any?>
        val cell = renderer.getListCellRendererComponent(list as JList<Any?>, list.model.getElementAt(row), row, false, false)
        return textOf(cell, rendered = true).trim()
    }

    /** Clicks the showing button labeled [text], such as a settings page's Restart, with real input; false if none shows. */
    @JvmStatic
    fun realClick(text: String): Boolean {
        val button =
            anyEdt {
                Window.getWindows().filter { it.isShowing }.reversed().firstNotNullOfOrNull { window ->
                    descendants(window).filterIsInstance<AbstractButton>().firstOrNull { it.isShowing && it.isEnabled && it.text == text }
                }
            } ?: return false
        val at = anyEdt { center(button) }
        logAction("click", "click: $text", at)
        robotClick(at)
        return true
    }

    /** Clicks the toolbar button of the action [id] in the tool window [window] with real input; false if none shows. */
    @JvmStatic
    fun windowButton(
        window: String,
        id: String,
    ): Boolean {
        val button =
            onEdt {
                val shown = ToolWindowManager.getInstance(project()).getToolWindow(window)?.component ?: return@onEdt null
                descendants(shown).filterIsInstance<ActionButton>().firstOrNull {
                    it.isShowing && ActionManager.getInstance().getId(it.action) == id
                }
            } ?: return false
        val at = onEdt { center(button) }
        logAction("click", "click: ${onEdt { button.presentation.text ?: id }}", at)
        robotClick(at)
        return true
    }

    /** Selects Current File in the run widget, as its list's first item does: no run configuration, the editor's file. */
    @JvmStatic
    fun selectCurrentFile(): Unit = onEdt { RunManager.getInstance(project()).selectedConfiguration = null }

    /** Types [text] into the selected editor a character at a time, as the keyboard does, typing handlers included. */
    @JvmStatic
    fun type(text: String) {
        logAction("type", text.trim().ifEmpty { "Enter" })
        text.forEach { char ->
            onEdt {
                val editor = editor()
                val context = DataManager.getInstance().getDataContext(editor.contentComponent)
                if (char == '\n') {
                    perform(ActionManager.getInstance().getAction("EditorEnter"), context, ActionPlaces.KEYBOARD_SHORTCUT)
                } else {
                    TypedAction.getInstance().actionPerformed(editor, char, context)
                }
            }
            // a readable pace, for the recording only: the text is typed the same either way
            if (visible) Thread.sleep(45)
        }
    }

    /**
     * Inserts [text] at the selected editor's caret, as pasting it does, without the typing handlers that would close
     * its brackets and quotes, and puts the caret after it.
     */
    @JvmStatic
    fun insert(text: String): Unit =
        onEdt {
            val editor = editor()
            val at = editor.caretModel.offset
            WriteCommandAction.runWriteCommandAction(project(), "Tour insert", null, { editor.document.insertString(at, text) })
            editor.caretModel.moveToOffset(at + text.length)
            scrollNow(editor) { scrollToCaret(ScrollType.CENTER) }
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
            scrollNow(editor) { scrollToCaret(ScrollType.CENTER) }
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

    /** Deletes the file or directory at [path], such as one that a step's refactoring made. */
    @JvmStatic
    fun delete(path: String): Unit =
        onEdt {
            val file = file(path)
            WriteCommandAction.runWriteCommandAction(project(), "Tour delete", null, { file.delete(this) })
        }

    /**
     * Makes the file [name] in the directory at [directory] with [text], as New | File and typing it do: through the
     * IDE's file system, which tells the language server, as it tells it of a file the Project view makes.
     */
    @JvmStatic
    fun newFile(
        directory: String,
        name: String,
        text: String,
    ): Unit =
        onEdt {
            val parent = file(directory)
            WriteCommandAction.runWriteCommandAction(project(), "Tour new file", null, {
                val created = parent.createChildData(this, name)
                val document = checkNotNull(FileDocumentManager.getInstance().getDocument(created)) { "No document for $name" }
                document.setText(text)
                FileDocumentManager.getInstance().saveDocument(document)
            })
        }

    /** Makes the directory [name] in the directory at [parent], as New | Directory does, such as a package's. */
    @JvmStatic
    fun newDirectory(
        parent: String,
        name: String,
    ): Unit =
        onEdt {
            val directory = file(parent)
            WriteCommandAction.runWriteCommandAction(project(), "Tour new directory", null, { directory.createChildDirectory(this, name) })
        }

    /**
     * Moves the files at [paths] to the directory at [destination], as dragging them there in the Project view, or
     * F6, does: through the Move handler that takes them, whose dialog then shows.
     */
    @JvmStatic
    fun move(
        paths: String,
        destination: String,
    ): Unit = onEdt { FileTreeOperations.move(project(), paths.split('\n'), destination) }

    /**
     * Selects the row of the newest dialog's lists whose text contains [text], as a click on it does, such as a
     * generator of New Project; false if none shows it.
     */
    @JvmStatic
    fun selectItem(text: String): Boolean =
        anyEdt {
            val dialog = Window.getWindows().filter { it is JDialog && it.isShowing }.lastOrNull() ?: return@anyEdt false
            descendants(dialog).filterIsInstance<JList<*>>().filter { it.isShowing }.firstNotNullOfOrNull { list ->
                (0 until list.model.size).firstOrNull { text in listRow(list, it) }?.let { index ->
                    list.selectedIndex = index
                    list.ensureIndexIsVisible(index)
                    true
                }
            } ?: false
        }

    /**
     * Expands the newest dialog's collapsible group titled [title], as a click on its title does; false if none
     * shows it. The group's separator answers its title and takes its state as `expanded`.
     */
    @JvmStatic
    fun expandGroup(title: String): Boolean =
        anyEdt {
            val dialog = Window.getWindows().filter { it is JDialog && it.isShowing }.lastOrNull() ?: return@anyEdt false
            val group =
                descendants(dialog).firstOrNull { component ->
                    component.isShowing && "CollapsibleTitledSeparator" in component.javaClass.name &&
                        runCatching { component.javaClass.getMethod("getText").invoke(component) }.getOrNull() == title
                } ?: return@anyEdt false
            group.javaClass.getMethod("setExpanded", Boolean::class.javaPrimitiveType).invoke(group, true)
            true
        }

    /**
     * Selects the file at [path] in the Project view and performs the action [id] there, as its context menu or
     * shortcut does, such as Rename, Move or Delete for the file. It runs later, as the action's dialog is modal.
     */
    @JvmStatic
    fun projectAction(
        path: String,
        id: String,
    ) {
        val action = checkNotNull(ActionManager.getInstance().getAction(id)) { "No action $id" }
        val file = file(path)
        onEdt {
            ToolWindowManager.getInstance(project()).getToolWindow("Project")?.show()
            ProjectView.getInstance(project()).select(null, file, true)
        }
        val deadline = System.nanoTime() + 30_000_000_000L
        while (onEdt { selectedInProject() != file }) {
            check(System.nanoTime() < deadline) { "${file.name} was not selected in the Project view" }
            Thread.sleep(100)
        }
        later {
            val tree = checkNotNull(ProjectView.getInstance(project()).currentProjectViewPane?.tree) { "No Project view" }
            IdeFocusManager.getInstance(project()).requestFocus(tree, true)
            perform(action, DataManager.getInstance().getDataContext(tree), ActionPlaces.PROJECT_VIEW_POPUP)
        }
    }

    private fun selectedInProject(): VirtualFile? =
        ProjectView
            .getInstance(project())
            .currentProjectViewPane
            ?.tree
            ?.let { CommonDataKeys.VIRTUAL_FILE.getData(DataManager.getInstance().getDataContext(it)) }

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

    /**
     * Presses the active keymap's first one-stroke shortcut for the action [id] as keys, as a user does, such as Ctrl+Z
     * for Undo in Default for XWin and Cmd+Z in macOS, where the keyboard focus is, which the tour gives the selected
     * editor first: the keys go to it as the window system delivers them, which runs the action that the keymap binds.
     * It presses nothing unless the selected editor has the focus, as the window system gives keys to the app in front,
     * which on a desktop need not be the IDE. Answers the keys and where the focus is as they go, as [focusState], and
     * which key presses reached the IDE and which actions it ran, by the time its event queue is idle after them.
     */
    @JvmStatic
    fun editorShortcut(id: String): String {
        val stroke =
            checkNotNull(
                KeymapUtil
                    .getActiveKeymapShortcuts(id)
                    .shortcuts
                    .filterIsInstance<KeyboardShortcut>()
                    .firstOrNull { it.secondKeyStroke == null }
                    ?.firstKeyStroke,
            ) { "No one-stroke shortcut for $id in the keymap ${KeymapManager.getInstance().activeKeymap.name}" }
        val keys = KeymapUtil.getKeystrokeText(stroke)
        val focus = focusState()
        if (!editorFocused()) return "$keys, not pressed, as the selected editor does not have the focus: $focus"
        // which of the keys reach the IDE, and what it runs for them, for a failure's message
        val got = CopyOnWriteArrayList<String>()
        val ran = CopyOnWriteArrayList<String>()
        val keyListener =
            AWTEventListener { event ->
                (event as? KeyEvent)?.takeIf { it.id == KeyEvent.KEY_PRESSED }?.let {
                    got += "${KeyEvent.getKeyText(it.keyCode)} in ${it.component?.javaClass?.simpleName}"
                }
            }
        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(
            AnActionListener.TOPIC,
            object : AnActionListener {
                override fun beforeActionPerformed(
                    action: AnAction,
                    event: AnActionEvent,
                ) {
                    val id = ActionManager.getInstance().getId(action) ?: action.javaClass.simpleName
                    // the editor and project that the action works on, as its event's context gives them, against the
                    // selected editor, which a direct undo of UndoManager's undid where Ctrl+Z's Undo did not
                    val given = PlatformCoreDataKeys.FILE_EDITOR.getData(event.dataContext)
                    val selected = FileEditorManager.getInstance(project()).selectedEditor
                    val editor =
                        given?.javaClass?.simpleName?.plus(if (given === selected) ", the selected one" else ", not the selected one")
                            ?: "no editor"
                    val owner = if (CommonDataKeys.PROJECT.getData(event.dataContext) == null) "no project" else "the project"
                    val undo = given?.let { UndoManager.getInstance(project()).isUndoAvailable(it) }
                    // UndoManager undoes nothing while a command runs, or an undo or redo does
                    val commands = CommandProcessor.getInstance()
                    val command =
                        commands.currentCommand?.let { "the command '${commands.currentCommandName}' (${commands.currentCommandGroupId})" }
                            ?: "no command"
                    val undoing = UndoManager.getInstance(project()).isUndoOrRedoInProgress
                    ran += "$id, with $editor and $owner in its context, and an undo for that editor: $undo, during " +
                        "$command, an undo or redo in progress: $undoing"
                }

                // how the action ended: Undo, its undo for that editor there, ran and undid nothing
                override fun afterActionPerformed(
                    action: AnAction,
                    event: AnActionEvent,
                    result: AnActionResult,
                ) {
                    val id = ActionManager.getInstance().getId(action) ?: action.javaClass.simpleName
                    val outcome =
                        when (result) {
                            is AnActionResult.Failed -> "failed with ${result.cause}"
                            is AnActionResult.Ignored -> "was ignored: ${result.reason}"
                            else -> "was performed"
                        }
                    ran += "$id $outcome"
                }
            },
        )
        Toolkit.getDefaultToolkit().addAWTEventListener(keyListener, AWTEvent.KEY_EVENT_MASK)
        try {
            press(stroke)
            Robot().waitForIdle()
            // the keys' events, once the EDT has dispatched what is queued
            anyEdt {}
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(keyListener)
            connection.disconnect()
        }
        return "$keys, with $focus; by the EDT's idle the IDE got ${got.ifEmpty { listOf("no key") }.joinToString()}, " +
            "and ran ${ran.ifEmpty { listOf("no action") }.joinToString()}"
    }

    /** Whether the file at [path] is marked as having errors, red in the Project view and on its editor's tab. */
    @JvmStatic
    fun problemFile(path: String): Boolean = onEdt { WolfTheProblemSolver.getInstance(project()).isProblemFile(file(path)) }

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
                            .firstOrNull { it.isShowing && it.text?.replace("_", "")?.replace("&", "") in setOf("Cancel", "Close", "No") }
                    val wrapper = DialogWrapper.findInstance(content) ?: DialogWrapper.findInstance(dialog.rootPane)
                    // in any modality: a dialog that is not modal, such as Undo's question, is not the modal context
                    later(ModalityState.any()) {
                        when {
                            !dialog.isShowing -> Unit
                            cancel != null -> cancel.doClick()
                            wrapper != null -> wrapper.close(DialogWrapper.CANCEL_EXIT_CODE)
                            else -> dialog.dispose()
                        }
                    }
                }
        }

    /** The text that the newest popup, hint or dialog shows, its HTML as plain text; empty when none shows. */
    @JvmStatic
    fun popupText(): String =
        anyEdt {
            // a balloon, such as the breakpoint's editor, or a hint is in the frame's layered pane, beside any window
            listOfNotNull(newestWindow(), lightweightPopup()).joinToString("\n") { textOf(it) }
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

    /** Selects the completion lookup's item [item], by its lookup string, as the arrow keys do; false if it lists none. */
    @JvmStatic
    fun selectLookup(item: String): Boolean =
        onEdt {
            val lookup =
                FileEditorManager
                    .getInstance(
                        project(),
                    ).selectedTextEditor
                    ?.let { LookupManager.getActiveLookup(it) as? LookupImpl }
            val chosen = lookup?.items?.firstOrNull { it.lookupString == item } ?: return@onEdt false
            lookup.currentItem = chosen
            true
        }

    /**
     * How the completion lookup's current item stands, for a failure's message: its lookup string and class, whether it
     * is still open, and for LSP4IJ's item, whether it needs the server's completionItem/resolve for its details and
     * whether its item already has documentation.
     */
    @JvmStatic
    fun lookupState(): String =
        onEdt {
            val lookup =
                FileEditorManager
                    .getInstance(project())
                    .selectedTextEditor
                    ?.let { LookupManager.getActiveLookup(it) as? LookupImpl } ?: return@onEdt "no lookup"
            val item = lookup.currentItem ?: return@onEdt "a lookup with no current item"
            val proposal = item as? LSPCompletionProposal
            listOfNotNull(
                "the item ${item.lookupString} (${item.javaClass.simpleName})",
                proposal?.let { "needs resolve: ${it.needToResolveCompletionDetail()}" },
                proposal?.let { "documentation in the item: ${it.item.documentation != null}" },
            ).joinToString(", ")
        }

    /** The inline completion that the selected editor shows as ghost text at the caret; empty when it shows none. */
    @JvmStatic
    fun ghostText(): String = onEdt { InlineCompletionUi.visibleText(editor()) }

    /** Chooses the completion lookup's item [item], by its lookup string, as Enter on it does; false if it lists none. */
    @JvmStatic
    fun pickLookup(item: String): Boolean =
        onEdt {
            val lookup =
                FileEditorManager
                    .getInstance(
                        project(),
                    ).selectedTextEditor
                    ?.let { LookupManager.getActiveLookup(it) as? LookupImpl }
            val chosen = lookup?.items?.firstOrNull { it.lookupString == item } ?: return@onEdt false
            lookup.currentItem = chosen
            lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR)
            true
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
                ?.let { textOf(it) }
                .orEmpty()
        }

    /**
     * Expands the rows of the tool window [id]'s trees whose text contains [row], as a click on their arrow does, so
     * that their children show, such as a task group of the Gradle window.
     */
    @JvmStatic
    fun expand(
        id: String,
        row: String,
    ): Unit =
        onEdt {
            val window = checkNotNull(ToolWindowManager.getInstance(project()).getToolWindow(id)?.component) { "No tool window $id" }
            descendants(window).filterIsInstance<JTree>().filter { it.isShowing }.forEach { tree ->
                var index = 0
                while (index < tree.rowCount) {
                    if (row in treeRow(tree, index) && !tree.isExpanded(index)) tree.expandRow(index)
                    index++
                }
            }
        }

    /**
     * Selects the row of the tool window [id]'s tree whose text contains [row], as a click on it does, such as a test
     * in the test results, and focuses the tree; false if no row has it.
     */
    @JvmStatic
    fun selectRow(
        id: String,
        row: String,
    ): Boolean =
        onEdt {
            val window = checkNotNull(ToolWindowManager.getInstance(project()).getToolWindow(id)?.component) { "No tool window $id" }
            descendants(window).filterIsInstance<JTree>().filter { it.isShowing }.firstNotNullOfOrNull { tree ->
                (0 until tree.rowCount).firstOrNull { row in treeRow(tree, it) }?.let { index ->
                    tree.setSelectionRow(index)
                    tree.scrollRowToVisible(index)
                    IdeFocusManager.getInstance(project()).requestFocus(tree, true)
                    val bounds = tree.getRowBounds(index)
                    val origin = tree.locationOnScreen
                    logAction("click", "click: ${row.trim()}", Point(origin.x + bounds.x + 20, origin.y + bounds.y + bounds.height / 2))
                    true
                }
            } ?: false
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

    /**
     * Whether the editor's analysis of the file at [path] is done for its current text, so that its marks, the
     * server's diagnostics among them, are of that text; false while it runs, and for a file that no editor shows.
     */
    @JvmStatic
    fun analyzed(path: String): Boolean =
        onEdt {
            val documents = PsiDocumentManager.getInstance(project())
            val document = FileDocumentManager.getInstance().getDocument(file(path)) ?: return@onEdt false
            val psi = documents.getPsiFile(document) ?: return@onEdt false
            documents.isCommitted(document) &&
                (DaemonCodeAnalyzer.getInstance(project()) as DaemonCodeAnalyzerImpl).isErrorAnalyzingFinished(psi)
        }

    /**
     * The ranges of the selected editor that are highlighted with a text attributes key whose name contains [key], such
     * as SMART_STEP_INTO_TARGET or IDENTIFIER_UNDER_CARET: its markup's and the highlighting's, each with its `text`,
     * its zero-based `line` and its `key`.
     */
    @JvmStatic
    fun highlighted(key: String): String =
        onEdt {
            val editor = editor()
            val document = editor.document
            val ranges =
                (
                    editor.markupModel.allHighlighters.toList() +
                        DocumentMarkupModel.forDocument(document, project(), false)?.allHighlighters.orEmpty()
                ).filter { it.isValid }
                    .mapNotNull { highlighter ->
                        highlighter.textAttributesKey?.externalName?.let { Triple(highlighter.startOffset, highlighter.endOffset, it) }
                    }
            val infos =
                DaemonCodeAnalyzerImpl.getHighlights(document, null, project()).map { info ->
                    Triple(info.startOffset, info.endOffset, (info.forcedTextAttributesKey ?: info.type.attributesKey).externalName)
                }
            JsonArray()
                .apply {
                    (ranges + infos)
                        .filter { (start, end, name) -> key in name && start < end && end <= document.textLength }
                        .distinct()
                        .sortedBy { it.first }
                        .forEach { (start, end, name) ->
                            add(
                                JsonObject().apply {
                                    addProperty("text", document.getText(TextRange(start, end)))
                                    addProperty("line", document.getLineNumber(start))
                                    addProperty("key", name)
                                },
                            )
                        }
                }.toString()
        }

    /**
     * The names of the text attributes keys of the highlighting at the first character of the selected editor's
     * [occurrence] of [text], such as a semantic token's for the name it starts with.
     */
    @JvmStatic
    fun keysAt(
        text: String,
        occurrence: Int,
    ): String =
        onEdt {
            val editor = editor()
            val start = offset(editor, text, occurrence)
            DaemonCodeAnalyzerImpl
                .getHighlights(editor.document, null, project())
                .filter { it.startOffset <= start && it.endOffset > start }
                .map { (it.forcedTextAttributesKey ?: it.type.attributesKey).externalName }
                .distinct()
                .joinToString("\n")
        }

    /** The inline inlay hints on the selected editor's zero-based [line], as their text, such as `numCards:` or `: Player`. */
    @JvmStatic
    fun inlays(line: Int): String =
        onEdt {
            val editor = editor()
            val document = editor.document
            editor.inlayModel
                .getInlineElementsInRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
                .joinToString("\n") { inlayText(it.renderer) }
        }

    private fun inlayText(renderer: EditorCustomElementRenderer): String =
        (renderer as? DeclarativeInlayRendererBase<*>)
            ?.presentationLists
            ?.flatMap { it.getEntries().toList() }
            ?.filterIsInstance<TextInlayPresentationEntry>()
            ?.joinToString("") { it.text }
            ?: renderer.toString()

    /** The selected editor's code lenses, such as `3 references` or `Run cardGame`, each as `text@line`, its zero-based line. */
    @JvmStatic
    fun lenses(): String =
        onEdt {
            val editor = editor()
            editor.lensContextIfCreated
                ?.getValidPairResult()
                .orEmpty()
                .mapNotNull { (range, entry) ->
                    (entry as? TextCodeVisionEntry)?.let { "${it.text}@${editor.document.getLineNumber(range.startOffset)}" }
                }.joinToString("\n")
        }

    /**
     * Clicks the selected editor's code lens whose text starts with [title] on its zero-based [line], or on any line
     * when that is negative, as the mouse does, which runs its command.
     */
    @JvmStatic
    fun clickLens(
        title: String,
        line: Int,
    ): Unit =
        onEdt {
            val editor = editor()
            val entry =
                editor.lensContextIfCreated
                    ?.getValidPairResult()
                    .orEmpty()
                    .firstOrNull { (range, entry) ->
                        entry is ClickableTextCodeVisionEntry && title in entry.text &&
                            (line < 0 || editor.document.getLineNumber(range.startOffset) == line)
                    }?.second as? ClickableTextCodeVisionEntry ?: error("No lens $title on line $line: ${lenses()}")
            val point = editor.offsetToXY(editor.caretModel.offset)
            entry.onClick.invoke(
                MouseEvent(
                    editor.contentComponent,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    point.x,
                    point.y,
                    1,
                    false,
                    MouseEvent.BUTTON1,
                ),
                editor,
            )
        }

    /** The selected editor's folded regions, each as its placeholder and its zero-based line, one a line. */
    @JvmStatic
    fun folds(): String =
        onEdt {
            val editor = editor()
            editor.foldingModel.allFoldRegions
                .filter { it.isValid && !it.isExpanded }
                .joinToString("\n") { "${it.placeholderText}@${editor.document.getLineNumber(it.startOffset)}" }
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

    /** Whether the file at [path] exists, as the IDE's file system sees it. */
    @JvmStatic
    fun exists(path: String): Boolean = onEdt { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))?.isValid == true }

    /** The selected editor's file name, its caret's zero-based line, and its selected text, as JSON. */
    @JvmStatic
    fun editorState(): String =
        onEdt {
            val editor = FileEditorManager.getInstance(project()).selectedTextEditor
            JsonObject()
                .apply {
                    addProperty("file", editor?.virtualFile?.name)
                    addProperty("path", editor?.virtualFile?.path)
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

    /** The tests of the watched run that started and have no result, one location to a line. */
    @JvmStatic
    fun testsRunning(): String = TestRunUi.running()

    /** Whether the selected run's test console says the run was stopped before its tests ended. */
    @JvmStatic
    fun testRunStopped(): Boolean = onEdt { TestRunUi.stopped(project()) }

    /** Whether every run has ended and its console shows all its output, as [TestRunUi.settled] says. */
    @JvmStatic
    fun consolesSettled(): Boolean = onEdt { TestRunUi.settled(project()) }

    /**
     * The text a component shows, with its descendants': labels, text, editors, colored fragments, and tree, list,
     * table and combo box rows as their renderers draw them. A [rendered] component, such as a cell renderer, is not
     * in the window, so it is read whether it shows or not.
     */
    private fun textOf(
        root: Component,
        rendered: Boolean = false,
    ): String =
        descendants(root)
            .filter { rendered || it.isShowing }
            .mapNotNull { component ->
                when (component) {
                    is EditorComponentImpl -> component.editor.document.text

                    is JEditorPane -> htmlText(component)

                    is JTextComponent -> component.text

                    is SimpleColoredComponent -> component.getCharSequence(false).toString()

                    is JLabel -> component.text?.let(::stripHtml)

                    is AbstractButton -> component.text

                    is JTree -> (0 until component.rowCount).joinToString("\n") { row -> treeRow(component, row) }

                    is JList<*> -> (0 until component.model.size).joinToString("\n") { listRow(component, it) }

                    is JTable ->
                        (0 until component.rowCount).joinToString("\n") { row ->
                            (0 until component.columnCount).joinToString(" ") { column ->
                                textOf(component.prepareRenderer(component.getCellRenderer(row, column), row, column), rendered = true)
                            }
                        }

                    is JComboBox<*> -> comboText(component)

                    else -> null
                }
            }.filter { it.isNotBlank() }
            .joinToString("\n")

    private fun treeRow(
        tree: JTree,
        row: Int,
    ): String {
        val path = tree.getPathForRow(row) ?: return ""
        val shown =
            tree.cellRenderer.getTreeCellRendererComponent(tree, path.lastPathComponent, false, tree.isExpanded(row), false, row, false)
        return textOf(shown, rendered = true).ifBlank { path.lastPathComponent.toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun listRow(
        list: JList<*>,
        index: Int,
    ): String {
        val value = list.model.getElementAt(index)
        val renderer = (list as JList<Any?>).cellRenderer ?: return value.toString()
        return textOf(renderer.getListCellRendererComponent(list, value, index, false, false), rendered = true).ifBlank { value.toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun comboText(combo: JComboBox<*>): String? {
        val item = combo.selectedItem ?: return null
        val renderer = (combo as JComboBox<Any?>).renderer ?: return item.toString()
        return textOf(
            renderer.getListCellRendererComponent(JList<Any?>(), item, -1, false, false),
            rendered = true,
        ).ifBlank { item.toString() }
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
                            ?: descendants(window).filterIsInstance<JTextComponent>().firstOrNull { it.isShowing && it.isEditable }
                    }
            }
        checkNotNull(field) { "No dialog with a text field" }
        onEdt(ModalityState.stateForComponent(field)) {
            when (field) {
                is EditorComponentImpl -> {
                    WriteCommandAction.runWriteCommandAction(project(), "Tour fill", null, { field.editor.document.setText(text) })
                    field.editor.caretModel.moveToOffset(text.length)
                }

                is JTextComponent -> field.text = text
            }
        }
    }

    /**
     * How a rename that Shift+F6 started goes on: `dialog` once its dialog shows, `inplace` once the selected editor
     * renames the name where it is, or empty while neither has started.
     */
    @JvmStatic
    fun renaming(): String =
        anyEdt {
            when {
                Window.getWindows().any { it is JDialog && it.isShowing } -> "dialog"

                FileEditorManager
                    .getInstance(
                        project(),
                    ).selectedTextEditor
                    ?.let { TemplateManagerImpl.getTemplateState(it) } != null -> "inplace"

                else -> ""
            }
        }

    /**
     * Sets the field that [label] names in the newest dialog or the Settings page that shows, as typing or choosing it
     * does: a check box or toggle by its own text, checked when [value] is `true`; or the text field, combo box or
     * spinner that a label with that text labels, or that follows it, to [value], a combo box's item by the text it
     * shows. Answers what the field had.
     */
    @JvmStatic
    fun form(
        label: String,
        value: String,
    ): String {
        val (field, window) =
            anyEdt {
                val windows = Window.getWindows().filter { it.isShowing && it !== frame() }.reversed() + frame()
                windows.firstNotNullOfOrNull { window -> labeled(window, label)?.let { it to window } }
            } ?: error("No field \"$label\" shows")
        return onEdt(ModalityState.stateForComponent(window)) {
            when (field) {
                is JToggleButton -> {
                    val was = field.isSelected.toString()
                    if (field.isSelected != value.toBoolean()) field.doClick()
                    was
                }

                is EditorComponentImpl -> {
                    val was = field.editor.document.text
                    WriteCommandAction.runWriteCommandAction(project(), "Tour form", null, { field.editor.document.setText(value) })
                    was
                }

                is JTextComponent -> field.text.also { field.text = value }

                is JSpinner -> field.value.toString().also { field.value = value.toInt() }

                is JComboBox<*> -> {
                    val was = comboText(field).orEmpty()
                    val index =
                        (0 until field.itemCount).firstOrNull { index ->
                            val item = field.getItemAt(index)
                            item.toString() == value || itemText(field, item) == value
                        }
                            ?: error(
                                "No item $value in \"$label\": ${(0 until field.itemCount).map { itemText(field, field.getItemAt(it)) }}",
                            )
                    field.selectedIndex = index
                    was
                }

                else -> error("\"$label\" is a ${field.javaClass.name}")
            }
        }
    }

    /** What the field that [label] names shows, as [form] finds it. */
    @JvmStatic
    fun formValue(label: String): String =
        anyEdt {
            val windows = Window.getWindows().filter { it.isShowing && it !== frame() }.reversed() + frame()
            val field = windows.firstNotNullOfOrNull { labeled(it, label) } ?: error("No field \"$label\" shows")
            when (field) {
                is JToggleButton -> field.isSelected.toString()
                is JComboBox<*> -> comboText(field).orEmpty()
                is JSpinner -> field.value.toString()
                else -> textOf(field, rendered = true)
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun itemText(
        combo: JComboBox<*>,
        item: Any?,
    ): String {
        val renderer = (combo as JComboBox<Any?>).renderer ?: return item.toString()
        return textOf(
            renderer.getListCellRendererComponent(JList<Any?>(), item, -1, false, false),
            rendered = true,
        ).ifBlank { item.toString() }
    }

    /** The input that [label] names in [window]: a toggle with that text, or the field its label labels or that follows the label. */
    private fun labeled(
        window: Window,
        label: String,
    ): Component? {
        val components = descendants(window).filter { it.isShowing }.toList()
        components.filterIsInstance<JToggleButton>().firstOrNull { it.text?.replace("_", "")?.trim() == label }?.let { return it }
        val named =
            components.filterIsInstance<JLabel>().firstOrNull {
                it.text
                    ?.let(::stripHtml)
                    ?.trim()
                    ?.removeSuffix(":") ==
                    label.removeSuffix(":")
            }
        named ?: return null
        named.labelFor?.let { target -> return input(target) ?: target }
        // a label without labelFor: the input after it in its container, as a form's row lays it out
        val siblings =
            named.parent
                ?.components
                ?.toList()
                .orEmpty()
        return siblings.drop(siblings.indexOf(named) + 1).firstNotNullOfOrNull(::input)
    }

    private fun input(component: Component): Component? =
        descendants(component).firstOrNull {
            it.isShowing && (it is JTextComponent || it is JComboBox<*> || it is JSpinner || it is EditorComponentImpl)
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

    /** Selects the tab named [tab] of the tool window [id], such as the Problems window's Project Errors. */
    @JvmStatic
    fun toolWindowTab(
        id: String,
        tab: String,
    ): Unit =
        onEdt {
            val window = checkNotNull(ToolWindowManager.getInstance(project()).getToolWindow(id)) { "No tool window $id" }
            window.show()
            val contents = window.contentManager.contents
            val content =
                checkNotNull(contents.firstOrNull { it.displayName?.contains(tab, ignoreCase = true) == true }) {
                    "No tab $tab in $id: ${contents.map { it.displayName }}"
                }
            window.contentManager.setSelectedContent(content, true)
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
        val at = anyEdt { center(button) }
        logAction("click", "click: $text", at)
        if (visible) robotClick(at) else later(ModalityState.stateForComponent(button)) { button.doClick() }
    }

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    /** Chooses the row of the showing list popup, such as Show Intention Actions, that contains [text]. */
    @JvmStatic
    fun choose(text: String): Boolean {
        logAction("key", "Enter: $text")
        return onEdt { PopupChoices.chooseListRow(text) }
    }

    /** Chooses [label] in the showing chooser, such as the language adapter's, as Enter on it does; false if none lists it. */
    @JvmStatic
    fun pick(label: String): Boolean = onEdt { AdapterChooserUi.pick(label) }

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

    /** Turns the project's Actions on Save | Reformat code on or off, for every file type; answers whether it was on. */
    @JvmStatic
    fun formatOnSave(on: Boolean): Boolean =
        onEdt {
            val options = FormatOnSaveOptions.getInstance(project())
            options.isRunOnSaveEnabled.also { options.isRunOnSaveEnabled = on }
        }

    /**
     * Sets the LSP4IJ trace of the Ecstasy language server, `off`, `messages` or `verbose`, as the Language Servers
     * window's Trace does, for the messages from now on; answers the one it had.
     */
    @JvmStatic
    fun serverTrace(value: String): String =
        onEdt {
            val settings = ProjectLanguageServerSettings.getInstance(project())
            val current = settings.getLanguageServerSettings(SERVER_ID)
            val was = current?.serverTrace?.name ?: "off"
            val updated = current ?: LanguageServerDefinitionSettings()
            updated.setServerTrace(ServerTrace.valueOf(value))
            settings.updateSettings(SERVER_ID, updated)
            was
        }

    /**
     * Selects the Ecstasy language server's process in the Language Servers window and performs the window's action
     * [id] on it, as its context menu does, such as `lsp.console.explorer.restart`.
     */
    @JvmStatic
    fun serverAction(id: String) {
        val action = checkNotNull(ActionManager.getInstance().getAction(id)) { "No action $id" }
        val tree =
            onEdt {
                val window =
                    checkNotNull(
                        ToolWindowManager.getInstance(project()).getToolWindow("Language Servers"),
                    ) { "No Language Servers window" }
                window.show()
                val tree =
                    checkNotNull(descendants(window.component).filterIsInstance<JTree>().firstOrNull { it.isShowing }) { "No server tree" }
                // the process's row is the one under the server's
                val server =
                    (0 until tree.rowCount).firstOrNull { "Ecstasy" in treeRow(tree, it) } ?: error("No Ecstasy server in ${textOf(tree)}")
                tree.expandRow(server)
                tree.setSelectionRow(server + 1)
                tree
            }
        later { perform(action, DataManager.getInstance().getDataContext(tree), ActionPlaces.UNKNOWN) }
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
            // a real click needs the line on the screen, below the sticky lines that cover the editor's top
            scrollNow(editor) { scrollTo(LogicalPosition(line, 0), ScrollType.CENTER) }
            val action = checkNotNull(renderer.clickAction) { "The gutter icon \"$tooltip\" has no click action" }
            val gutter = (editor as EditorEx).gutterComponentEx

            fun icon() =
                Point(
                    gutter.locationOnScreen.x + gutter.lineMarkerAreaOffset + gutter.iconsAreaWidth / 2,
                    gutter.locationOnScreen.y + editor.logicalPositionToXY(LogicalPosition(line, 0)).y + editor.lineHeight / 2,
                )
            val at = icon()
            logAction("click", "click: $tooltip", at)
            if (visible) {
                // aimed again as the pointer gets there: a code lens that comes meanwhile moves the line down
                Thread { robotClick(at) { onEdt(::icon) } }.start()
            } else {
                perform(action, DataManager.getInstance().getDataContext(editor.contentComponent), ActionPlaces.EDITOR_GUTTER)
            }
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
            breakpoint = onEdt { lineBreakpoint(file, line) }
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

    private fun lineBreakpoint(
        file: VirtualFile,
        line: Int,
    ): XLineBreakpoint<*>? =
        XDebuggerManager
            .getInstance(project())
            .breakpointManager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .firstOrNull { it.fileUrl == file.url && it.line == line }

    /** Opens the editor of the breakpoint on the selected editor's zero-based [line], the balloon a right-click on it shows. */
    @JvmStatic
    fun editBreakpoint(line: Int): Unit =
        onEdt {
            val editor = editor()
            val breakpoint = checkNotNull(lineBreakpoint(checkNotNull(editor.virtualFile), line)) { "No breakpoint on line $line" }
            val gutter = (editor as EditorEx).gutterComponentEx
            val y = editor.logicalPositionToXY(LogicalPosition(line, 0)).y + editor.lineHeight / 2
            DebuggerUIUtil.showXBreakpointEditorBalloon(project(), Point(gutter.width / 2, y), gutter, false, breakpoint)
        }

    /** The line breakpoints' conditions, as `file:line condition`, one a line; a breakpoint without one shows none. */
    @JvmStatic
    fun breakpointConditions(): String =
        onEdt {
            XDebuggerManager
                .getInstance(project())
                .breakpointManager
                .allBreakpoints
                .filterIsInstance<XLineBreakpoint<*>>()
                .joinToString("\n") { "${it.shortFilePath}:${it.line + 1} ${it.conditionExpression?.expression.orEmpty()}" }
        }

    /** What Undo would undo in the selected editor, as its menu item names it, or `nothing`. */
    @JvmStatic
    fun undoable(): String =
        onEdt {
            val editor = FileEditorManager.getInstance(project()).selectedEditor ?: return@onEdt "no editor"
            val undo = UndoManager.getInstance(project())
            if (undo.isUndoAvailable(editor)) undo.getUndoActionNameAndDescription(editor).first else "nothing"
        }

    /**
     * The build mode that Build Project uses, and each module's source roots and whether it holds Ecstasy sources, which
     * decide whether the embedded build takes it from Gradle's.
     */
    @JvmStatic
    fun buildModules(): String =
        onEdt {
            val modules =
                ModuleManager.getInstance(project()).modules.joinToString("; ") { module ->
                    val roots = ModuleRootManager.getInstance(module).sourceRoots.joinToString(",") { it.name }
                    val ecstasy = FileTypeIndex.containsFileOfType(XtcFileType, module.moduleContentScope)
                    "${module.name} roots=[$roots] ecstasy=$ecstasy"
                }
            "mode=${XtcBuildSettings.effective(project())} $modules"
        }

    /** Removes every line breakpoint. */
    @JvmStatic
    fun clearBreakpoints(): Unit =
        onEdt {
            val breakpoints = XDebuggerManager.getInstance(project()).breakpointManager
            breakpoints.allBreakpoints.filterIsInstance<XLineBreakpoint<*>>().forEach(breakpoints::removeBreakpoint)
        }

    /**
     * The current debug session: whether there is one, whether it is `suspended`, and where, as `file` and `line`
     * (1-based) and `path`; once it has ended, its process's `exitCode`.
     */
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
                        addProperty("path", position.file.path)
                        addProperty("line", position.line + 1)
                    }
                    runCatching { session?.debugProcess?.processHandler }
                        .getOrNull()
                        ?.takeIf { it.isProcessTerminated }
                        ?.exitCode
                        ?.let { addProperty("exitCode", it) }
                }.toString()
        }

    /** The frames of the current debug session's paused fiber, each with its `name`, `file` and `line` (1-based), as the Frames view lists them. */
    @JvmStatic
    fun frames(): String =
        session()
            .suspendContext
            ?.activeExecutionStack
            ?.let { DebugSessionUi.frames(it).toString() } ?: "[]"

    /** The current debug session's threads, its program's fibers, each with its `name` and `frames`, as the Frames view's thread list has them. */
    @JvmStatic
    fun threads(): String = DebugSessionUi.threads(session())

    /**
     * The variables of the current debug session's paused frame, its Locals, or the fields or elements of the value at
     * the `/`-separated [path]: each with its `name`, `value`, `type` and whether it `expands`.
     */
    @JvmStatic
    fun variables(path: String): String = DebugSessionUi.variables(session(), path)

    /** Sets the value at the `/`-separated [path] of the paused frame to [expression], as Set Value does: "" once set, or the error. */
    @JvmStatic
    fun setValue(
        path: String,
        expression: String,
    ): String = DebugSessionUi.setValue(session(), path, expression)

    /**
     * Evaluates [expression] in the paused frame as the evaluation's [origin] asks, such as `DIALOG` for Evaluate
     * Expression or `EDITOR` for a hover, or as a watch when it is empty: its `value`, `type`, or `error`.
     */
    @JvmStatic
    fun evaluate(
        expression: String,
        origin: String,
    ): String = DebugSessionUi.evaluate(session(), expression, origin.takeIf { it.isNotEmpty() }?.let(XEvaluationOrigin::valueOf))

    /**
     * How Quick Evaluate and a hover stand at the selected editor's caret, for a failure's message: the expression the
     * paused frame's evaluator finds there, which a value hint shows, and whether the adapter evaluates for hovers,
     * without which LSP4IJ finds none.
     */
    @JvmStatic
    fun quickEvaluateState(): String {
        val frame = session().currentStackFrame ?: return "no frame is selected"
        val evaluator = frame.evaluator ?: return "the frame has no evaluator"
        val (document, offset) = onEdt { editor().let { it.document to it.caretModel.offset } }
        val found =
            runCatching {
                evaluator
                    .getExpressionInfoAtOffsetAsync(
                        project(),
                        document,
                        offset,
                        true,
                    ).blockingGet(HANG_SECONDS.toInt(), TimeUnit.SECONDS)
            }.fold({ info -> info?.let { "\"${it.expressionText ?: document.getText(it.textRange)}\" at ${it.textRange}" } ?: "none" }) {
                "failed: $it"
            }
        val hovers = (frame as? DAPStackFrame)?.client?.isSupportsEvaluateForHovers
        return "the expression at offset $offset: $found; the adapter evaluates for hovers: $hovers; ${evaluator.javaClass.simpleName}"
    }

    /** The calls that Smart Step Into offers on the paused line, as its handler computes them. */
    @JvmStatic
    fun smartTargets(): String = DebugSessionUi.smartStepTargets(session())

    /** The texts of the editor hints that show, such as the one where an exception stopped the program. */
    @JvmStatic
    fun hints(): String = DebugSessionUi.hintTexts()

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

    /** The text of the current debug session's console, as its Console tab shows it. */
    @JvmStatic
    fun debugConsole(): String =
        onEdt {
            val console = XDebuggerManager.getInstance(project()).currentSession?.consoleView as? ConsoleViewImpl
            console?.component
            console?.flushDeferredText()
            console?.text.orEmpty()
        }

    /** The current debug session's process handler and the stream that a line typed in its console goes to, by class. */
    @JvmStatic
    fun debugInput(): String =
        onEdt {
            val handler =
                XDebuggerManager
                    .getInstance(project())
                    .currentSession
                    ?.debugProcess
                    ?.processHandler
            "${handler?.javaClass?.name} input=${handler?.processInput?.javaClass?.name}"
        }

    /** Whether the selected run, such as a test run, has ended. */
    @JvmStatic
    fun runEnded(): Boolean =
        onEdt {
            // every run's, as the selected content is the last active window's, which can be the Debug window's
            RunContentManager.getInstance(project()).allDescriptors.all { it.processHandler?.isProcessTerminated != false }
        }

    /**
     * The runs that the Run and Debug windows show: each one's execution ID, name and process, which each new run,
     * rerun or test run changes, so that a step waits for its own run rather than reading the one before. It reads them
     * all, as the selected content is the last active window's, which can be the Debug window while a test runs.
     */
    @JvmStatic
    fun runId(): String =
        onEdt {
            RunContentManager
                .getInstance(project())
                .allDescriptors
                .joinToString("; ") { "${it.executionId} ${it.displayName} ${System.identityHashCode(it.processHandler)}" }
        }

    /** The exit code of the selected run's process once it has ended, or empty while it runs. */
    @JvmStatic
    fun exitCode(): String =
        onEdt {
            RunContentManager
                .getInstance(project())
                .selectedContent
                ?.processHandler
                ?.takeIf { it.isProcessTerminated }
                ?.exitCode
                ?.toString()
                .orEmpty()
        }

    /** Sends [text] to the program of the selected run, as its process's input, as Send EOF and a pipe do. */
    @JvmStatic
    fun input(text: String) {
        val handler =
            checkNotNull(onEdt { RunContentManager.getInstance(project()).selectedContent?.processHandler }) { "No run is selected" }
        val input = checkNotNull(handler.processInput) { "The run takes no input" }
        input.write(text.toByteArray())
        input.flush()
    }

    /**
     * Types [text] into the console of the selected run, or with [debug] the current debug session's, as the keyboard
     * does there: the console shows it as input, and a line goes to the program once it ends with a newline.
     */
    @JvmStatic
    fun typeInConsole(
        text: String,
        debug: Boolean,
    ): Unit =
        onEdt {
            val console =
                if (debug) {
                    XDebuggerManager.getInstance(project()).currentSession?.consoleView
                } else {
                    RunContentManager.getInstance(project()).selectedContent?.executionConsole
                }
            val view = checkNotNull(console as? ConsoleViewImpl) { "No console to type in: $console" }
            view.component
            val editor = checkNotNull(view.editor) { "The console has no editor" }
            IdeFocusManager.getInstance(project()).requestFocus(editor.contentComponent, true)
            view.type(editor, text)
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
    fun runWith(value: String): String = configure("runWith", value)

    /**
     * Sets the selected Ecstasy run configuration's [field] to [value], as its editor in Run | Edit Configurations
     * does: `runWith`, an XtcRunMode name, `moduleArguments`, `stopOnEntry` or `evaluationRunsProgram`. Answers the
     * configuration's name.
     */
    @JvmStatic
    fun configure(
        field: String,
        value: String,
    ): String =
        onEdt {
            val settings = checkNotNull(RunManager.getInstance(project()).selectedConfiguration) { "No run configuration is selected" }
            val configuration =
                settings.configuration as? XtcRunConfiguration ?: error("${settings.name} is not an Ecstasy run configuration")
            when (field) {
                "runWith" -> configuration.runWith = XtcRunMode.valueOf(value)
                "moduleArguments" -> configuration.moduleArguments = value
                "stopOnEntry" -> configuration.stopOnEntry = value.toBoolean()
                "evaluationRunsProgram" -> configuration.evaluationRunsProgram = value.toBoolean()
                else -> error("No field $field")
            }
            settings.name
        }

    /** Selects the run configuration [name], as the main toolbar's chooser does, and answers the one it had. */
    @JvmStatic
    fun selectConfiguration(name: String): String =
        onEdt {
            val runManager = RunManager.getInstance(project())
            val was = runManager.selectedConfiguration?.name.orEmpty()
            runManager.selectedConfiguration = checkNotNull(runManager.findConfigurationByName(name)) { "No run configuration $name" }
            was
        }

    /**
     * Selects the Ecstasy run configuration [name], made first, as the module file's Run icon makes it, for the
     * module [module] whose file is at [path], when the project has none of that name; answers the configuration that
     * was selected.
     */
    @JvmStatic
    fun runConfiguration(
        name: String,
        module: String,
        path: String,
    ): String =
        onEdt {
            val runManager = RunManager.getInstance(project())
            val was = runManager.selectedConfiguration?.name.orEmpty()
            val settings =
                runManager.findConfigurationByName(name)
                    ?: runManager
                        .createConfiguration(
                            name,
                            ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first(),
                        ).also { made ->
                            (made.configuration as XtcRunConfiguration).apply {
                                moduleName = module
                                moduleUri = Path.of(path).toUri().toString()
                            }
                            runManager.addConfiguration(made)
                        }
            runManager.selectedConfiguration = settings
            was
        }

    /**
     * Makes the Ecstasy Attach configuration [name], which attaches to the program that listens on [port] on this
     * machine, as Run | Edit Configurations | + | Ecstasy Attach does, and selects it; answers the configuration that
     * was selected.
     */
    @JvmStatic
    fun attachConfiguration(
        name: String,
        port: Int,
    ): String =
        onEdt {
            val runManager = RunManager.getInstance(project())
            val was = runManager.selectedConfiguration?.name.orEmpty()
            val factory = ConfigurationTypeUtil.findConfigurationType(XtcAttachConfigurationType::class.java).configurationFactories.first()
            val settings =
                runManager.findConfigurationByName(name) ?: runManager.createConfiguration(name, factory).also(runManager::addConfiguration)
            (settings.configuration as XtcAttachConfiguration).port = port
            runManager.selectedConfiguration = settings
            was
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
