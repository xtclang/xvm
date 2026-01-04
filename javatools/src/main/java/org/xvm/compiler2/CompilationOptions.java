package org.xvm.compiler2;

/**
 * Immutable compilation options.
 * <p>
 * Use the builder to create options with custom values:
 * <pre>
 * CompilationOptions opts = CompilationOptions.builder()
 *     .warningsAsErrors(true)
 *     .debugInfo(true)
 *     .build();
 * </pre>
 */
public record CompilationOptions(
        boolean warningsAsErrors,
        boolean debugInfo,
        boolean verbose,
        boolean strictMode,
        int maxErrors
) {
    /**
     * Default options.
     */
    public static final CompilationOptions DEFAULT = new CompilationOptions(
            false,  // warningsAsErrors
            true,   // debugInfo
            false,  // verbose
            false,  // strictMode
            100     // maxErrors
    );

    /**
     * Create a builder with default values.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a builder initialized from these options.
     *
     * @return a new builder with current values
     */
    public Builder toBuilder() {
        return new Builder()
                .warningsAsErrors(warningsAsErrors)
                .debugInfo(debugInfo)
                .verbose(verbose)
                .strictMode(strictMode)
                .maxErrors(maxErrors);
    }

    /**
     * Builder for CompilationOptions.
     */
    public static class Builder {
        private boolean warningsAsErrors = DEFAULT.warningsAsErrors;
        private boolean debugInfo = DEFAULT.debugInfo;
        private boolean verbose = DEFAULT.verbose;
        private boolean strictMode = DEFAULT.strictMode;
        private int maxErrors = DEFAULT.maxErrors;

        /**
         * Treat warnings as errors.
         */
        public Builder warningsAsErrors(boolean value) {
            this.warningsAsErrors = value;
            return this;
        }

        /**
         * Include debug information in output.
         */
        public Builder debugInfo(boolean value) {
            this.debugInfo = value;
            return this;
        }

        /**
         * Enable verbose output.
         */
        public Builder verbose(boolean value) {
            this.verbose = value;
            return this;
        }

        /**
         * Enable strict mode (more rigorous checking).
         */
        public Builder strictMode(boolean value) {
            this.strictMode = value;
            return this;
        }

        /**
         * Maximum number of errors before aborting.
         */
        public Builder maxErrors(int value) {
            this.maxErrors = value;
            return this;
        }

        /**
         * Build the options.
         *
         * @return the immutable options
         */
        public CompilationOptions build() {
            return new CompilationOptions(
                    warningsAsErrors,
                    debugInfo,
                    verbose,
                    strictMode,
                    maxErrors
            );
        }
    }
}
