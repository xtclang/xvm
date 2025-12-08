/**
 * An annotation applied to a module to indicate that it provides test engine extensions.
 */
annotation TestEngineExtender(ExtensionSupplier supplier)
        into module {

    /**
     * A function that returns test engine extensions.
     */
    typedef function EngineExtension[]() as ExtensionSupplier;

    /**
     * @returns the test engine extensions provided by the module.
     */
    EngineExtension[] getTestEngineExtensions() {
        return supplier();
    }
}