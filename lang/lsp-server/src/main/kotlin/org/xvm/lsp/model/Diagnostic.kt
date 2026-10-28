package org.xvm.lsp.model

/** Immutable diagnostic (error, warning, hint). */
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
        ;

        companion object
    }

    companion object {
        fun error(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.ERROR, message, source = "xtc")

        fun warning(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.WARNING, message, source = "xtc")

        fun info(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.INFORMATION, message, source = "xtc")
    }
}
