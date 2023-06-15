import extensions.AfterEachFunction;
import extensions.FixedExtensionProvider;

/**
 * A mixin on a `Method` to indicate that this method should be executed repeatedly
 * after each test method.
 *
 * * If the annotated method is declared at the `Module` level, the method is executed
 *   repeatedly after every test in the entire module.
 * * If the annotated method is declared at the `Package` level, the method is executed
 *   repeatedly after every test in that `Package`.
 * * If the annotated method is declared at the `Class` level, the method is executed
 *   repeatedly after every test in that `Class`.
 * * If the annotated method throws an exception, all remaining "after" processing will
 *   still be invoked.
 *
 * @param priority  applies an ordering to the execution of `AfterEach` annotated methods
 *                  that apply at the same level. Execution will be highest priority first.
 */
mixin AfterEach(Int priority = 0, FixturePredicate matcher = MethodFixturePredicate)
        extends AbstractEach(priority, matcher)
        into TestMethodOrFunction {
    /**
     * Return the `AfterEach` annotated `TestMethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `AfterEach` annotated `TestMethodOrFunction` as an `ExtensionProvider`
     */
    ExtensionProvider asAfterEachProvider() {
        return new FixedExtensionProvider(new AfterEachFunction(this));
    }
}
