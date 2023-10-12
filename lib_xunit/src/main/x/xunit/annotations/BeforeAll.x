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
 * * Priority of annotated method execution is determined by tge `priority` property.
 *   Where methods or functions with a lower priority are executed first. Methods or
 *   functions with the default `Int.MaxValue` priority will be executed in order of
 *   super class annotated methods or functions first.
 *
 * * Whilst priority can be used to affect ordering, it is clearer and recommended to only have
 *   a single `@BeforeAll` annotated method in a test class.
 *
 * @param priority  applies an ordering to the execution of `BeforeAll` annotated methods or
 *                  functions.
 */
mixin BeforeAll(Int priority = Int.MaxValue)
        extends MethodOrFunctionMixin(priority)
        implements ExtensionMixin
        into MethodOrFunction {
    /**
     * Return the `BeforeAll` annotated `MethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `BeforeAll` annotated `MethodOrFunction` as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders() {
        return super() + new FixedExtensionProvider(name, new BeforeAllFunction(this));
    }
}
