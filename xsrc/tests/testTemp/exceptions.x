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
            console.println("UNEXPECTED THROW!");
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
    }