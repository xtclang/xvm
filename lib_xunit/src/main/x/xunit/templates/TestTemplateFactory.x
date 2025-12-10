import extensions.ExecutionContext;

/**
 * A factory that produces instances of `TestTemplateContext` that will be used to execute a
 * templated test.
 */
interface TestTemplateFactory<TemplateType extends TestTemplate> {
    /**
     * @return `True` if the factory is enabled.
     */
    @RO Boolean enabled.get() = True;

    /**
     * Return the `TestTemplateContext`s to use to execute the templated test.
     *
     * The order of the `TestTemplateContext`s returned should be deterministic, so that repeated
     * calls return the same list. This allows test discovery `Selector`s to be used to run just one
     * of, or a sub-set of, the test iterations.
     *
     * @param context  the current `ExecutionContext`
     *
     * @return the `TestTemplateContext`s to use to execute the templated test
     */
    Iterable<TestTemplateContext> getContexts(ExecutionContext context);
}
