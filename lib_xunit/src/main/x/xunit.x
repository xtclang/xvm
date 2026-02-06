/**
 * The XUnit module is the API required for developers to be able to add tests to their
 * applications.
 *
 * Applications can import this module to add XUnit tests to their application code.
 * Basic tests that only use the `@Test` annotation can be added without importing this module.
 *
 * The xunit module does not contain the functionality to actually execute tests. Tests are executed using the Ecstasy
 * CLI by running the `xtc test` command.
 */
module xunit.xtclang.org {

    /**
     * The valid targets for a Test annotation.
     */
    typedef Module | Package | Class | Property | Method | Function as TestTarget;

    /**
     * An `PreconditionFailed` exception is raised when a test precondition fails.
     *
     * This is typically used in tests to indicate that a the preconditions for running the test
     * cannot be met so the test is marked as skipped rather than failed.
     */
    const PreconditionFailed(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A `Method` or a `Function`.
     */
    typedef Method<Object, Tuple<>, Tuple<>> | Function<Tuple, Tuple> | Function<<>, <Object>>
            as MethodOrFunction;

   /**
     * A skipped test result.
     */
	const SkipResult(Boolean skipped, String reason = "unknown") {
	    /**
	     * A singleton not skipped `SkipResult`.
	     */
	    static SkipResult NotSkipped = new SkipResult(False);
    }

    /**
     * A simple Injector service to pass injectable resources to test modules.
     *
     * This service is typically used as the injector for module imports inside test modules.
     */
    static service PassThruInjector
            implements ecstasy.reflect.Injector
            implements ecstasy.mgmt.ResourceProvider {

        @Override
        Supplier getResource(Type type, String name) {
            assert as "Should not be called, PassThruInjector should only be used as an Injector";
        }

        @Override
        <InjectionType> InjectionType inject(Type<InjectionType> type,
                                             String              name,
                                             Inject.Options      opts = Null) {
            @Inject ecstasy.reflect.Injector injector;
            return injector.inject(type, name, opts);
        }
    }
}
