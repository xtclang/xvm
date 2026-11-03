package org.xtclang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.redhat.devtools.lsp4ij.LSPIJUtils
import org.xtclang.idea.XtcFileType
import org.xtclang.idea.lsp.ModuleTests
import org.xtclang.idea.lsp.ServerTest
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Run and Debug icons in the gutter of Ecstasy files, on the lines of their xunit tests and of the modules, packages
 * and classes that hold tests, from the tests the language server lists (`xtc/tests`). Ecstasy files have no PSI for
 * a line marker, so the icons are document line highlighters, as [RunGutterIcons]' are.
 */
internal object TestGutterIcons {
    private val placed = Key.create<List<RangeHighlighter>>("xtc.testGutterIcons")

    /** Marks the lines of [file]'s tests, and clears marks whose test is gone. */
    fun refresh(
        project: Project,
        file: VirtualFile,
    ) {
        if (project.isDisposed || file.fileType != XtcFileType || !file.isInLocalFileSystem) return
        XtcTestCatalog.refresh(project).thenAccept { modules ->
            ApplicationManager.getApplication().invokeLater({
                FileDocumentManager.getInstance().getCachedDocument(file)?.let { show(project, it, declaredIn(modules, file)) }
            }, project.disposed)
        }
    }

    /** A test or container of tests that [module] declares in the file being marked. */
    internal data class Declared(
        val module: ModuleTests,
        val test: ServerTest,
        val line: Int,
    )

    /** The tests and containers of [modules] whose declarations are in [file], each on its zero-based line. */
    internal fun declaredIn(
        modules: List<ModuleTests>,
        file: VirtualFile,
    ): List<Declared> {
        val files = HashMap<String, VirtualFile?>()

        fun all(test: ServerTest): List<ServerTest> = listOf(test) + test.children.flatMap(::all)
        return modules.flatMap { module ->
            all(module.tests).mapNotNull { test ->
                test.location
                    ?.takeIf { at -> files.getOrPut(at.uri) { LSPIJUtils.findResourceFor(at.uri) } == file }
                    ?.let { at -> Declared(module, test, at.startLine) }
            }
        }
    }

    private fun show(
        project: Project,
        document: Document,
        declared: List<Declared>,
    ) {
        val markup = DocumentMarkupModel.forDocument(document, project, true)
        document
            .getUserData(placed)
            .orEmpty()
            .filter { it.isValid }
            .forEach(markup::removeHighlighter)
        document.putUserData(
            placed,
            declared.filter { it.line < document.lineCount }.map { test ->
                markup.addLineHighlighter(test.line, HighlighterLayer.ADDITIONAL_SYNTAX, null).apply {
                    gutterIconRenderer = TestIcon(project, test)
                }
            },
        )
    }

    /** Runs or debugs a test, or the tests a container holds, from a popup with both. */
    private class TestIcon(
        private val project: Project,
        private val declared: Declared,
    ) : GutterIconRenderer() {
        private val name = XtcTestRuns.name(declared.module.module, declared.test)

        override fun getIcon(): Icon =
            if (declared.test.kind ==
                ServerTest.TEST
            ) {
                AllIcons.RunConfigurations.TestState.Run
            } else {
                AllIcons.RunConfigurations.TestState.Run_run
            }

        override fun getTooltipText(): String = name

        override fun isNavigateAction(): Boolean = true

        override fun getClickAction(): AnAction =
            object : DumbAwareAction() {
                override fun actionPerformed(event: AnActionEvent) {
                    val popup =
                        JBPopupFactory
                            .getInstance()
                            .createActionGroupPopup(
                                null,
                                popupMenuActions,
                                event.dataContext,
                                JBPopupFactory.ActionSelectionAid.MNEMONICS,
                                true,
                            )
                    (event.inputEvent as? MouseEvent)?.let { popup.show(RelativePoint(it)) }
                        ?: popup.showInBestPositionFor(event.dataContext)
                }
            }

        override fun getPopupMenuActions(): ActionGroup =
            DefaultActionGroup(
                action("Run '$name'", AllIcons.Actions.Execute, DefaultRunExecutor.getRunExecutorInstance()),
                action("Debug '$name'", AllIcons.Actions.StartDebugger, DefaultDebugExecutor.getDebugExecutorInstance()),
            )

        private fun action(
            text: String,
            icon: Icon,
            executor: Executor,
        ) = object : DumbAwareAction(text, null, icon) {
            override fun actionPerformed(event: AnActionEvent) =
                XtcTestRuns.start(project, declared.module.module, declared.module.uri, declared.test, executor)
        }

        override fun equals(other: Any?): Boolean = other is TestIcon && other.declared == declared

        override fun hashCode(): Int = declared.hashCode()
    }
}
