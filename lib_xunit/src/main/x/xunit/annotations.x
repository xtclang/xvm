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
     */
    @Abstract annotation AbstractAll
            extends Test(group=Test.Omit)
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
     */
    @Abstract annotation AbstractEach
            extends Test(group=Test.Omit)
            implements Extension
            into MethodOrFunction;
}
