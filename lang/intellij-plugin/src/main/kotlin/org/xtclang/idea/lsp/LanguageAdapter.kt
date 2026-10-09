package org.xtclang.idea.lsp

/** The bundled default remains available for development builds that choose their own backend. */
internal enum class LanguageAdapter(
    val setting: String,
    private val label: String,
) {
    DEFAULT("default", "Bundled default"),
    COMPILER("compiler", "Ecstasy Compiler"),
    TREE_SITTER("treesitter", "Tree-sitter"),
    ;

    fun launchArguments(): List<String> = if (this == DEFAULT) emptyList() else listOf("-Dxtc.lsp.adapter=$setting")

    override fun toString(): String = label

    companion object {
        fun fromSetting(setting: String): LanguageAdapter =
            requireNotNull(entries.find { it.setting == setting }) {
                "Language adapter must be default, compiler or treesitter"
            }
    }
}
