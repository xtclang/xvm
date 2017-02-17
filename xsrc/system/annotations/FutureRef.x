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
     * Construct the future reference with any number (0-4) of the optional notification functions:
     * * {@link onResult}
     * * {@link onThrown}
     * * {@link onExpiry}
     * * {@link onFinish}
     */
    construct((function Void (RefType) | function Void (Exception) | function Void ()
            | function Void (Completion)) ... functions)
        {
        for ((function Void (RefType) | function Void (Exception) | function Void ()
                | function Void (Completion)) fn : functions)
            {
            switch (&fn.ActualType)
                {
                case function Void (RefType):
                    assert onResult = null;
                    onResult = fn;
                    break;

                case function Void (Exception):
                    assert onThrown = null;
                    onThrown = fn;
                    break;

                case function Void ():
                    assert onExpiry = null;
                    onExpiry = fn;
                    break;

                case function Void (Completion):
                    assert onFinish = null;
                    onFinish = fn;
                    break;
                }
            }
        }

    /**
     * Future completion:
     * * Pending: The future has not completed.
     * * Result: The future completed because the operation returned successfully.
     * * Thrown: The future completed because the operation threw an exception.
     * * Expire: The future completed because the operation timed out.
     */
    enum Completion {Pending, Result, Thrown, Expiry};

    public/private Completion completion = Pending;
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
