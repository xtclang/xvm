package org.xtclang.idea.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.xtclang.idea.XtcIntelliJLanguage
import java.util.concurrent.CompletableFuture

/**
 * Custom LSP language client that bridges IntelliJ Code Style settings to the XTC LSP server.
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
     * Handle `workspace/configuration` requests from the LSP server.
     *
     * The server requests configuration sections after initialization. For each
     * [ConfigurationItem] with section `"xtc.formatting"`, we return the IntelliJ
     * Code Style settings for XTC. Unknown sections get `null`.
     */
    override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> {
        logger.info("workspace/configuration: ${params.items.map { it.section }}")
        val results = params.items.map { item -> resolveConfigSection(item) }
        return CompletableFuture.completedFuture(results)
    }

    private fun resolveConfigSection(item: ConfigurationItem): Any? =
        when (item.section) {
            FORMATTING_SECTION -> {
                readFormattingSettings()
            }

            else -> {
                logger.info("workspace/configuration: unknown section '${item.section}', returning null")
                null
            }
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
