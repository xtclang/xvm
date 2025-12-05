/**
 * The extensions package provides classes for extending the behavior of xUnit.
 */
package extensions {

    /**
     * An class that determines whether a test should be skipped.
     */
    interface TestExecutionPredicate
            extends Const {
        /**
         * Returns whether a test should be skipped.
         *
         * @return `True` if the test should be skipped, otherwise `False`
         * @return the reason the test should be skipped
         */
        conditional String shouldSkip(ExecutionContext context);
    }

    /**
     * A provider of `ExtensionProvider` instances.
     */
    interface ExtensionProviderProvider {
        /**
         * @return the `ExtensionProvider` instances this provider provides.
         */
        ExtensionProvider[] getExtensionProviders() = [];
    }
}