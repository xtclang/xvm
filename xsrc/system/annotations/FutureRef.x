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
mixin FutureRef<RefType>((function Void () | function Void (RefType))? notify)
        into Ref<RefType>
    {
    enum Completion {Pending, Result, Thrown, Expiry};
    public/private Completion completion = Pending;
    public/private Exception? exception;
    public/private Boolean    expired;

    private Boolean    assignable;

    function Void (RefType)? onResult;
    function Void (Exception)? onThrown;
    function Void ()? onExpiry;
    function Void (Completion)? onFinish;

    Void begin()
        {
        clear();
        }

    Void clear()
        {
        super();
        eThrown = null;
        }

    Void set(RefType value)
        {
        assert !assigned && assignable;
        super(value);
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
        onFinish?(Result);
        }

    Void completeExceptionally(Exception e)
        {
        assert !assigned && completion == Pending;
        exception  = e;
        completion = Thrown;

        onThrown?(e);
        onFinish?(Thrown);
        }

    Void timedOut()
        {
        assert !assigned && completion == Pending;
        expired    = true;
        completion = Expiry;

        onExpiry?();
        onFinish?(Expiry);
        }
    }
