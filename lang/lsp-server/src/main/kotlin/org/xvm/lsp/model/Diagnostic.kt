package org.xvm.lsp.model

/**
 * Immutable diagnostic (error, warning, hint).
 */
data class Diagnostic(
    val location: Location,
    val severity: Severity,
    val message: String,
    val code: String? = null,
    val source: String? = null,
) {
    enum class Severity {
        ERROR,
        WARNING,
        INFORMATION,
        HINT,
    }

    companion object {
        fun error(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.ERROR, message, null, "xtc")

        fun warning(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.WARNING, message, null, "xtc")

        fun info(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.INFORMATION, message, null, "xtc")
    }
}
