import extensions.Extension;

/**
 * An annotation applied to a module to indicate that it provides extensions to the test engine
 * prior to test discovery and execution.
 *
 * This annotation is generally expected to be applied to modules that provide additional XUnit
 * functionality and integrations rather than to application modules.
 */
annotation TestEngineExtender(ExtensionSupplier supplier)
        into module {

    /**
     * A function that returns test extensions.
     */
    typedef function Extension[]() as ExtensionSupplier;

    /**
     * @returns the extensions provided by the module.
     */
    Extension[] getTestEngineExtensions() {
        return supplier();
    }
}