import extensions.AfterAllFunction;
import extensions.FixedExtensionProvider;

/**
 * A mixin on a `Method` or `Function` to indicate that this method should be executed
 * once after all test methods. The annotated method or function must be static unless
 * it is declared in a singleton such as a module or package. This is because the method
 * or function will be called before construction of the test fixture class.
 *
 * * If the annotated `Method` or `Function` is declared at the `Module` level, the method is
 *   executed once after any test in the entire module.
 * * If the annotated `Method` or `Function` is declared at the `Package` level, the method is
 *   executed once after all tests in that `Package`.
 * * If the annotated `Method` or `Function` is declared at the `Class` level, the method is
 *   executed once after all test in that `Class`.
 * * If the annotated method throws an exception, all remaining "after" processing will
 *   still be invoked.
 *
 * @param priority  applies an ordering to the execution of `AfterAll` annotated methods or
 *                  functions that apply at the same level. Execution will be highest priority
 *                  first.
 */
mixin AfterAll(Int priority = 0)
        extends TestMethodOrFunctionMixin(priority)
        into TestMethodOrFunction {

    /**
     * Return the `AfterAll` annotated `TestMethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `AfterAll` annotated `TestMethodOrFunction` as an `ExtensionProvider`
     */
    ExtensionProvider asAfterAllProvider() {
        return new FixedExtensionProvider(new AfterAllFunction(this));
    }
}
