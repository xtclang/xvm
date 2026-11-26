package org.xtclang.idea.lsp

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPCallHierarchyBrowser
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPCallHierarchyIncomingCallsTreeStructure
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPCallHierarchyOutgoingCallsTreeStructure
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPCallHierarchyProvider

/**
 * The Call Hierarchy of an Ecstasy file, LSP4IJ's, whose callers and callees IntelliJ computes again when a write action
 * comes while it waits for them.
 *
 * TODO LSP4IJ: UP88 — LSP4IJ 0.30.1's call hierarchy tree structures wait for the server's answer in the read action
 *  of IntelliJ's hierarchy tree, and when IntelliJ cancels that read action for a write action, they cancel the
 *  request and return no children instead of throwing the cancellation. IntelliJ restarts only a read action that ends
 *  canceled, and keeps the children a node's computation returns: the node shows no callers until the Hierarchy window
 *  is refreshed. These structures throw the cancellation that LSP4IJ caught, so that IntelliJ computes the node's
 *  children again once the write action is done, with a new request.
 */
class XtcCallHierarchyProvider : LSPCallHierarchyProvider() {
    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser = XtcCallHierarchyBrowser(target)

    /** LSP4IJ's browser with the structures below; the playbook finds the Hierarchy window's browser by this name. */
    private class XtcCallHierarchyBrowser(
        target: PsiElement,
    ) : LSPCallHierarchyBrowser(target) {
        override fun createHierarchyTreeStructure(
            type: String,
            psiElement: PsiElement,
        ): HierarchyTreeStructure? =
            when (type) {
                CallHierarchyBrowserBase.getCallerType() -> Callers(myProject, psiElement)
                CallHierarchyBrowserBase.getCalleeType() -> Callees(myProject, psiElement)
                else -> null
            }
    }

    /** The callers of [element], as LSP4IJ's structure gives them, and computed again when a write action interrupts it. */
    internal class Callers(
        project: Project,
        element: PsiElement,
    ) : LSPCallHierarchyIncomingCallsTreeStructure(project, element) {
        override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> =
            super.buildChildren(descriptor).also { ProgressManager.checkCanceled() }
    }

    /** The callees of [element], as LSP4IJ's structure gives them, and computed again when a write action interrupts it. */
    internal class Callees(
        project: Project,
        element: PsiElement,
    ) : LSPCallHierarchyOutgoingCallsTreeStructure(project, element) {
        override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> =
            super.buildChildren(descriptor).also { ProgressManager.checkCanceled() }
    }
}
