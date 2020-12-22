module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Inject("random")     Random r0;
        @Inject("random", 42) Random r1;

        console.println("random");
        for (Int i : [0..2))
            {
            console.println($"r0={r0.byte()}");
            }

        console.println("pseudo");
        for (Int i : [0..2))
            {
            console.println($"r1={r1.byte()}");
            }
        }
    }
