module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        Int result;
        try
            {
            result = f();
            }
        catch (Exception e)
            {
            result = 0;
            }
        console.println(result);
        }

    Int f()
        {
        return 42;
        }
    }
