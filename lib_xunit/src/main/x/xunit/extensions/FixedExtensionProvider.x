/**
 * An `ExtensionProvider` that provides a fixed `Extension` array.
 *
 * @param extensions  the fixed array of extensions to provide
 */
const FixedExtensionProvider(Extension[] extensions)
        implements ExtensionProvider {

    /**
     * Create a `FixedExtensionProvider` that provides a single `Extension`.
     *
     * @param extension  the single `Extension` to provide
     */
    construct(Extension extension) {
        construct FixedExtensionProvider([extension]);
    }

    @Override
    Extension[] getExtensions(ExecutionContext context) {
        return extensions;
    }
}
