import extensions.PropertyExtensionProvider;

/**
 * This mixin is used to indicate that a property of a test fixture should be
 * registered as an `Extension`.
 *
 * @param priority  applies an ordering to the execution of methods in `Extension`
 *                  annotated classes that apply at the same level. Execution will be
 *                  highest priority first.
 */
mixin RegisterExtension(Int priority = 0)
        extends Test(Omit, priority)
        into Property<Object, Object, Ref<Object>> {

    /**
     * Return the `RegisterExtension` annotated `Property` as an `ExtensionProvider`.
     *
     * @return the `RegisterExtension` annotated `Property` as an `ExtensionProvider`
     */
    ExtensionProvider asProvider() {
        return new PropertyExtensionProvider(this);
    }
}
