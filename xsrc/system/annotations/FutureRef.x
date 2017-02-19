/**
 * A FutureRef represents a result that may be asynchronously provided, allowing the caller to
 * indicate a response to the result.
 *
 *   service Pi
 *       {
 *       String calc(Int digits)
 *           {
 *           String value;
 *           // some calculation code goes here
 *           // ...
 *           return value;
 *           }
 *       }
 *
 *   Void test(Console console)
 *       {
 *       Pi pi = new Pi();
 *
 *       // blocking call to the Pi calculation service - wait for 100 digits
 *       console.print(pi.calc(100));
 *
 *       // potentially async call to the Pi calculation service
 *       @future String fs = pi.calc(99999);
TODO update examples
 *       }
 */
mixin FutureRef<RefType>
        into Ref<RefType>
    {
    /**
     * Future completion status:
     * * Pending: The future has not completed.
     * * Result: The future completed because the operation returned successfully.
     * * Error: The future completed because the operation threw an exception (which may indicate
     *   that the operation timed out).
     */
    private enum Completion {Pending, Result, Error};

    /**
     * Tracks whether and how the future has completed.
     */
    private Completion completion = Pending;

    /**
     * True if the value of the future can be set.
     */
    private Boolean assignable;

    /**
     * The exception, if the future completes exceptionally.
     */
    private Exception? failure;

    /**
     * The function type used to notify dependent futures.
     */
    typedef function Void (Completion, RefType?, Exception?) NotifyDependent;

    /**
     * The future that is chained to this future, that this future sends its completion result to.
     */
    protected NotifyDependent? chained;

    /**
     * Determine if the future has completed, either successfully or exceptionally.
     */
    Boolean completed.get()
        {
        return completion != Pending;
        }

    /**
     * Perform a non-blocking examination of the future:
     * * Peek returns negatively iff the future has not completed.
     * * Peek throws an exception iff the future completed exceptionally.
     * * Peek returns positively with the result iff the future has completed successfully.
     */
    @Override
    conditional RefType peek()
        {
        if (completion == Pending)
            {
            return false;
            }

        return true, get();
        }

    /**
     * Perform a blocking wait-for-result of the future:
     * * A call to get() will block until the future has completed (subject to any time-outs being
     *   enforced by the runtime).
     * * If the future completes exceptionally, then get() throws an exception. Note that a time-out
     *   results in a TimeoutException.
     * * If the future completes successfully, get() returns the result of the future.
     */
    @Override
    RefType get()
        {
        while (completion == Pending)
            {
            this:service.yield();
            }

        if (completion == Error)
            {
            throw (Exception) failure;
            }

        return super;
        }

    @Override
    Void set(RefType value)
        {
        assert assignable;
        super(value);
        }

    /**
     * TODO
     */
    Future.Type<RefType> thenDo(function Void () run)
        {
        return chain(new ThenDoStep<RefType>(run));
        }

    /**
     * TODO
     */
    Future.Type<RefType> passTo(function Void (RefType) consume)
        {
        return chain(new PassToStep<RefType>(consume));
        }

    /**
     * TODO
     */
    Future.Type<RefType> handle(function RefType (Exception) convert)
        {
        return chain(new HandleStep<RefType>(convert));
        }

    /**
     * TODO
     */
    Future.Type<RefType> whenComplete(function Void (RefType?, Exception?) notify)
        {
        return chain(new WhenCompleteStep<RefType>(notify));
        }

    /**
     * TODO
     */
    Future.Type<RefType> or(Future.Type<RefType> other)
        {
        return chain(new OrStep<RefType>(other));
        }

    /**
     * TODO
     */
    Future.Type<RefType> orAny(Future.Type<RefType> ... others)
        {
        Future.Type<RefType> result = this;
        others.forEach(other -> result = result.or(other));
        return result;
        }

    /**
     * TODO
     */
    <NewType> Future.Type<NewType> and(Future.Type other,
            function Future.Type<NewType> (RefType, other.RefType) combine = v1, v2 -> (v1, v2))
        {
        return chain(new AndStep<NewType, RefType, other.RefType>(other, combine));
        }

    /**
     * TODO
     */
    <NewType> Future.Type<NewType> transform(function NewType (RefType) convert)
        {
        return chain(new TransformStep<NewType, RefType>(convert));
        }

    /**
     * TODO
     */
    <NewType> Future.Type<NewType> transform(function NewType (RefType?, Exception?) convert)
        {
        return chain(new Transform2Step<NewType, RefType>(convert));
        }

    /**
     * TODO
     */
    <NewType> Future.Type<NewType> createContinuation(function Future.Type<NewType> (RefType) create)
        {
        // TODO - need to verify that this method should exist, and be able to explain what it does
        return chain(new ContinuationStep<>(create));
        }

    /**
     * Cause the future to complete successfully with a result, if the future has not already
     * completed.
     */
    Void complete(RefType result)
        {
        if (completion == Pending)
            {
            completion = Result;
            assignable = true;
            try
                {
                set(result);
                }
            finally
                {
                assignable = false;
                }
            thisCompleted(result, null);
            }
        }

    /**
     * Cause the future to complete exceptionally with an exception, if the future has not already
     * completed.
     */
    Void completeExceptionally(Exception e)
        {
        if (completion == Pending)
            {
            completion = Error;
            failure    = e;
            thisCompleted(null, e);
            }
        }

    /**
     * Internal method that has the once-and-only-once behavior associated with the future's
     * completion.
     */
    protected Void thisCompleted(RefType? result, Exception? e)
        {
        // by default, the only completion logic is to chain the completion
        chained?(completion, result, e);
        chained = null;
        }

    /**
     * TODO
     */
    Future.Type<future.RefType> chain(DependentFuture future)
        {
        chain(future.parentCompleted);
        return future;
        }

    /**
     * TODO
     */
    Void chain(NotifyDependent notify)
        {
        switch (completion)
            {
            case Pending:
                if (chained == null)
                    {
                    chained = notify;
                    }
                else
                    {
                    chained = new MultiCompleter(chained, notify).parentCompleted;
                    }
                break;

            case Result:
                // this future has already completed, so notify the dependent
                notify(Result, get(), null);
                break;

            case Error:
                // this future has already completed, so notify the dependent
                notify(Error, null, failure);
                break;
            }
        }

    /**
     * A DependentFuture is the base class for making simple futures that are dependent on the
     * result of another future. Specifically, a future invokes the {@link chained} method of the
     * DependentFuture, which in turn completes the future, which in turn invokes the next in the
     * chain.
     */
    static class DependentFuture<RefType, InputType>
            implements Ref<RefType>
            incorporates Future<RefType>
            delegates Ref<RefType>(&result)
        {
        @Override
        Boolean assigned.get()
            {
            return completed;
            }

        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            assert completion != Pending;
            if (completion == Result)
                {
                complete((RefType) input);
                }
            else
                {
                completeExceptionally((Exception) e);
                }
            }

        private RefType? result = null;
        }

    /**
     * A MultiCompleter multiplexes a single dependent completion into multiple chained completions.
     * A MultiCompleter is not intended to be used as a normal future, but rather is used by other
     * figures solely to multiplex their own completion dependencies.
     */
    static class MultiCompleter<RefType>
            extends DependentFuture<RefType, RefType>
        {
        construct MultiCompleter(NotifyDependent first, NotifyDependent second)
            {
            chained  = first;
            chained2 = second;
            }

        protected NotifyDependent? chained2;

        @Override
        Void chain(NotifyDependent chain)
            {
            // the MultiCompleter is used by other future instances to implement chaining, but
            // the MultiCompleter is not intended to be visible outside of those futures, and it
            // does not have its own dependents
            assert;
            }

        @Override
        protected Void thisCompleted(RefType? result, Exception? e)
            {
            super(result, e);

            chained2?(completion, result, e);
            chained2 = null;
            }
        }

    /**
     * A dependent future that runs a function on the successful completion of its parent future,
     * and on successful completion of that function, this future completes with the same value as
     * its parent.
     *
     * If the parent completed exceptionally, or if the parent completed successfully but the
     * {@link run} function throws an exception, then this future completes exceptionally.
     */
    static class ThenDoStep<RefType>(function Void () run)
            extends DependentFuture<RefType, RefType>
        {
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            if (!this.completed && completion == Result)
                {
                try
                    {
                    run();
                    complete(input);
                    }
                catch (Exception e2)
                    {
                    completeExceptionally(e2);
                    }
                }
            else
                {
                super(completion, input, e);
                }
            }
        }

    /**
     * A dependent future that calls a function consuming the parent's result on the successful
     * completion of its parent future, and on successful completion of that function, this future
     * completes with the same value as its parent.
     *
     * If the parent completed exceptionally, or if the parent completed successfully but the
     * {@link consume} function throws an exception, then this future completes exceptionally.
     */
    static class PassToStep<RefType>(function Void (RefType) consume)
            extends DependentFuture<RefType, RefType>
        {
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            if (!this.completed && completion == Result)
                {
                try
                    {
                    consume(input);
                    complete(input);
                    }
                catch (Exception e2)
                    {
                    completeExceptionally(e2);
                    }
                }
            else
                {
                super(completion, input, e);
                }
            }
        }

    /**
     * Unlike most dependent futures, this dependent future handles the case in which its parent
     * completed _exceptionally_. If the parent completed exceptionally, then this future will call
     * a function to handle that exception, converting it to a usable value, and if the function
     * completes successfully, then this future will complete with that value.
     *
     * If the parent completed successfully, then this future completes successfully with the same
     * result.
     *
     * If the parent completed exceptionally and the {@link convert} function throws an exception,
     * then this future completes exceptionally.
     */
    static class HandleStep<RefType>(function RefType (Exception) convert)
            extends DependentFuture<RefType, RefType>
        {
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            if (!this.completed && completion == Error)
                {
                try
                    {
                    complete(convert(e));
                    }
                catch (Exception e2)
                    {
                    completeExceptionally(e2);
                    }
                }
            else
                {
                super(completion, input, e);
                }
            }
        }

    /**
     * A dependent future that runs a function on the completion of its parent future, providing the
     * function with the result if the parent completed successfully, and the exception if the
     * parent completed exceptionally.
     *
     * If the parent completed successfully, and the {@link notify} function does not throw an
     * exception, then this future completes successfully with the same result.
     *
     * If the parent completed exceptionally, or if the parent completed successfully but the
     * {@link notify} function throws an exception, then this future completes exceptionally.
     */
    static class WhenCompleteStep<RefType>(function Void (RefType?, Exception?) notify)
            extends DependentFuture<RefType, RefType>
        {
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            if (!this.completed && completion != Pending)
                {
                try
                    {
                    notify(input, e)
                    }
                catch (Exception e2)
                    {
                    completeExceptionally(e2);
                    return;
                    }
                }

            super(completion, input, e);
            }
        }

    /**
     * An OrStep is simply a junction point of two parents in which the first to signal completion
     * to this future will cause it to complete. In other words, only one _or_ the other needs to
     * complete in order for this future to complete.
     *
     * This future will complete iff one or more of its parents completes:
     * * If one of the parents completes successfully, then this future may complete successfully.
     * * If one of the parents completes exceptionally, then this future may complete exceptionally.
     * * Generally, it is expected that the first parent that notifies this future of the parent's
     *   completion will cause this future to complete.
     */
    static class OrStep<RefType>
            extends DependentFuture<RefType, RefType>
        {
        construct (Future.Type<RefType> other)
            {
            // TODO is this illegal by using "this"?
            other.whenComplete((result, e) -> parentCompleted(e == null ? Result : Error, result, e));
            }
        }

    /**
     * An AndStep is simply a junction point of two parents in which the first to signal exceptional
     * completion or the second to signal successful completion will cause it to complete. In other
     * words, both of the parents must complete successfully in order for this future to complete
     * successfully.
     */
    static class AndStep<RefType, InputType, Input2Type>
            extends DependentFuture<RefType, InputType>
        {
        construct (Future.Type<Input2Type> other, function RefType (InputType, Input2Type) combine)
            {
            // TODO is this illegal by using "this"?
            other.whenComplete((result, e) -> parent2Completed(e == null ? Result : Error, result, e));
            }

        public/private function Future.Type<NewType> (RefType, other.RefType) combine;

        // TODO / REVIEW - conditional vs. Nullable
        private conditional InputType input1;
        private conditional InputType input2;

        /**
         * Handle the completion of the first parent.
         */
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            if (!completed && !input1)
                {
                assert completion != Pending;

                if (completion == Error)
                    {
                    completeExceptionally((Exception) e);
                    }
                else
                    {
                    input1 = true, (InputType) input;
                    if (input2)
                        {
                        bothParentsCompleted();
                        }
                    }
                }
            }

        /**
         * Handle the completion of the second parent.
         */
        Void parent2Completed(Completion completion, Input2Type? input, Exception? e)
            {
            if (!completed && !input2)
                {
                assert completion != Pending;

                if (completion == Error)
                    {
                    completeExceptionally((Exception) e);
                    }
                else
                    {
                    input2 = true, (Input2Type) input;
                    if (input1)
                        {
                        bothParentsCompleted();
                        }
                    }
                }
            }

        /**
         * Handle the successful completion of both parents.
         */
        private Void bothParentsCompleted()
            {
            assert input1 && input2;
            try
                {
                complete(combine(input1[1], input2[1]));
                }
            catch (Exception e)
                {
                completeExceptionally(e);
                }
            }
        }

    /**
     * A dependent future that uses a provided {@link convert} function to convert the result of the
     * parent future from {@link InputType} to {@link RefType}, and then use the result value from
     * the conversion as the completion value for this future.
     *
     * If the parent completed exceptionally, or if the parent completed successfully but the
     * {@link convert} function throws an exception, then this future completes exceptionally.
     */
    static class TransformStep<RefType, InputType>(function RefType (InputType) convert)
            extends DependentFuture<RefType, InputType>
        {
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            assert completion != Pending;

            if (completion == Result)
                {
                if (!this.completed)
                    {
                    try
                        {
                        complete(convert(input));
                        }
                    catch (Exception e2)
                        {
                        completeExceptionally(e2);
                        }
                    }
                }
            else
                {
                super(completion, null, e);
                }
            }
        }

    /**
     * A dependent future that uses a provided {@link convert} function to convert the result of the
     * parent future from {@link InputType} to {@link RefType}, and then use the result value from
     * the conversion as the completion value for this future.
     *
     * If the parent completed exceptionally, or if the parent completed successfully but the
     * {@link convert} function throws an exception, then this future completes exceptionally.
     */
    static class Transform2Step<RefType, InputType>(function RefType (InputType, Exception) convert)
            extends DependentFuture<RefType, InputType>
        {
        @Override
        Void parentCompleted(Completion completion, InputType? input, Exception? e)
            {
            if (!this.completed)
                {
                assert completion != Pending;
                try
                    {
                    complete(convert(input, e));
                    }
                catch (Exception e2)
                    {
                    completeExceptionally(e2);
                    }
                }
            else
                {
                super(completion, input, e);
                }
            }
        }
    }
