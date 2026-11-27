package org.xvm.lsp.adapter

/**
 * Formatting configuration for Ecstasy source files.
 *
 * Resolution order (highest priority first):
 * 1. The file's `.editorconfig` files, which [EditorConfig] reads at each formatting request
 * 2. Editor config from `workspace/configuration` (IntelliJ Code Style settings, VS Code's `xtc.formatting`)
 * 3. LSP `FormattingOptions` from the editor (tabSize / insertSpaces)
 * 4. Ecstasy defaults (4-space indent, 8-space continuation, no tabs)
 */
data class FormattingConfig(
    val indentSize: Int = 4,
    val continuationIndentSize: Int = 8,
    val insertSpaces: Boolean = true,
    val maxLineWidth: Int = 120,
    /** Whether formatting refills a doc or block comment's paragraphs that run past [maxLineWidth]. */
    val wrapComments: Boolean = false,
) {
    companion object {
        /** XTC's opinionated defaults, matching lib_ecstasy conventions. */
        val DEFAULT: FormattingConfig = FormattingConfig()

        /**
         * Resolve the effective formatting config from settings supplied by the caller.
         *
         * Priority: editor config > LSP options > defaults. This function performs no file lookup;
         * [EditorConfig.formatting] sets a file's `.editorconfig` settings over its result.
         *
         * @param lspOptions the LSP FormattingOptions from the current request
         * @param editorConfig editor-provided config from `workspace/configuration`, or null
         */
        fun resolve(
            lspOptions: FormattingOptions,
            editorConfig: FormattingConfig? = null,
        ): FormattingConfig {
            if (editorConfig != null) return editorConfig
            return fromLspOptions(lspOptions)
        }

        /** Create from LSP FormattingOptions (editor fallback). */
        fun fromLspOptions(options: FormattingOptions): FormattingConfig =
            DEFAULT.copy(
                indentSize = if (options.insertSpaces) options.tabSize else DEFAULT.indentSize,
                insertSpaces = options.insertSpaces,
            )
    }
}
