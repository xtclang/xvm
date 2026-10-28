package org.xtclang.idea.lsp

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.redhat.devtools.lsp4ij.features.signatureHelp.LSPParameterInfoHandler
import com.redhat.devtools.lsp4ij.features.signatureHelp.LSPSignatureHelperPsiElement

/** LSP4IJ's Parameter Info for Ecstasy files, which first sends the server the edits LSP4IJ still holds. */
class XtcParameterInfoHandler : LSPParameterInfoHandler() {
    // Ecstasy's lexer can use plain-text or TextMate PSI. Leave other languages to LSP4IJ.
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): LSPSignatureHelperPsiElement? {
        val file = context.file.virtualFile?.takeIf { it.extension == "x" } ?: return null
        // TODO LSP4IJ: UP73 — the typed "(" goes to the server before showParameterInfo asks for the signature
        sendHeldEdits(context.project, file)
        return super.findElementForParameterInfo(context)
    }

    override fun updateParameterInfo(
        element: LSPSignatureHelperPsiElement,
        context: UpdateParameterInfoContext,
    ) {
        // TODO LSP4IJ: UP73 — a typed "," or argument goes to the server before LSP4IJ asks for the signature again
        context.file.virtualFile
            ?.takeIf { it.extension == "x" }
            ?.let { sendHeldEdits(context.project, it) }
        super.updateParameterInfo(element, context)
    }
}
