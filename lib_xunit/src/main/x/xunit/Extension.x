/**
 * ## XUnit Extension Mechanism
 *
 * XUnit uses the concept of extensions for almost everything.
 * This allows test developers for customize the execution of tests to suit
 * their uses-cases. For example adding behaviour to execute before or after tests,
 * parameterized tests, intercepting fixture construction, etc.
 *
 * The `Extension` interface is basically a marker, that indicates that a type is an
 * extension. An `Extension` has a priority, which is used to determine the order that
 * extensions are executed in a particular test phase.
 *
 * `Extension`s are `Orderable` by their `priority`. `Extension`s with a higher `priority` will
 * comer before those with a lower priority (i.e. the reverse of natural `Int` ordering).
 *
 * ### Extension Execution Phases
 *
 * For normal operation, where a test fixture instance is created for every test method, the order
 * of execution of extensions is as follows:
 *
 * * BeforeAllCallback extensions
 * * @BeforeAll annotated methods and functions
 * * The following phases are repeated for each test:
 * ** TestFixturePreConstructCallback extensions
 * ** The test fixture constructor is called.
 * ** TestFixturePostConstructCallback extensions
 * ** BeforeEachCallback extensions
 * ** @BeforeEach annotated methods and functions
 * ** BeforeTestInvocationCallback extensions
 * ** The test method is executed
 * ** AfterTestInvocationCallback extensions
 * ** @AfterEach annotated methods and functions
 * ** AfterEachCallback extensions
 * ** TestFixturePreDestroyCallback extensions
 * * @AfterAll annotated methods and functions
 * * AfterAllCallback extensions
 *
 * Where a test fixture is a singleton, such as a `Module` or `Package`, or where the test fixture
 * lifecycle has been configured to be per-class, a single instance of the test fixture is created
 * for all tests methods. The order of execution of extensions is as follows:
 *
 * * TestFixturePreConstructCallback extensions
 * * TestFixturePostConstructCallback extensions
 * * The test fixture constructor is called, unless it is a singleton.
 * * BeforeAllCallback extensions
 * * @BeforeAll annotated methods and functions
 * * The following phases are repeated for each test:
 * ** BeforeEachCallback extensions
 * ** @BeforeEach annotated methods and functions
 * ** BeforeTestInvocationCallback extensions
 * ** The test method is executed
 * ** AfterTestInvocationCallback extensions
 * ** @AfterEach annotated methods and functions
 * ** AfterEachCallback extensions
 * * @AfterAll annotated methods and functions
 * * AfterAllCallback extensions
 * * TestFixturePreDestroyCallback extensions
 *
 * Any of the phases of execution above could fail due to an exception being thrown, or a test
 * pre-condition failing. In this case some, or all of the remaining phases will be skipped.
 *
 * * BeforeAllCallback extensions throw an exception
 * ** No further BeforeAllCallback extensions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * @BeforeAll annotated methods and functions throw an exception
 * ** No further @BeforeAll annotated methods and functions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * TestFixturePreConstructCallback extensions throw an exception
 * ** No further TestFixturePreConstructCallback extensions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * The test fixture constructor throw an exception
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * TestFixturePostConstructCallback extensions throw an exception
 * ** No further TestFixturePostConstructCallback extensions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * BeforeEachCallback extensions
 * ** No further BeforeEachCallback extensions will execute
 * ** @BeforeEach annotated methods or functions will not execute
 * ** BeforeTestInvocationCallback extensions will not execute
 * ** The test method will not execute
 * ** AfterTestInvocationCallback extensions will not execute
 * ** @AfterEach annotated methods or functions will not execute
 * ** AfterEachCallback extensions will execute
 * ** TestFixturePreDestroyCallback extensions will execute
 * ** @AfterAll annotated methods and functions will execute
 * ** AfterAllCallback extensions will execute
 *
 * * @BeforeEach annotated methods and functions  throw an exception
 * ** No further @BeforeEach annotated methods and functions will execute
 * ** BeforeTestInvocationCallback extensions will not execute
 * ** The test method will not execute
 * ** AfterTestInvocationCallback extensions will execute
 * ** @AfterEach annotated methods or functions will execute
 * ** AfterEachCallback extensions will execute
 * ** TestFixturePreDestroyCallback extensions will execute
 * ** @AfterAll annotated methods and functions will execute
 * ** AfterAllCallback extensions will execute
 *
 * * BeforeTestInvocationCallback extensions throw an exception
 * ** No further BeforeTestInvocationCallback extensions will execute
 * ** The test method will not execute
 * ** AfterTestInvocationCallback extensions will not execute
 * ** @AfterEach annotated methods or functions will not execute
 * ** AfterEachCallback extensions will execute
 * ** TestFixturePreDestroyCallback extensions will execute
 * ** @AfterAll annotated methods and functions will execute
 * ** AfterAllCallback extensions will execute
 *
 * * Test methods throw an exception, this is normal failed test behaviour, so all remaining
 *   phases will execute.
 *
 * * AfterTestInvocationCallback extensions throw an exception
 * ** All remaining AfterTestInvocationCallback extensions will execute.
 * ** All @AfterEach annotated methods and functions will execute.
 * ** All AfterAllCallback extensions will execute.
 * ** All TestFixturePreDestroyCallback extensions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * @AfterEach annotated methods and functions throw an exception
 * ** All remaining @AfterEach annotated methods and functions will execute.
 * ** All AfterAllCallback extensions will execute.
 * ** All TestFixturePreDestroyCallback extensions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * AfterEachCallback extensions throw an exception
 * ** All remaining AfterAllCallback extensions will execute.
 * ** All TestFixturePreDestroyCallback extensions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * TestFixturePreDestroyCallback extensions throw an exception
 * ** All remaining TestFixturePreDestroyCallback extensions will execute
 * ** All @AfterAll annotated methods and functions will execute
 * ** All AfterAllCallback extensions will execute
 *
 * * @AfterAll annotated methods and functions throw an exception
 * ** All remaining @AfterAll annotated methods and functions will execute.
 * ** All AfterAllCallback extensions will execute.
 *
 * * AfterAllCallback extensions throw an exception
 * ** All remaining AfterAllCallback extensions will execute.
 */
interface Extension
        extends Orderable {

    /**
     * The priority for this extension.
     * Extensions with a higher priority will execute first.
     */
    @RO Int priority.get() {
        Class clz = &this.actualClass;
        if (clz.is(Test)) {
            return clz.priority;
        }
        return 0;
    }

    // ---- Orderable ------------------------------------------------------------------------------

    static <CompileType extends Extension> Ordered compare(CompileType value1, CompileType value2) {
        // Highest priority comes first (i.e. reverse natural Int order)
        return value2.priority <=> value1.priority;
    }
}