package org.xtclang.idea

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.folding.LanguageFolding
import com.intellij.psi.PsiWhiteSpace
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewFactory
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * An Ecstasy file's PSI is a flat tree of its words, of the Ecstasy language, so that what IntelliJ looks up by
 * a file's language finds what plugin.xml registers for Ecstasy.
 */
class XtcPsiTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `an Ecstasy file's PSI is of the Ecstasy language`() {
        editor.open("Probe.x", "module Probe {\n    void run() {}\n}\n") { file ->
            assertThat(file).isInstanceOf(XtcPsiFile::class.java)
            assertThat(file.language).isEqualTo(XtcIntelliJLanguage)
            assertThat(file.fileType).isEqualTo(XtcFileType)
        }
    }

    @Test
    fun `the PSI's leaves are the file's words`() {
        val text = "module Probe {\n    void run() {}\n}\n"
        editor.open("Probe.x", text) { file ->
            assertThat(file.findElementAt(text.indexOf("run") + 1)?.text).isEqualTo("run")
            assertThat(file.findElementAt(text.indexOf("Probe"))?.textRange?.startOffset).isEqualTo(text.indexOf("Probe"))
            assertThat(file.findElementAt(text.indexOf("{"))?.text).isEqualTo("{")
            assertThat(file.findElementAt(text.indexOf("\n"))).isInstanceOf(PsiWhiteSpace::class.java)
            assertThat(file.text).isEqualTo(text)
        }
    }

    @Test
    fun `the Ecstasy live templates apply in an Ecstasy file by its language`() {
        val context = XtcTemplateContextType()
        assertThat(editor.open("Probe.x", "module Probe {}\n") { context.isInContext(TemplateActionContext.expanding(it, 0)) }).isTrue()
        assertThat(editor.open("notes.txt", "module Probe {}\n") { context.isInContext(TemplateActionContext.expanding(it, 0)) }).isFalse()
    }

    @Test
    fun `LSP4IJ's folding and structure view apply to the Ecstasy language`() {
        editor.open("Probe.x", "module Probe {}\n") { file ->
            assertThat(
                LanguageFolding.INSTANCE.allForLanguage(file.language),
            ).hasAtLeastOneElementOfType(LSPFoldingRangeBuilder::class.java)
            assertThat(LanguageStructureViewBuilder.getInstance().forLanguage(file.language))
                .isInstanceOf(LSPDocumentSymbolStructureViewFactory::class.java)
        }
    }
}
