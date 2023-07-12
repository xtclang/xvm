import xunit.PreconditionFailed;

/**
 * An `ExceptionCollector` collects exceptions that may be thrown during execution
 * of a test fixture.
 *
 * @param abortExecution  an `AbortExecutionPredicate` to determine whether to abort
                          test execution when an exception is thrown
 */
class ExceptionCollector(AbortExecutionPredicate abortExecution = (e) -> False) {
    /**
     * An `AbortExecutionPredicate` is a predicate `Function` to determine whether
     * to abort test execution give a specified `Exception`
     */
    typedef function Boolean (Exception) as AbortExecutionPredicate;

    /**
     * An `Exception` thrown by the test fixture.
     */
    Exception? exception = Null;

    /**
     * An optional array of suppressed `Exception` thrown by the test fixture.
     */
    Exception[]? suppressed = Null;

    /**
     * @return `True` iff this collector does not contain an `Exception`
     */
    Boolean empty.get() {
        return exception == Null;
    }

    /**
     * @return `True` iff this collector contains a `PreconditionFailed`
     */
    Boolean skipped.get() {
        return exception.is(PreconditionFailed);
    }

    /**
     * @return `True` iff this collector contains an `Exception`
     * that is not a `PreconditionFailed`.
     */
    Boolean failed.get() {
        return exception != Null && !skipped;
    }

    /**
     * @return the `Result` of the test execution based on the state
     *         of this collector
     */
    Result result.get() {
        Exception? exception = this.exception;
        if (exception.is(Exception)) {
            if (exception.is(PreconditionFailed)) {
                return new Result(Skipped, exception, suppressed);
            }
            if (hasAbortedExecution(exception)) {
                return new Result(Aborted, exception, suppressed);
            }
            return new Result(Failed, exception, suppressed);
        }
        return Result.Passed;
    }

    /**
     * Execute a void no-args `Function` collecting any thrown exceptions.
     *
     * @param fn  the `Function` to execute
     */
    Boolean executeVoid(function void () fn) {
        return executeObject(() -> {
            fn();
            return Null;
        });
    }

    /**
     * Execute a `Function` that returns a conditional `Result`, collecting any thrown exceptions.
     *
     * @param fn  the `Function` to execute
     *
     * @return `True` iff the `Function` ran successfully or `False` if the `Function` threw an `Exception`
     * @return the `Result` returned by the `Function`
     */
    conditional Object executeObject(Function<<>, <Object>> fn) {
        try {
            return True, fn();
        }
        catch (Exception e) {
            add(e);
        }
        return False;
    }

    /**
     * Execute a `Function` that returns a conditional `Result`, collecting any thrown exceptions.
     *
     * @param fn  the `Function` to execute
     *
     * @return `True` iff the `Function` ran successfully or `False` if the `Function` threw an `Exception`
     * @return the `Result` returned by the `Function`
     */
    <Result> conditional Result execute(Function<<>, <Result>> fn) {
        try {
            return True, fn();
        }
        catch (Exception e) {
            add(e);
        }
        return False;
    }

	/**
	 * Add the specified `Exception` to this collector.
	 *
	 * @param e  the `Exception` to add
	 */
	private void add(Exception e) {
		if (this.exception == Null) {
			this.exception = e;
		}
		else if (hasAbortedExecution(this.exception) && !hasAbortedExecution(e)) {
			addSuppressed(this.exception);
			this.exception = e;
		}
		else if (this.exception != e) {
			addSuppressed(e);
        }
    }

    /**
	 * Add the specified `Exception` to the array of suppressed exceptions.
	 *
	 * @param e  the `Exception` to add
     */
    private void addSuppressed(Exception? e) {
        if (e.is(Exception)) {
            Exception[]? suppressed = this.suppressed;
            if (suppressed.is(Exception[])) {
                suppressed = suppressed + e;
            }
            else {
                suppressed = [e];
            }
        }
    }

    /**
     * @return `True` if this collector has aborted operation due to a thrown `Exception`.
     */
    private Boolean hasAbortedExecution(Exception? e) {
        if (e.is(Exception)) {
            return abortExecution(e);
        }
        return False;
    }
}
