import extensions.PropertyExtensionProvider;

/**
 * This mixin is used to indicate that a property of a test fixture should be
 * registered as an `Extension`.
 *
 * * Priority of annotated method execution is determined by tge `priority` property.
 *   Where extensions with a lower priority are executed first. Extensions with the
 *   default `Int.MaxValue` priority will be executed in order of super class
 *   extensions first.
 *
 * @param priority  applies an ordering to the execution of `Extension`s
 */
mixin RegisterExtension(Int priority = Int.MaxValue)
        extends Test(Omit, priority)
        implements ExtensionMixin
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
