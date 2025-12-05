import extensions.AfterEachCallback;
import extensions.ExecutionContext;
import extensions.ExtensionProvider;
import extensions.ExtensionProviderProvider;
import extensions.FixedExtensionProvider;

/**
 * An annotation on a method or function to indicate that this method should be executed repeatedly
 * after each test method in the parent class.
 *
 * * If the annotated method is declared at the module level, the method is executed repeatedly
 *   after every test declared directly in the module.
 * * If the annotated method is declared at the package level, the method is executed repeatedly
 *   after every test in declared directly in the package.
 * * If the annotated method is declared at the class level, the method is executed repeatedly after
 *   every test declared directly in that class.
 * * If the annotated method throws an exception, all remaining "after" processing will still be
 *   invoked.
 *
 * * Priority of annotated method execution is determined by tge `priority` property. Where methods
 *   or functions with a lower priority are executed first. Methods or functions with the default
 *   `Int.MaxValue` priority will be executed in order of super class annotated methods or functions
 *    first.
 *
 * * Whilst priority can be used to affect ordering, it is clearer and recommended to only have a
 *   single `@AfterEach` annotated method in a test class.
 *
 * @param order   applies an ordering to the execution of `AfterEach` annotated methods or functions
 * @param matcher a predicate used to filter
 */
annotation AfterEach(Int order = Int.MaxValue)
        extends AbstractEach(order)
        implements ExtensionProviderProvider
        into MethodOrFunction {

    /**
     * Return the `AfterEach` annotated `MethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `AfterEach` annotated `MethodOrFunction` as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders()
            = super() + new FixedExtensionProvider(name, new AfterEachFunction(this));

    /**
     * An `AfterEachCallback` extension implementation that wraps an `AfterEach` annotated method or
     * function. When the callback is invoked the wrapped method or function will be invoked.
     *
     * @param after  the `AfterEach` annotated method or function to be invoked
     */
    static const AfterEachFunction(AfterEach after)
            implements AfterEachCallback {

        @Override
        Boolean requiresTarget.get() = after.is(Method);

        @Override
        Int order.get() = after.order;

        @Override
        void afterEach(ExecutionContext context) = context.invoke(after);
    }
}
