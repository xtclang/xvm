module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        using (new Bob())
            {
            console.println("using Bob");
            }
        }

    class Bob
            implements Closeable
        {
        construct()
            {
            console.println("constructing Bob");
            }
        @Override void close(Exception? cause = Null)
            {
            console.println("closing Bob");
            }
        }
    }