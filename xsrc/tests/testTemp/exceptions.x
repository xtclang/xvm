module TestTry.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Exception tests:");

        try
            {
            testBasic();
            }
        catch (Exception e)
            {
            console.println("UNEXPECTED THROW in testBasic(): " + e);
            }

        try
            {
            testUsing();
            }
        catch (Exception e)
            {
            console.println("UNEXPECTED THROW in testUsing(): " + e);
            }

        try
            {
            testFinally();
            }
        catch (Exception e)
            {
            console.println("expected throw in testFinally(): " + e);
            }
        }

    void testThrow()
        {
        console.println("in testThrow()");
        throw new X.IllegalStateException("test");
        }

    void testBasic()
        {
        console.println("\n** testBasic()");

        try
            {
            testThrow();
            console.println("DIDN'T THROW!");
            }
        catch (Exception e)
            {
            console.println("caught: " + e);
            }

        console.println("done testBasic()");
        }

    void testUsing()
        {
        console.println("\n** testUsing()");

        try
            {
            using (ByeBye bye = new ByeBye())
                {
                testThrow();
                console.println("DIDN'T THROW!");
                }
            }
        catch (Exception e)
            {
            console.println("ok");
            }

        console.println("done");
        }

    class ByeBye
            implements X.Closeable
        {
        construct()
            {
            console.println("hello!");
            }

        @Override
        void close()
            {
            console.println("bye-bye!");
            }
        }

    void testFinally()
        {
        console.println("\n** testFinally()");

        FOR: for (Int i : 1..2)
            {
            console.println("iteration " + i);
            TRY: try
                {
                if (FOR.last)
                    {
                    console.println("throwing exception inside try");
                    testThrow();
                    }
                else
                    {
                    console.println("not throwing exception inside try");
                    }
                }
            finally
                {
                console.println("exception in finally: " + TRY.exception);
                }
            }

        console.println("done testFinally() - which shouldn't happen!");
        }
    }