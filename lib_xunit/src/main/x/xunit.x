/**
 * The XUnit test framework module.
 */
module xunit.xtclang.org {
    package collections import collections.xtclang.org;

    /**
     * A mixin that marks a module as a test suite.
     *
     * ToDo: we should eventually be able to remove this when there is a proper "xtest"
     * Ecstasy test executable that will execute tests for a given module in the same
     * way that "xec" executes a module.
     */
    mixin Suite
            into Module {
        /**
         * Discover and execute all the test fixtures in the `Module`.
         */
        void test() {
        }
    }

    /**
     * A `Method` or a `Function`.
     */
    typedef Method<Object, Tuple<>, Tuple<>> | Function<<>, Tuple<>> as TestMethodOrFunction;


    /**
     * An identifier of a test fixture.
     *
     * @param uniqueId     the `UniqueId` for the test fixture in the test hierarchy
     * @param displayName  the human readable display name to use for the test fixture
     */
    const TestIdentifier(UniqueId uniqueId, String displayName);

    /**
     * A function that performs a predicate check on a test fixture.
     */
    typedef function Boolean (Object) as FixturePredicate;
    
    static FixturePredicate MethodFixturePredicate = o -> o.is(Method);

    static FixturePredicate ClassFixturePredicate = o -> o.is(Class);

    static FixturePredicate PackageFixturePredicate = o -> o.is(Package);
}
