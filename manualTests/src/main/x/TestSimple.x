module TestSimple
    {
    @Inject Console console;

    void run()
        {
        DateTime? dt1 = Null;
        DateTime? dt2 = DateTime.EPOCH;

        Ordered order = dt1? <=> dt2? : switch (dt1, dt2)  // this used to NPE
            {
            case (Null, Null): Equal;
            case (Null, _): Greater;
            case (_, Null): Lesser;
            default: assert;
            };

        console.println(order);
        }
    }