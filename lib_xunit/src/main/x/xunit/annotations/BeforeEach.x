import extensions.BeforeEachFunction;
import extensions.FixedExtensionProvider;

/**
 * A mixin on a `Method` to indicate that this method should be executed repeatedly
 * before each test method.
 *
 * * If the annotated method is declared at the `Module` level, the method is executed
 *   repeatedly before every test in the entire module.
 * * If the annotated method is declared at the `Package` level, the method is executed
 *   repeatedly before every test in that `Package`.
 * * If the annotated method is declared at the `Class` level, the method is executed
 *   repeatedly before every test in that `Class`.
 * * If the annotated method throws an exception, no further "before" processing will be
 *   invoked, tests will not be invoked, any "after all" and "after each" processing will
 *   be invoked.
 *
 * @param priority  applies an ordering to the execution of `BeforeEach` annotated methods
 *                  that apply at the same level. Execution will be highest priority first.
 */
mixin BeforeEach(Int priority = 0, FixturePredicate matcher = MethodFixturePredicate)
        extends AbstractEach(priority, matcher)
        into TestMethodOrFunction {
    /**
     * Return the `BeforeEach` annotated `TestMethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `BeforeEach` annotated `TestMethodOrFunction` as an `ExtensionProvider`
     */
    ExtensionProvider asBeforeEachProvider() {
        return new FixedExtensionProvider(new BeforeEachFunction(this));
    }
}
