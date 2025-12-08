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
 */
annotation BeforeEach
        extends AbstractEach
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
        void beforeEach(ExecutionContext context) = context.invoke(before);
    }
}
