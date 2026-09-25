package org.xtclang.idea.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import org.xtclang.idea.XtcIntelliJLanguage

/**
 * Bridges Ecstasy Code Style settings while preserving LSP4IJ's server configuration support.
 * Compiler source modules use LSP4IJ's server Configuration settings under `xtc.compiler`;
 * the base client owns section lookup, change notifications and listener disposal.
 *
 * When the LSP server sends a `workspace/configuration` request for section `"xtc.formatting"`,
 * this client reads the current IntelliJ Code Style settings for the Ecstasy language and
 * returns them as a JSON-compatible map. This implements Phase 3 of the formatting plan:
 * IntelliJ Code Style settings flow to the LSP server as a fallback when no `xtc-format.toml`
 * config file is present.
 *
 * Resolution chain (highest priority first):
 * 1. `xtc-format.toml` in the project tree (not yet implemented)
 * 2. IntelliJ Code Style settings (this client provides them)
 * 3. LSP `FormattingOptions` from the editor (tabSize / insertSpaces)
 * 4. XTC defaults (4-space indent, 8-space continuation, no tabs)
 */
class XtcLanguageClient(
    project: Project,
) : LanguageClientImpl(project) {
    /**
     * Specialize only formatting. Delegating other sections preserves configured compiler graphs,
     * null entries for unknown sections and the base client's asynchronous response ordering.
     */
    override fun findSettings(section: String?): Any? =
        when (section) {
            FORMATTING_SECTION -> readFormattingSettings()
            else -> super.findSettings(section)
        }

    private fun readFormattingSettings(): Map<String, Any> {
        val settings = CodeStyle.getProjectOrDefaultSettings(project)
        val commonSettings = settings.getCommonSettings(XtcIntelliJLanguage)
        val indentOptions = commonSettings.indentOptions

        val config =
            if (indentOptions != null) {
                mapOf(
                    "indentSize" to indentOptions.INDENT_SIZE,
                    "continuationIndentSize" to indentOptions.CONTINUATION_INDENT_SIZE,
                    "tabSize" to indentOptions.TAB_SIZE,
                    "insertSpaces" to !indentOptions.USE_TAB_CHARACTER,
                    "maxLineWidth" to commonSettings.RIGHT_MARGIN,
                )
            } else {
                mapOf(
                    "indentSize" to 4,
                    "continuationIndentSize" to 8,
                    "tabSize" to 4,
                    "insertSpaces" to true,
                    "maxLineWidth" to 120,
                )
            }
        logger.info("workspace/configuration: returning formatting config: $config")
        return config
    }

    companion object {
        private val logger = logger<XtcLanguageClient>()

        /** The configuration section name for XTC formatting settings. */
        const val FORMATTING_SECTION = "xtc.formatting"
    }
}
