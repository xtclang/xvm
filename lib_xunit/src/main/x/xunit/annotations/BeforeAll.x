import extensions.BeforeAllCallback;
import extensions.ExecutionContext;
import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.FixedExtensionProvider;

/**
 * An annotation on a static function to indicate that this function should be executed once before
 * all tests. The annotated function must be static unless it is declared in a singleton such as a
 * module or package. This is because the function will be called before construction of the test
 * fixture class.
 *
 * * If the annotated function is declared at the module level, the function is executed once
 *   before any test in the entire module, it packages and child classes.
 * * If the annotated function is declared at the package level, the function is executed once
 *   before all tests in that package.
 * * If the annotated function is declared at the class level, the function is executed once
 *   before all test in that class.
 * * If the annotated function throws an exception, no further "before" processing will be invoked,
 *   tests will not be invoked, any "after all" processing will be invoked.
 *
 * * Priority of annotated function execution is determined by tge `order` property. Where functions
 *   with a higher priority are executed first. Functions with the default zero order will be
 *   executed in order of super class annotated functions first.
 *
 * * Whilst the order property can be used to affect ordering, it is clearer and recommended to only
 *   have a single `@BeforeAll` annotated function in a test class.
 *
 * @param order  applies an ordering to the execution of `BeforeAll` annotated functions.
 */
annotation BeforeAll(Int order = 0)
        extends AbstractAll(order)
        implements ExtensionProviderProvider {

    /**
     * @return the `BeforeAll` annotated function as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders()
            = super() + new FixedExtensionProvider(name, new BeforeAllFunction(this));

    /**
     * A `BeforeAllCallback` extension implementation that wraps a `BeforeAll` annotated function.
     * When the callback is invoked, the wrapped `BeforeAll` will be invoked.
     *
     * @param before  the `BeforeAll` annotated function to be invoked
     */
    static const BeforeAllFunction(BeforeAll before)
            implements BeforeAllCallback {

        @Override
        Int order.get() = before.order;

        @Override
        void beforeAll(ExecutionContext context) = context.invoke(before);
    }
}
