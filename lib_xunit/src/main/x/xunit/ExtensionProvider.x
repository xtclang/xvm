/**
 * A provider of `Extension`s.
 */
interface ExtensionProvider {

    /**
     * The name of this provider.
     */
    @RO String name;

    /**
     * Returns the `Extension`s provided by this `ExtensionProvider`.
     *
     * @param context  the current `ExecutionContext`
     *
     * @return the `Extension`s.
     */
    Extension[] getExtensions(ExecutionContext context);
}
