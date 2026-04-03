package org.xvm.lsp.adapter

/**
 * Formatting configuration for XTC source files.
 *
 * Resolution order (highest priority first):
 * 1. `xtc-format.toml` in the project tree (not yet implemented)
 * 2. Editor config from `workspace/configuration` (IntelliJ Code Style settings)
 * 3. LSP `FormattingOptions` from the editor (tabSize / insertSpaces)
 * 4. XTC defaults (4-space indent, 8-space continuation, no tabs)
 */
data class XtcFormattingConfig(
    val indentSize: Int = 4,
    val continuationIndentSize: Int = 8,
    val insertSpaces: Boolean = true,
    val maxLineWidth: Int = 120,
) {
    companion object {
        /** XTC's opinionated defaults, matching lib_ecstasy conventions. */
        val DEFAULT = XtcFormattingConfig()

        /**
         * Resolve the effective formatting config for a file.
         *
         * Priority: xtc-format.toml (future) > editor config > LSP options > defaults.
         *
         * @param fileUri the file being formatted (for future config file discovery)
         * @param lspOptions the LSP FormattingOptions from the current request
         * @param editorConfig editor-provided config from `workspace/configuration`, or null
         */
        fun resolve(
            fileUri: String,
            lspOptions: XtcCompilerAdapter.FormattingOptions,
            editorConfig: XtcFormattingConfig? = null,
        ): XtcFormattingConfig {
            // TODO Phase 4: check for xtc-format.toml before other sources.
            if (editorConfig != null) return editorConfig
            return fromLspOptions(lspOptions)
        }

        /**
         * Create from LSP FormattingOptions (editor fallback).
         */
        fun fromLspOptions(options: XtcCompilerAdapter.FormattingOptions): XtcFormattingConfig =
            DEFAULT.copy(
                indentSize = if (options.insertSpaces) options.tabSize else DEFAULT.indentSize,
                insertSpaces = options.insertSpaces,
            )
    }
}
