import extensions.BeforeAllFunction;
import extensions.FixedExtensionProvider;

/**
 * A mixin on a static `Method` or `Function` to indicate that this method should be executed
 * once before all test methods. The annotated method or function must be static unless
 * it is declared in a singleton such as a module or package. This is because the method
 * or function will be called before construction of the test fixture class.
 *
 * * If the annotated `Method` or `Function` is declared at the `Module` level, the method is
 *   executed once before any test in the entire module.
 * * If the annotated `Method` or `Function` is declared at the `Package` level, the method is
 *   executed once before all tests in that `Package`.
 * * If the annotated `Method` or `Function` is declared at the `Class` level, the method is
 *   executed once before all test in that `Class`.
 * * If the annotated method throws an exception, no further "before" processing will be
 *   invoked, tests will not be invoked, any "after all" processing will be invoked.
 *
 * @param priority  applies an ordering to the execution of `BeforeAll` annotated methods or
 *                  functions that apply at the same level. Execution will be highest priority
 *                  first.
 */
mixin BeforeAll(Int priority = 0)
        extends TestMethodOrFunctionMixin(priority)
        into TestMethodOrFunction {

    /**
     * Return the `BeforeAll` annotated `TestMethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `BeforeAll` annotated `TestMethodOrFunction` as an `ExtensionProvider`
     */
    ExtensionProvider asBeforeAllProvider() {
        return new FixedExtensionProvider(new BeforeAllFunction(this));
    }
}
