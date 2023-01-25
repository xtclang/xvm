module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Exception? ex = Null;
        try
            {
            new S(1);
            }
        catch (Exception e)
            {
            ex = e;
            }
        assert ex != Null;
        console.print($"Caught: {ex.text}");
        }

    service S
        {
        construct(Int n)
            {
            new S(n); // used to throw up a native StackOverflowError
            }
        }
    }