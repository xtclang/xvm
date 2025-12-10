import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.PropertyExtensionProvider;

/**
 * This annotation is used to indicate that a property of a test fixture should be registered as an
 * `Extension`.
 */
annotation RegisterExtension
        extends Test(group=Test.Omit)
        implements ExtensionProviderProvider
        into Property<Object, Object, Ref<Object>> {

    /**
     * @return the `RegisterExtension` annotated `Property` as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders() {
        return [new PropertyExtensionProvider(this)];
    }
}
