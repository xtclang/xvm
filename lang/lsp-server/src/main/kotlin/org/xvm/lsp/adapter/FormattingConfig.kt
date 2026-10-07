package org.xvm.lsp.adapter

/**
 * Formatting configuration for Ecstasy source files.
 *
 * Resolution order (highest priority first):
 * 1. Editor config from `workspace/configuration` (IntelliJ Code Style settings)
 * 2. LSP `FormattingOptions` from the editor (tabSize / insertSpaces)
 * 3. Ecstasy defaults (4-space indent, 8-space continuation, no tabs)
 *
 * File-scoped configuration and project-local `xtc-format.toml` lookup are not implemented.
 */
data class FormattingConfig(
    val indentSize: Int = 4,
    val continuationIndentSize: Int = 8,
    val insertSpaces: Boolean = true,
    val maxLineWidth: Int = 120,
) {
    companion object {
        /** XTC's opinionated defaults, matching lib_ecstasy conventions. */
        val DEFAULT: FormattingConfig = FormattingConfig()

        /**
         * Resolve the effective formatting config from settings supplied by the caller.
         *
         * Priority: editor config > LSP options > defaults. This function performs no file lookup;
         * any file-scoped settings must be resolved by the caller before applying this precedence.
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
