package org.xvm.lsp.model

/**
 * Immutable source location.
 */
data class Location(
    val uri: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
) {
    init {
        require(startLine >= 0) { "startLine must be non-negative" }
        require(startColumn >= 0) { "startColumn must be non-negative" }
        require(endLine >= 0) { "endLine must be non-negative" }
        require(endColumn >= 0) { "endColumn must be non-negative" }
    }

    companion object {
        fun of(uri: String, line: Int, column: Int): Location =
            Location(uri, line, column, line, column)

        fun ofLine(uri: String, line: Int): Location =
            Location(uri, line, 0, line, Int.MAX_VALUE)
    }
}
