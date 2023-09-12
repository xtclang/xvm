import ecstasy.collections.ListMap;

import xunit_engine.Model;
import xunit_engine.UniqueId;

import xunit_engine.ExecutionListener;
import xunit_engine.Result;

/**
 * An implementation of a `ExecutionListener` that allows assertions
 * to be made about the events received by the listener.
 *
 * The listener also asserts that events are received in the correct order,
 * for example complete after start, start not called twice, etc.
 */
class AssertingExecutionListener
        implements ExecutionListener {

    /**
     * The `List` of started tests.
     */
    List<UniqueId> started = new Array();

    /**
     * The `ListMap` of completed tests.
     */
    ListMap<UniqueId, Result> completed = new ListMap();

    /**
     * The `ListMap` of skipped tests.
     */
    ListMap<UniqueId, String> skipped = new ListMap();

    /**
     * The list of published `ReportEntry` instances
     */
    List<Tuple<UniqueId, ExecutionListener.ReportEntry>> reportEntries = new Array();

    /**
     * Any errors that occurred.
     */
    ListMap<UniqueId, Exception> errors = new ListMap();

    /**
     * Clear the state of this listener.
     */
    void clear() {
        started.clear();
        completed.clear();
        skipped.clear();
        reportEntries.clear();
        errors.clear();
    }

    /**
     * Assert that no errors have occurred.
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertNoErrors() {
        assert:test errors.empty;
        return this;
    }

    /**
     * Assert that the test with the specified identifier was completed
     * and then run the specified function with the completed `Result`.
     *
     * @param identifier  the `UniqueId` of the test
     * @param fn          the function to call with the test `Result`
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertResult(UniqueId identifier, function void (Result) fn) {
        if (Result result := completed.get(identifier)) {
            fn(result);
        } else {
            throw new Assertion($"Test {identifier} was not completed");
        }
        return this;
    }

    /**
     * Assert that the test with the specified identifier was started.
     *
     * @param identifier the `UniqueId` of the test
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertStarted(UniqueId identifier) {
        assert:test started.contains(identifier);
        return this;
    }

    /**
     * Assert that the test with the specified identifier was not started.
     *
     * @param identifier the `UniqueId` of the test
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertNotStarted(UniqueId identifier) {
        assert:test !started.contains(identifier);
        return this;
    }

    /**
     * Assert that the test with the specified identifier was completed.
     *
     * @param identifier the `UniqueId` of the test
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertCompleted(UniqueId identifier) {
        assert:test completed.contains(identifier);
        return this;
    }

    /**
     * Assert that the test with the specified identifier was not completed.
     *
     * @param identifier the `UniqueId` of the test
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertNotCompleted(UniqueId identifier) {
        assert:test !completed.contains(identifier);
        return this;
    }

    /**
     * Assert that the test with the specified identifier was skipped.
     *
     * @param identifier the `UniqueId` of the test
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertSkipped(UniqueId identifier) {
        assert:test skipped.contains(identifier);
        return this;
    }

    /**
     * Assert that the test with the specified identifier was skipped.
     *
     * @param identifier  the `UniqueId` of the test
     * @param expected    the expected skip reason message
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertSkipped(UniqueId identifier, String? expected) {
        if (String actualReason := skipped.get(identifier)) {
            assert:test actualReason == expected;
            return this;
        }
        throw new Assertion($"Test with identifier {identifier} was not skipped");
    }

    /**
     * Assert that the test with the specified identifier was not skipped.
     *
     * @param identifier the `UniqueId` of the test
     *
     * @return this `AssertingExecutionListener` so that assertions can be chained
     */
    AssertingExecutionListener assertNotSkipped(UniqueId identifier) {
        assert:test !skipped.contains(identifier);
        return this;
    }

    TestState forTest(UniqueId identifier) {
        return new TestState(identifier, started.contains(identifier), completed[identifier],
                skipped[identifier], errors[identifier], []);
    }


    @Override
	void onStarted(Model model) {
	    try {
	        assert !started.contains(model.uniqueId);
	        assert !completed.contains(model.uniqueId);
	        started.add(model.uniqueId);
	    } catch (Exception e) {
	        errors.put(model.uniqueId, e);
	    }
	}

    @Override
	void onCompleted(Model model, Result result) {
	    try {
	        assert started.contains(model.uniqueId);
	        assert !completed.contains(model.uniqueId);
	        completed.put(model.uniqueId, result);
	    } catch (Exception e) {
	        errors.put(model.uniqueId, e);
	    }
	}

    @Override
	void onSkipped(Model model, String reason) {
	    try {
	        assert !skipped.contains(model.uniqueId);
	        skipped.put(model.uniqueId, reason);
	    } catch (Exception e) {
	        errors.put(model.uniqueId, e);
	    }
	}

    @Override
	void onPublished(Model model, ReportEntry entry) {
	    reportEntries.add(Tuple:(model.uniqueId, entry));
	}


	static const TestState(UniqueId identifier, Boolean started, Result? result, String? skippedReason,
	        Exception? error, ExecutionListener.ReportEntry[] reports) {

        /**
         * Assert that the test completed normally with no errors.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertSuccessfulTest() {
            return assertNoErrors()
                    .assertNotSkipped()
                    .assertStarted()
                    .assertCompleted()
                    .assertResult(result -> {
                        assert:test result.exception == Null;
                        assert:test result.status == Successful;
                        assert:test result.duration != Duration.None;
                    });
        }

        /**
         * Assert that the test failed with no errors.
         *
         * @param errorType  the expected failure exception type
         *
         * @return this `TestState` so that assertions can be chained
         */
        <Error extends Exception> TestState assertFailedTest(Type<Error> errorType) {
            return assertNoErrors()
                    .assertNotSkipped()
                    .assertStarted()
                    .assertCompleted()
                    .assertResult(result -> {
                        assert:test result.status == Failed;
                        assert:test result.exception.is(errorType);
                        assert:test result.duration != Duration.None;
                        });
        }

        /**
         * Assert that the test was skipped with no errors.
         *
         * @param reason  the expected skip reason message
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertSkippedTest(String reason) {
            return assertNoErrors()
                    .assertSkipped(reason)
                    .assertNotStarted()
                    .assertNotCompleted();
        }

        /**
         * Assert that no errors have occurred.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertNoErrors() {
            assert:test error == Null;
            return this;
        }

        /**
         * Assert that the test was completed and then run the specified
         * function with the completed `Result`.
         *
         * @param fn  the function to call with the test `Result`
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertResult(function void (Result) fn) {
            if (result.is(Result)) {
                fn(result);
            } else {
                throw new Assertion($"Test with identifier {identifier} was not completed");
            }
            return this;
        }

        /**
         * Assert that the test was started.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertStarted() {
            assert:test started;
            return this;
        }

        /**
         * Assert that the test was not started.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertNotStarted() {
            assert:test !started;
            return this;
        }

        /**
         * Assert that the test was completed.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertCompleted() {
            assert:test result != Null;
            return this;
        }

        /**
         * Assert that the test was not completed.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertNotCompleted() {
            assert:test result == Null;
            return this;
        }

        /**
         * Assert that the test was skipped.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertSkipped() {
            assert:test skippedReason != Null;
            return this;
        }

        /**
         * Assert that the test was not skipped.
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertNotSkipped() {
            return assertSkipped(Null);
        }

        /**
         * Assert that the test was skipped.
         *
         * @param reason  the expected skip reason message
         *
         * @return this `TestState` so that assertions can be chained
         */
        TestState assertSkipped(String? reason) {
            assert:test skippedReason == reason;
            return this;
        }
	}
}