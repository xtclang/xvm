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

    /**
     * Codes for problems the language server reports itself, rather than codes the compiler assigns (such as
     * `COMPILER-43`). Clients, tests and the shared playbook match these exact strings.
     */
    object Codes {
        /** Analysis threw unexpectedly; the server log has the cause. */
        const val ANALYSIS_FAILED = "ANALYSIS-FAILED"

        /** The configured source graph cannot be used, for example because it is cyclic or overlapping. */
        const val SOURCE_GRAPH = "SOURCE-GRAPH"

        /** A configured module's source could not be read. */
        const val SOURCE_UNAVAILABLE = "SOURCE-UNAVAILABLE"

        /** A module this one imports failed, so this module was not compiled against it. */
        const val DEPENDENCY_FAILED = "DEPENDENCY-FAILED"

        /** A configured root declares a different module than its configured name. */
        const val PROJECT_MODULE = "PROJECT-MODULE"

        /** The editor sent changes the server could not apply, so its text no longer matches the editor's. */
        const val DOCUMENT_OUT_OF_SYNC = "DOCUMENT-OUT-OF-SYNC"
    }

    companion object {
        /**
         * The source of every diagnostic this server publishes, from the compiler or about it. Editors show it
         * next to the code, as in `Ecstasy(COMPILER-43)`; nothing matches on it.
         */
        const val SOURCE = "Ecstasy"

        fun error(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.ERROR, message, source = SOURCE)

        fun warning(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.WARNING, message, source = SOURCE)

        fun info(
            location: Location,
            message: String,
        ): Diagnostic = Diagnostic(location, Severity.INFORMATION, message, source = SOURCE)
    }
}
