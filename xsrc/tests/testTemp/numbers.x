module TestNumbers.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        testUInt();
        }

    void testUInt()
        {
        console.println("\n** testUInt()");

        UInt n1 = 42;
        console.println("n1=" + n1);

        UInt n2 = 0xFFFFFFFFFFFFFFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        UInt d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));
        }
    }