package org.xtclang.idea.dap

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPExpressionCodeFragment

/**
 * The debugger's expression editors as LSP4IJ makes them, but with a physical code fragment when IntelliJ asks for one,
 * as it does for an expression editor's document, such as the Debug window's expression field, Evaluate Expression's
 * and a logpoint's.
 *
 * TODO LSP4IJ: UP37 — LSP4IJ 0.21.0 ignores isPhysical and its fragment is never physical, which IntelliJ's daemon
 *  cannot highlight: with a debug session paused, the Debug window's expression field ends each daemon run with "no
 *  passes created", and the daemon starts again 300 ms later, as long as the session stays paused; an edited Evaluate
 *  Expression fails the Document/PSI check, and a logpoint's expression fails LocalInspectionsPass's. LSP4IJ #1763
 *  passes isPhysical through; drop this once a release has it.
 */
internal class PhysicalExpressionFragments(
    fileType: FileType?,
    debugProcess: DAPDebugProcess?,
) : DAPDebuggerEditorsProvider(fileType, debugProcess) {
    override fun createExpressionCodeFragment(
        project: Project,
        text: String,
        context: PsiElement?,
        isPhysical: Boolean,
    ): PsiFile {
        val fragment = super.createExpressionCodeFragment(project, text, context, isPhysical)
        if (!isPhysical || fragment !is DAPExpressionCodeFragment) return fragment
        return PhysicalFragment(text, fragment.fileType, fragment.language, debugProcess, project)
    }

    /** LSP4IJ's fragment, physical; a LightVirtualFile still keeps it off the language server. */
    private class PhysicalFragment(
        text: String,
        fileType: FileType,
        language: Language,
        debugProcess: DAPDebugProcess?,
        project: Project,
    ) : DAPExpressionCodeFragment(text, fileType, language, debugProcess, project) {
        override fun isPhysical(): Boolean = true
    }
}
