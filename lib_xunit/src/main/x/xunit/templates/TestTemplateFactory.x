/**
 * A factory that produces instances of `TestTemplateContext` that will be used
 * to execute a templated test.
 */
interface TestTemplateFactory {
    /**
     * @return `True` if the factory is enabled.
     */
    @RO Boolean enabled.get() {
        return True;
    }

    /**
     * Return the `TestTemplateContext`s to use to execute the templated test.
     *
     * The order of the `TestTemplateContext`s returned should ideally be deterministic,
     * so that repeated calls return the same list, which allows test discovery `Selector`s
     * to be used to run just a sub-set of the test iterations.
     */
    Iterable<TestTemplateContext> getTemplates(ExecutionContext context);
}
