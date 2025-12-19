import extensions.AroundTestCallback;
import extensions.ExecutionContext;
import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.FixedExtensionProvider;

/**
 * An annotation that can be used to annotate a @Test method or function to provide custom
 * injectable values for the annotated test.
 *
 * @param injectables  the map of key value paris that can be injected into tests
 */
annotation TestInjectables(Map<String, String> injectables)
        implements ExtensionProviderProvider
        into MethodOrFunction {

    @Override
    ExtensionProvider[] getExtensionProviders()
            = super() + new FixedExtensionProvider(name, new Callback(injectables));

    /**
     * The `AroundTestCallback` callback this annotation registers.`
     */
    static const Callback(Map<String, String> injectables)
            implements AroundTestCallback {

        @Override
        @RO Boolean requiresTarget.get() = True;

        @Override
        void beforeTest(ExecutionContext context) {
            for (Map.Entry<String, String> entry : injectables.entries) {
                assert context.registry.register(entry.value, entry.key, Always);
            }
        }

        @Override
        void afterTest(ExecutionContext context) {
        }
    }
}
