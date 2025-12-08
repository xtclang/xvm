/**
 * `Test` is a compile-time annotation that has two purposes:
 *
 * * `Test` is a compile-time annotation that marks the class, property, method, constructor or
 *    function as being a link-time conditional using the name-condition of `test`. Items marked
 *    with this annotation will be available in a unit testing container, but are unlikely to be
 *    available if the code is not running in a testing container. This means that the annotated
 *    class, property, method or function will **not** be loaded by default, but will be available
 *    when the TypeSystem is created in `test` mode.
 *
 * * When used to annotate a method or constructor, and if the method or constructor is determined
 *   to be _callable_, this annotation indicates that the method or constructor is a _unit test_
 *   intended for automatic test execution, for example by the `xunit` utility. To be _callable_,
 *   the method or constructor must have no non-default parameters, and for a non-static method,
 *   there must also exist a constructor on the class with no non-default parameters. Lastly, if the
 *   `group` is specified as [Omit], then the method is **not** _callable_.
 *
 * The annotation provides two optional parameters that are used to tailor the unit test
 * specification for methods and constructors:
 *
 * * [group] - this assigns the test to a named group of tests, which allows specific groups of
 *   tests to be selected for execution (or for avoidance):
 *
 * * * The default for unit test execution is ["unit"](Unit);
 *
 * * * The default for long-running unit test avoidance is ["slow"](Slow);
 *
 * * * A special value ["omit"](Omit) unconditionally avoids use for unit testing, and is used for
 *     _callable_ methods and constructors that are link-time conditional (using `@Test`), but are
 *     **not** intended as unit tests.
 *
 *   Other group names can be used; any other names are expected to be treated as normal unit tests
 *   unless the test runner (such as `xunit`) is configured otherwise.
 *
 * * [order] - this assigns an ordering to execution of the annotated elements. Best practice is
 *   that unit tests should be agnostic of ordering, but other annotated elements, such as XUnit
 *   before test or after test methods, test extensions, etc. may require ordering to be specified.
 *   Annotated elements are ordered based the priority value, where a higher value has greater
 *   priority than a lower value.
 *
 * The parameters are ignored when the annotation is used on classes and properties. Any usage other
 * than that specified above may result in a compile-time and/or load/link-time error.
 */
annotation Test(String group = Unit, Int order = Int.MaxValue)
        extends Iff("test".defined)
        implements Orderable {
    /**
     * Use this [group] value to indicate a normal unit test. This is the default test group name.
     */
    static String Unit = "unit";

    /**
     * Use this [group] value to indicate a unit test that should only be run if it is explicitly
     * told to run (because the test may take a long time).
     */
    static String Slow = "slow";

    /**
     * Use this [group] value to indicate that the method must **not** be treated as a unit test.
     *
     * Alternatively, just use `@Iff("test".defined)`.
     */
    static String Omit = "omit";

    // ----- Orderable -----------------------------------------------------------------------------

    static <CompileType extends Test> Ordered compare(CompileType value1, CompileType value2) {
        return value1.order <=> value2.order;
    }
}