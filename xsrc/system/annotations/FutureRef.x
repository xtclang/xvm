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
 *       fs.onResult(value -> console.print(value));
 *       fs.onThrown(e -> console.print(e.to<String>()));
 *       fs.onExpiry(() -> console.print("it took too long!"));
 *       fs.onFinish(() -> console.print("done"));
 *       }
 */
mixin FutureRef<RefType>
        into Ref<RefType>
    {
    /**
     * Future completion:
     * * Pending: The future has not completed.
     * * Result: The future completed because the operation returned successfully.
     * * Error: The future completed because the operation threw an exception (which may indicate
     *   that the operation timed out).
     */
    private enum Completion {Pending, Result, Error};
    private Completion completion = Pending;
    private (RefType | Exception)? result = null;

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
    RefType get()
        {
        while (completion == Pending)
            {
            this:service.yield();
            }

        if (completion == Error)
            {
            throw
            }
        }

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
    Void completeExceptionally(Exception e)
        {
        if (completion == Pending)
            {
            }
        }

    // impl


    }

    public/private Exception? exception  = null;
    public/private Boolean    expired    = false;
    private        Boolean    assignable = false;

    function Void (RefType)?    onResult = null;
    function Void (Exception)?  onThrown = null;
    function Void ()?           onExpiry = null;
    function Void (Completion)? onFinish = null;

    RefType get()
        {
        }

    Void set(RefType value)
        {
        assert !assigned && assignable;
        super(value);
        }

    Void clear()
        {
        super();

        completion = Pending;
        exception  = null;
        expired    = false;
        }

    Void begin()
        {
        clear();
        }

    Void complete(RefType value)
        {
        assert !assigned && completion == Pending;

        try
            {
            completion = Result;
            assignable = true;
            set(value);
            }
        finally
            {
            assignable = false;
            }

        onResult?(value);
        onFinish?(completion);
        }

    Void completeExceptionally(Exception e)
        {
        assert !assigned && completion == Pending;
        exception  = e;
        completion = Thrown;

        onThrown?(e);
        onFinish?(completion);
        }

    Void timedOut()
        {
        assert !assigned && completion == Pending;
        expired    = true;
        completion = Expiry;

        onExpiry?();
        onFinish?(completion);
        }
    }
