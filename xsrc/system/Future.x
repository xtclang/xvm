interface Future<RefType>
    {
    /**
     * Perform a non-blocking examination of the future:
     * * Peek returns negatively iff the future has not completed.
     * * Peek throws an exception iff the future completed exceptionally.
     * * Peek returns positively with the result iff the future has completed successfully.
     */
    conditional RefType peek();

    /**
     * Determine if the future has completed, either successfully or exceptionally.
     */
    @ro Boolean completed;

    /**
     * Perform a blocking wait-for-result of the future:
     * * A call to get() will block until the future has completed (subject to any time-outs being
     *   enforced by the runtime).
     * * If the future completes exceptionally, then get() throws an exception. Note that a time-out
     *   results in a TimeoutException.
     * * If the future completes successfully, get() returns the result of the future.
     */
    RefType get();

    Future.Type<RefType> thenDo(function Void () run);

    Future.Type<RefType> passTo(function Void (RefType) consume);

    Future.Type<RefType> handle(function RefType (Exception) convert);

    Future.Type<RefType> whenComplete(function Void (RefType?, Exception?) notify);

    Future.Type<RefType> or(Future.Type<RefType> other);

// TODO think about whether this is the right way to do this
    Future.Type<RefType> orAny(Future.Type<RefType> ... others)
        {
        Future.Type<RefType> result = this;
        others.forEach(other -> result = result.or(other));
        return result;
        }

    <NewType> Future.Type<NewType> and(Future.Type other,
            function Future.Type<NewType> (RefType, other.RefType) combine = v1, v2 -> (v1, v2));

    <NewType> Future.Type<NewType> transform(function NewType (RefType) convert);

    <NewType> Future.Type<NewType> transform(function NewType (RefType?, Exception?) convert);

    <NewType> Future.Type<NewType> createContinuation(
            function Future.Type<NewType> (RefType) create);

    /**
     * Cause the future to complete successfully with a result, if the future has not already
     * completed.
     */
    Void complete(RefType result);

    /**
     * Cause the future to complete exceptionally with an exception, if the future has not already
     * completed.
     */
    Void completeExceptionally(Exception e);
    }
