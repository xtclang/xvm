/**
 * The `discovery` package contains classes that are used
 * during discovery of test models to execute.
 */
package discovery {

    /**
     * The injection name prefix for all XUnit discovery configuration values.
     */
    static String ConfigDiscoveryPrefix = ConfigPrefix + ".discovery";

    /**
     * The injection name for the name of the test package to execute tests in.
     */
    static String ConfigDiscoveryTestPackage = ConfigDiscoveryPrefix + ".testPackage";

    /**
     * The injection name for the name of the test class to execute tests in.
     */
    static String ConfigDiscoveryTestClass = ConfigDiscoveryPrefix + ".testClass";

    /**
     * The injection name for the name of a specific test to execute.
     */
    static String ConfigDiscoveryTest = ConfigDiscoveryPrefix + ".test";

    /**
     * The injection name for the name of a specific @Test group to execute.
     */
    static String ConfigDiscoveryGroup = ConfigDiscoveryPrefix + ".testGroup";

    /**
     * The injection name for the verbose discovery logging flag.
     */
    static String ConfigDiscoveryVerbose = ConfigDiscoveryPrefix + ".verbose";

    /**
     * A marker interface to indicate a specific `const` is an implementation
     * of a `Selector`, which will be processed by a `SelectorResolver`.
     */
    interface Selector
            extends immutable Const {
    }

    /**
     * A resolver that resolves a `Selector` into a set of `ModelBuilder`s
     * and additional test `Selector`s.
     */
    interface SelectorResolver
            extends immutable Const {
        /**
         * Resolve a `Selector`.
         *
         * During the discovery phase, a chain of `SelectorResolver`s will be used to resolve `Selector`s.
         * After the first `SelectorResolver` in the chain has handled a `Selector` and returned
         * `True` from its `resolve` method, no further `SelectorResolver`s in the chain will be
         * called for that `Selector.
         *
         * @param configuration  the `DiscoveryConfiguration`
         * @param selector       the `Selector` to resolve
         *
         * @return `True` iff this `SelectorResolver` processed the `Selector`, or `False` if this
         *         `SelectorResolver` does not handle that type of `Selector`
         * @return the `Model`s resolved from the `Selector`
         * @return any additional `Selector`s to use to further resolve test `Model`s
         */
        conditional (ModelBuilder[], Selector[]) resolve(DiscoveryConfiguration config, Selector selector);
    }
}