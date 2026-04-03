package org.xvm.lsp.adapter

/**
 * Formatting configuration for XTC source files.
 *
 * Resolution order (highest priority first):
 * 1. `xtc-format.toml` in the project tree (Phase 3 — not yet implemented)
 * 2. LSP `FormattingOptions` from the editor (tabSize / insertSpaces)
 * 3. XTC defaults (4-space indent, 8-space continuation, no tabs)
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
         * Currently returns LSP-derived config (or defaults). Phase 3 will add
         * config file discovery (walk up from [fileUri] to find `xtc-format.toml`).
         */
        fun resolve(
            fileUri: String,
            lspOptions: XtcCompilerAdapter.FormattingOptions,
        ): XtcFormattingConfig {
            // Phase 3: check for xtc-format.toml before falling back to LSP options.
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
