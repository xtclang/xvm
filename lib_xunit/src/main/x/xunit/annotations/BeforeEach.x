import extensions.BeforeEachCallback;
import extensions.ExecutionContext;
import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.FixedExtensionProvider;

/**
 * An annotation on a `Method` to indicate that this method should be executed repeatedly before
 * each test method.
 *
 * * If the annotated method is declared at the module level, the method is executed repeatedly
 *   before every test in the entire module.
 * * If the annotated method is declared at the package level, the method is executed repeatedly
 *   before every test in that package.
 * * If the annotated method is declared at the class level, the method is executed repeatedly
 *   before every test in that class.
 * * If the annotated method throws an exception, no further "before" processing will be invoked,
 *   tests will not be invoked, any "after all" and "after each" processing will be invoked.
 *
 * * Priority of annotated method execution is determined by tge `order` property. Where methods
 *   or functions with a higher priority are executed first. Methods or functions with the default
 *   zero priority will be executed in order of super class annotated methods or functions first.
 *
 * * Whilst the order property can be used to affect ordering, it is clearer and recommended to only
 *   have a single `@BeforeEach` annotated method in a test class.
 *
 * @param order  applies an ordering to the execution of `BeforeEach` annotated methods or
 *               functions
 */
annotation BeforeEach(Int order = 0)
        extends AbstractEach(order)
        implements ExtensionProviderProvider
        into MethodOrFunction {

    /**
     * @return the `BeforeEach` annotated `MethodOrFunction` as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders()
            = super() + new FixedExtensionProvider(name, new BeforeEachFunction(this));

    /**
     * A `BeforeEachCallback` extension implementation that wraps a `BeforeEach` annotated method or
     * function. When the callback is invoked, the wrapped `BeforeEach` will be invoked.
     *
     * @param before  the `BeforeEach` annotated method or function to be invoked
     */
    static const BeforeEachFunction(BeforeEach before)
            implements BeforeEachCallback {

        @Override
        Boolean requiresTarget.get() = before.is(Method);

        @Override
        Int order.get() = before.order;

        @Override
        void beforeEach(ExecutionContext context) = context.invoke(before);
    }
}
