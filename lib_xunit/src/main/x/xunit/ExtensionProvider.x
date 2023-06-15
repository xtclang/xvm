/**
 * A provider of `Extension`s.
 */
interface ExtensionProvider {
    /**
     * Returns the `Extension`s provided by this `ExtensionProvider`.
     *
     * @param context  the current `ExecutionContext`
     *
     * @return the `Extension`s.
     */
    Extension[] getExtensions(ExecutionContext context);
}
