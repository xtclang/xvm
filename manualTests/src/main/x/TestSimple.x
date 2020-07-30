module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        Int bitCount = 1;
        if (1 << bitCount & 0x2 != 0)
            {
            console.println("yes");
            }
        }

    Int f()
        {
        return 42;
        }
    }
