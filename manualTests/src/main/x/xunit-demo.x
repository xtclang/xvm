/**
 * An XUnit test.
 *
 * This test can be executed from the manualTests directory using the following command
 *
 *  xtc test -L build/xtc/main/lib -o  build/xtc/main/lib src/main/x/xunit-demo.x
 *
 * Any XUnit test output will be in the manualTests/xunit directory. This can be changed using the
 * --xunit-out command line option, for example to put output into the manualTests/build/xunit
 * directory
 *
 *  xtc test -L build/xtc/main/lib -o build/xtc/main/lib --xunit-out build/xunit src/main/x/xunit-demo.x
 *
 */
module xunit_demo {

    package xunit import xunit.xtclang.org;

    import xunit.annotations.AfterAll;
    import xunit.annotations.AfterEach;
    import xunit.annotations.BeforeAll;
    import xunit.annotations.BeforeEach;
    import xunit.annotations.ParameterizedTest;
    import xunit.annotations.RepeatedTest;
    import xunit.annotations.TestInjectables;

    import xunit.extensions.ExecutionContext;

    @Inject Console console;

    Int n = 100;

    static Int n2 = 200;

    /**
     * Executes before any test method in this module or any classes or packages in this module
     * (i.e. it is the first thing executed)
     */
    @BeforeAll
    static void setupAll() {
        console.print(">>>> In Module BeforeAll");
        @Inject ExecutionContext context;
        console.print($">>>> In Module BeforeAll {context.testClass} {context.testMethod}");
    }

    /**
     * Executes before each test method declared directly in this module.
     */
    @BeforeEach
    void setupTestEach() {
        console.print($"  >>>> In Module BeforeEach non-static");
        @Inject ExecutionContext context;
        console.print($"  >>>> In Module BeforeEach non-static {context.testClass} {context.testMethod}");
    }

    /**
     * Executes before each test method in this module and because it is a static method it also
     * executes before any test method in any package or class within this module.
     */
    @BeforeEach
    static void setupTestEachAll() {
        console.print($"  >>>> In Module BeforeEach static");
        @Inject ExecutionContext context;
        console.print($"  >>>> In Module BeforeEach static {context.testClass} {context.testMethod}");
    }

    /**
     * Executes after each test method declared directly in this module.
     */
    @AfterEach
    void cleanupEach() {
        console.print($"  >>>> In Module AfterEach non-static");
        @Inject ExecutionContext context;
        console.print($"  >>>> In Module AfterEach non-static {context.testClass} {context.testMethod}");
    }

    /**
     * Executes after each test method in this module and because it is a static method it also
     * executes after any test method in any package or class within this module.
     */
    @AfterEach
    static void cleanupEachAll() {
        console.print($"  >>>> In Module AfterEach static");
        @Inject ExecutionContext context;
        console.print($"  >>>> In Module AfterEach static {context.testClass} {context.testMethod}");
        console.print($"  >>>> In Module AfterEach static test class {context.testClass}");
        console.print($"  >>>> In Module AfterEach static test {context.testMethod}");
    }

    /**
     * Executes after any test method in this module or any classes or packages in this module
     * (i.e. it is the last thing executed)
     */
    @AfterAll
    static void cleanupAll() {
        console.print($">>>> In Module AfterAll");
        @Inject ExecutionContext context;
        console.print($">>>> In Module AfterAll {context.testClass} {context.testMethod}");
    }

    @Test
    @TestInjectables(Map:["Foo"="1", "Bar"="2"])
    void testOne() {
        @Inject ExecutionContext context;
        @Inject("Foo") String? s;
        console.print($"    >>>> In Module testOne {context.testClass} {context.testMethod} String foo={s}");
        @Inject("Bar") Int? n;
        console.print($"    >>>> In Module testOne {context.testClass} {context.testMethod} Int bar={n}");
    }

    @Test
    void testTwo() {
        @Inject ExecutionContext context;
        console.print($"    >>>> In Module testTwo {context.testClass} {context.testMethod}");
    }

    @Test
    @ParameterizedTest(simpleParameters)
    @RepeatedTest(3)
    void testThreeTimesWithParams(String arg) {
        @Inject ExecutionContext context;
        @Inject(RepeatedTest.CurrentIteration) Int? iteration;
        @Inject(RepeatedTest.IterationCount)   Int? iterations;
        console.print($"    >>>> In Module testThreeTimes {context.testClass}  {context.testMethod} arg={arg} iteration={iteration} iterations={iterations}");
    }

    @RepeatedTest(2)
    void testTwoTimes(RepeatedTest.Info info) {
        @Inject ExecutionContext context;
        @Inject(RepeatedTest.CurrentIteration) Int? iteration;
        @Inject(RepeatedTest.IterationCount)   Int? iterations;
        console.print($"    >>>> In Module testThreeTimes {context.testClass} {context.testMethod} iteration={iteration} iterations={iterations} info={info}");
    }

    @ParameterizedTest(simpleParameters)
    void shouldHaveStringParameter(String arg) {
        console.print($"    >>>> In Module shouldHaveStringParameter: {arg}");
    }

    static String[] simpleParameters() {
        return ["Foo", "Bar"];
    }

    @ParameterizedTest(multiParameters)
    void shouldHaveTwoParameters(String stringValue, String anotherString) {
        console.print($"    >>>> In Module shouldHaveTwoParameters: {stringValue}, {anotherString}");
    }

    static List<Tuple> multiParameters() {
        return [("One", "Another One"), ("Two", "Another Two")];
    }

    @ParameterizedTest(mixedParameters)
    void shouldHaveMixedParameters(String stringValue, Int intValue, String anotherString) {
        console.print($"    >>>> In Module shouldHaveMixedParameters: {stringValue}, {intValue}, {anotherString}");
    }

    static List<Tuple> mixedParameters() {
        return [("One", 1, "Another One"), ("Two", 2, "Another Two")];
    }



    class Foo {

        Int n = 0;

        static Int n2 = 0;

        /**
         * Executes before any test method in this class is executed.
         */
        @BeforeAll
        static void setupAllFoo() {
            console.print($"    >>>> In Foo BeforeAll");
            @Inject ExecutionContext context;
            console.print($"    >>>> In Foo BeforeAll {context.testClass} {context.testMethod}");
        }

        /**
         * Executes before each test method declared directly in this class.
         */
        @BeforeEach
        void setupTestFoo() {
            n++;
            console.print($"      >>>> In Foo BeforeEach {n}");
            @Inject ExecutionContext context;
            console.print($"      >>>> In Foo BeforeEach {context.testClass} {context.testMethod}");
        }

        /**
         * Executes after each test method declared directly in this module.
         */
        @AfterEach
        void cleanupFoo() {
            console.print($"      >>>> In Foo AfterEach");
            @Inject ExecutionContext context;
            console.print($"      >>>> In Foo AfterEach {context.testClass} {context.testMethod}");
        }

        /**
         * Executes before all test method in this class have executed.
         */
        @AfterAll
        static void cleanupAllFoo() {
            console.print($"    >>>> In Foo AfterAll");
            @Inject ExecutionContext context;
            console.print($"    >>>> In Foo AfterAll {context.testClass} {context.testMethod}");
        }

        @Test
        void testFooOne() {
            @Inject ExecutionContext context;
            console.print($"       >>>> In Foo testFooOne {context.testClass} {context.testMethod}");
        }

        @Test
        void testFooTwo() {
            @Inject ExecutionContext context;
            console.print($"       >>>> In Foo testFooTwo {context.testClass} {context.testMethod}");
        }
    }

   package tests {

        class Bar {
            @Test
            void testBar() {
                @Inject ExecutionContext context;
                console.print($"       >>>> In Bar testBar {context.testClass} {context.testMethod}");
            }
        }
   }
}