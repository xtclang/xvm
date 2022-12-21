module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        @Inject Random rnd;

        Bit[] bits0 = new Bit[12] (i-> 1);
        report(bits0, "original");

        Bit[] bits1 = bits0[1..3];
        report(bits1, "slice");

        rnd.fill(bits1); // used to assert
        report(bits1, "slice rnd'ed");

        report(bits0, "original");
        }

    void report(Bit[] bits, String descr)
        {
        console.println(descr);
        console.println(bits);
        console.println();
        }
    }