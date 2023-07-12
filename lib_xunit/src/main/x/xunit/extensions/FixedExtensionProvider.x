/**
 * An `ExtensionProvider` that provides a fixed `Extension` array.
 *
 * @param extensions  the fixed array of extensions to provide
 */
const FixedExtensionProvider(String name, Extension[] extensions)
        implements ExtensionProvider {

    /**
     * Create a `FixedExtensionProvider` that provides a single `Extension`.
     *
     * @param extension  the single `Extension` to provide
     */
    construct(String name, Extension extension) {
        construct FixedExtensionProvider(name, [extension]);
    }

    @Override
    Extension[] getExtensions(ExecutionContext context) {
        return extensions;
    }
}
