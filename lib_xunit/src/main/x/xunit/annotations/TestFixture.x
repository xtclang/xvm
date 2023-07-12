/**
 * A mixin to indicate the lifecycle of a test fixture.
 *
 * The default behaviour is to create a new instance of a test fixture for
 * every test method. This allows tests to be executed without side-affects
 * due to left over state from previous tests. To change this behaviour so
 * that all tests execute on a single instance of the fixture, annotate the
 * fixture `Class` with `@TestFixture` with a `lifecycle` value of `Singleton`.
 *
 * @param lifecycle  the reason for disabling the test.
 */
mixin TestFixture(Lifecycle lifecycle = EveryTest)
        into Class | Type
    {
    /**
     * An enum representing the different options for the
     * lifecycle of a test fixture.
     */
    enum Lifecycle
        {
        /**
         * All tests for a fixture execute on the same instance.
         */
        Singleton,
        /**
         * Each test for a fixture executes on new fixture instance.
         */
        EveryTest
        }
    }
