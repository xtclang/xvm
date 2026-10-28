package org.xvm.lsp.adapter

/** sRGB channels and unpremultiplied alpha, normalized to the LSP interval [0, 1]. */
data class ColorValue(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double,
) {
    init {
        require(listOf(red, green, blue, alpha).all { it.isFinite() && it in 0.0..1.0 })
    }
}

data class DocumentColor(
    val range: Range,
    val color: ColorValue,
)

/** One source spelling offered by a picker; application remains owned by the editor. */
data class ColorPresentation(
    val label: String,
    val textEdit: TextEdit,
)
