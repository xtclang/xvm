module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        console.println("Starting");

        Int i = 0;
        Int j = 3;

        Boolean f1 = i < 1 < j < 5;
        Boolean f2 = i <= j < 5;
        Boolean f3 = Int.minvalue <= i <= Int.maxvalue;
        console.println($"{f1} {f2} {f3} ");
        }
    }

