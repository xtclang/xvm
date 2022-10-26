module TestAsync
    {
    @Inject Console console;

    void run()
        {
        for (Int i : 1..3)
            {
            new Async(i).foo();         // synchronous call
            // new Async(i).foo^();     // asynchronous call
            }
        }

    service Async(Int id, Int count=5)
        {
        void foo()
            {
            for (Int i : 1 .. count)
                {
                console.println($"Async:{id} #{i}");
                }
            }
        }
    }
