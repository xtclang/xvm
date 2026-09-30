package org.xtclang.idea.lsp

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.redhat.devtools.lsp4ij.features.signatureHelp.LSPParameterInfoHandler
import com.redhat.devtools.lsp4ij.features.signatureHelp.LSPSignatureHelperPsiElement
import org.eclipse.lsp4j.SignatureInformation

/** Missing parameter metadata means no safe active slot, not an empty signature. */
class XtcParameterInfoHandler : LSPParameterInfoHandler() {
    // Ecstasy's lexer can use plain-text or TextMate PSI. Leave other languages to LSP4IJ.
    override fun findElementForParameterInfo(
        context: CreateParameterInfoContext
    ): LSPSignatureHelperPsiElement? =
        if (context.file.virtualFile?.extension == "x") super.findElementForParameterInfo(context)
        else null

    override fun updateUI(
        signature: SignatureInformation,
        context: ParameterInfoUIContext,
    ) {
        // TODO LSP4IJ: refresh rendered overload metadata after retrigger; the popup keeps original
        // SignatureInformation objects. Read current metadata for this overload when rendering.
        val current =
            (context.parameterOwner as? LSPSignatureHelperPsiElement)
                ?.activeSignatureHelp
                ?.signatures
                ?.singleOrNull { it.label == signature.label } ?: signature
        if (current.parameters.isNullOrEmpty()) {
            context.setupUIComponentPresentation(
                current.label,
                -1,
                -1,
                !context.isUIComponentEnabled,
                false,
                false,
                context.defaultParameterColor,
            )
        } else {
            // TODO LSP4IJ: honor per-overload activeParameter and omit highlighting without
            // metadata.
            // LSP permits each overload to override the top-level active parameter. LSP4IJ's
            // renderer otherwise reads only the shared context, which defaults to slot zero.
            super.updateUI(
                current,
                object : ParameterInfoUIContext by context {
                    override fun getCurrentParameterIndex(): Int =
                        current.activeParameter ?: context.currentParameterIndex
                },
            )
        }
    }
}
