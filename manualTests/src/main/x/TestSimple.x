module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Inject Random random;

        console.print(random.fill(new Byte[8])); // used to throw
        }
    }