module TestSimple
    {
    @Inject Console console;
    void run()
        {
        console.println(factorial(Int128:30));
        }

    static <Value extends Number> Value factorial(Value n)
        {
        if (n <= Value.one())
            {
            return n;
            }
        return n * factorial(n - Value.one()); // this used to fail to compile
        }
    }