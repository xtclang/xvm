package org.xvm.compiler2.parser;

/**
 * Severity level for diagnostics.
 */
public enum DiagnosticSeverity {
    /**
     * Hidden diagnostic (not shown to user).
     */
    HIDDEN,

    /**
     * Informational message.
     */
    INFO,

    /**
     * Warning - possible issue but compilation can continue.
     */
    WARNING,

    /**
     * Error - compilation cannot produce valid output.
     */
    ERROR;

    /**
     * @return true if this is an error
     */
    public boolean isError() {
        return this == ERROR;
    }

    /**
     * @return true if this is warning or error
     */
    public boolean isWarningOrError() {
        return this == WARNING || this == ERROR;
    }
}
