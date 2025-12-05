import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.PropertyExtensionProvider;

/**
 * This annotation is used to indicate that a property of a test fixture should be registered as an
 * `Extension`.
 *
 * * Priority of annotated method execution is determined by the `order` property. Where
 *   extensions with a lower order are executed first. Extensions with the default `Int.MaxValue`
 *   order will be executed in order of super class extensions first.
 *
 * @param order  applies an ordering to the execution of `Extension`s
 */
annotation RegisterExtension(Int order = Int.MaxValue)
        extends Test(group=Test.Omit, order=order)
        implements ExtensionProviderProvider
        into Property<Object, Object, Ref<Object>> {

    /**
     * Return the `RegisterExtension` annotated `Property` as an `ExtensionProvider`.
     *
     * @return the `RegisterExtension` annotated `Property` as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders() {
        return [new PropertyExtensionProvider(this)];
    }
}
