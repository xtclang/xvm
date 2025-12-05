import extensions.AfterAllCallback;
import extensions.ExecutionContext;
import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.FixedExtensionProvider;

/**
 * An annotation on a function to indicate that this function should be executed once after all test
 * methods or functions in the parent class and any inner classes or sub-classes have completed.
 * The annotated function must be static unless it is declared in a singleton, such as a module or
 * package. This is because the function will be called before construction of the test fixture
 * class.
 *
 * * If the annotated function is declared at the module level, the function is executed once
 *   after all tests in the entire module have executed.
 * * If the annotated function is declared at the package level, the function is executed once
 *   after all tests in that package and any classes contained in that package or sub-packages.
 * * If the annotated function is declared at the class level, the function is executed once after
 *   all test in that class or any inner classes or sub-classes.
 * * If the annotated function throws an exception, all remaining "after" processing will still be
 *   invoked.
 * * `AfterAll` annotated methods will be executed regardless of any errors in "before" test methods
 *   or any test failures.
 *
 * * Priority of annotated function execution is determined by the `order` property. Functions with
 *   a lower priority are executed first. Functions or functions with the default `Int.MaxValue`
 *   priority will be executed in order of super class annotated functions first.
 *
 * * Whilst priority can be used to affect ordering, it is clearer and recommended to only have
 *   a single `@AfterAll` annotated function in a test class.
 *
 * @param order  applies an ordering to the execution of `AfterAll` annotated functions.
 */
annotation AfterAll(Int order = Int.MaxValue)
        extends AbstractAll(order)
        implements ExtensionProviderProvider {

    /**
     * Return the `AfterAll` annotated function as an `ExtensionProvider`.
     *
     * @return the `AfterAll` annotated function as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders()
            = super() + new FixedExtensionProvider(name, new AfterAllFunction(this));

    /**
     * An `AfterAllCallback` extension implementation that wraps an `AfterAll` annotated function.
     * When the callback is invoked, the wrapped function will be invoked.
     *
     * @param after  the `AfterAll` annotated function to be invoked
     */
    static const AfterAllFunction(AfterAll after)
            implements AfterAllCallback {

        @Override
        Int order.get() = after.order;

        @Override
        void afterAll(ExecutionContext context) = context.invoke(after);
    }
}
