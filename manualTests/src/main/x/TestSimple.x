module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        console.println();

        Boolean    flag      = False;
        Exception? exception = Null;
        try
            {
            if (flag)
                {
                return;
                }
            }
        catch (Exception e)
            {
            exception = e;
            }

        if (exception != Null)
            {
            console.println(exception);
            }

        Int lo;
        try
            {
            flag = False;
            }
        catch (OutOfBounds e)
            {
            return;
            }
        finally
            {
            lo = 2;
            }

        console.println(lo + 1);
        }
    }
