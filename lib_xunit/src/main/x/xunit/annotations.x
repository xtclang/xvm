/**
 * The `annotations` package contains the XUnit mixins used to
 * annotate test fixtures.
 */
package annotations {
    /**
     * A base mixin for test mixins that extends `Test` with a group of `Omit`
     * and a specific `priority`.
     *
     * @param priority  applies an ordering to the execution of associate test extension.
     */
    @Abstract mixin AbstractTestMixin(Int priority)
            extends Test(Test.Omit, priority)
            implements Extension;

    /**
     * A base mixin for test mixins that extends `Test` with a group of `Omit`
     * and a specific `priority` that mix into a `TestMethodOrFunction`.
     *
     * @param priority  applies an ordering to the execution of associate test extension.
     */
    @Abstract mixin TestMethodOrFunctionMixin(Int priority)
            extends Test(Test.Omit, priority)
            implements Extension
            into TestMethodOrFunction;

    /**
     * A mixin on a `Method` to indicate that this method should be executed repeatedly
     * before each test method.
     *
     * * If the annotated method is declared at the `Module` level, the method is executed
     *   repeatedly before every test in the entire module.
     * * If the annotated method is declared at the `Package` level, the method is executed
     *   repeatedly before every test in that `Package`.
     * * If the annotated method is declared at the `Class` level, the method is executed
     *   repeatedly before every test in that `Class`.
     *
     * @param priority  applies an ordering to the execution of `BeforeEach` annotated methods
     *                  that apply at the same level. Execution will be highest priority first.
     */
    @Abstract mixin AbstractEach(Int priority = 0, FixturePredicate matcher = MethodFixturePredicate)
            extends Test(Test.Omit, priority)
            implements Extension
            into TestMethodOrFunction;
}