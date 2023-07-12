/**
 * A `TestTemplateContext` provides a context to use to execute
 * tests in a `TestTemplate`.
 */
interface TestTemplateContext {
    /**
     * Returns the information to use for the specified iteration of the `TestTemplate`.
     *
     * @param iteration  the iteration of the test to be executed
     *
     * @return  the display name to use for the specified iteration of the `TestTemplate`.
     * @return  any additional `Extension`s for the specified iteration of the `TestTemplate`.
     * @return  any additional `ResourceRegistry.Resource`s to register for the specified
     *          iteration of the `TestTemplate`.
     */
    (String, Extension[], ResourceRegistry.Resource[]) getTemplateInfo(Int iteration);
}
