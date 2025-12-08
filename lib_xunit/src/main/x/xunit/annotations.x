import extensions.Extension;

/**
 * The `annotations` package contains the XUnit annotations used to annotate test fixtures.
 */
package annotations {

    /**
     * An annotation on a `Method` or `Function` to indicate that this method should be executed
     * once before any test method is executed in the specified class, sub-class or inner classes.
     *
     * This annotation can only be added to functions as it is executed before the test target
     * instance is constructed.
     *
     * @param order  applies an ordering to the execution of associate test extension. Extensions
     *               with the highest order value will be executed first.
     */
    @Abstract annotation AbstractAll(Int order)
            extends Test(group=Test.Omit, order=order)
            implements Extension
            into Function;

    /**
     * An annotation on a `Method` or `Function` to indicate that this method should be executed
     * repeatedly before each test method.
     *
     * * If the annotated method is declared at the `Module` level, the method is executed
     *   repeatedly before every test in the entire module.
     * * If the annotated method is declared at the `Package` level, the method is executed
     *   repeatedly before every test in that `Package`.
     * * If the annotated method is declared at the `Class` level, the method is executed
     *   repeatedly before every test in that `Class`.
     *
     * @param order  applies an ordering to the execution of annotated methods. Extensions with the
     *               highest order value are executed first.
     */
    @Abstract annotation AbstractEach(Int order = 0)
            extends Test(group=Test.Omit, order=order)
            implements Extension
            into MethodOrFunction;
}