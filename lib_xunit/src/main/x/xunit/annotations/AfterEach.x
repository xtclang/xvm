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
 * * Priority of annotated method execution is determined by tge `priority` property.
 *   Where methods or functions with a lower priority are executed first. Methods or
 *   functions with the default `Int.MaxValue` priority will be executed in order of
 *   super class annotated methods or functions first.
 *
 * * Whilst priority can be used to affect ordering, it is clearer and recommended to only have
 *   a single `@AfterEach` annotated method in a test class.
 *
 * @param priority  applies an ordering to the execution of `AfterEach` annotated
 *                  methods or functions
 */
mixin AfterEach(Int priority = Int.MaxValue, FixturePredicate matcher = MethodFixturePredicate)
        extends AbstractEach(priority, matcher)
        implements ExtensionMixin
        into MethodOrFunction {
    /**
     * Return the `AfterEach` annotated `MethodOrFunction` as an `ExtensionProvider`.
     *
     * @return the `AfterEach` annotated `MethodOrFunction` as an `ExtensionProvider`
     */
    @Override
    ExtensionProvider[] getExtensionProviders() {
        return super() + new FixedExtensionProvider(name, new AfterEachFunction(this));
    }
}
