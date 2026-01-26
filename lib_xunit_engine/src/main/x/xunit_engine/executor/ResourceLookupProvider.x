import xunit.extensions.ExecutionContext;
import xunit.extensions.ResourceLookupCallback;

/**
 * Implemented by the Service provides test resource lookups during test execution.
 *
 * The instance of this service can be injected into test extensions during test execution.
 */
interface ResourceLookupProvider
        extends Service {
    /**
     * The current test ExecutionContext or Null if there is no current context.
     */
    @RO ExecutionContext? context;

    /**
     * The current test ResourceLookupCallbacks.
     */
    @RO ResourceLookupCallback[] lookupCallbacks;

    /**
     * Sets the current test ExecutionContext and ResourceLookupCallbacks.
     *
     * @param context the current test ExecutionContext or Null if there is no current context.
     * @param callbacks the current test ResourceLookupCallbacks.
     */
    void setExecutionContext(ExecutionContext? context, ResourceLookupCallback[] callbacks);
}
