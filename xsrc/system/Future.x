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
    enum Status {Pending, Processing, Complete};
    public/private Status status;

    public/private function Void ()? complete;
    public/private function Void (RefType)? notifyValue;
    public/private function Void (Exception)? notifyException;
    public/private function Void ()? notifyTimeout;

    Void begin()
        {
        assert !assigned;
        status = Pending;
        }

    Void set(RefType value)
        {
        super(value);
        status = Complete;

        if (notify != null)
            {
            }
        }

    Void complete(RefType value)
        {
        set(value);
        }

    Void completeExceptionally(Exception e)
        {
        }

    Void abandon()
        {
        }

    /**
     * TODO / review
     */
    Void interrupt()

    }
