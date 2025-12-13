/**
 * A `ConditionalExecution` extension can determine at execution time whether tests or test fixtures
 * should be executed or should be skipped.
 */
interface ConditionalExecution
        extends Extension {
    /**
     * Determine whether the test or fixture represented by the `ExecutionContext` should be
     * skipped.
     *
     * @param context  the `ExecutionContext` for the current test or fixture
     *
     * @return True iff the test or fixture should be skipped or `False` if execution should proceed
     * @return if the test should be skipped an optional reason message
     */
    conditional String? skipExecution(ExecutionContext context);
}
