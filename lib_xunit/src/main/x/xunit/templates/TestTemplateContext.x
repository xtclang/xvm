import extensions.Extension;
import extensions.ResourceRegistry;

/**
 * A `TestTemplateContext` provides a context to use to execute tests in a `TestTemplate`.
 */
interface TestTemplateContext {
    /**
     * Returns the display name for the specified iteration of the `TestTemplate`.
     *
     * @param iteration  the iteration of the test to be executed
     *
     * @return  the display name to use for the specified iteration of the `TestTemplate`.
     */
    String getDisplayName(Int iteration);

    /**
     * Returns any additional `Extension`s for the specified iteration of the `TestTemplate`.
     *
     * @param iteration  the iteration of the test to be executed
     *
     * @return  any additional `Extension`s for the specified iteration of the `TestTemplate`.
     */
    Extension[] getExtensions(Int iteration) = [];

    /**
     * Returns any additional `ResourceRegistry.Resource`s for the specified iteration of the
     * `TestTemplate`.
     *
     * @param iteration  the iteration of the test to be executed
     *
     * @return  any additional `ResourceRegistry.Resource`s to register for the specified
     *          iteration of the `TestTemplate`.
     */
    ResourceRegistry.Resource[] getResources(Int iteration) = [];
}
