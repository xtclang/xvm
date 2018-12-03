module TestNumbers.xqiz.it
    {
    import X.Int128;
    import X.UInt128;

    @Inject X.io.Console console;

    void run()
        {
        testUInt();
        testInt128();
        testUInt128();
        }

    void testUInt()
        {
        console.println("\n** testUInt()");

        UInt n1 = 42;
        console.println("n1=" + n1);

        UInt n2 = 0xFFFF_FFFF_FFFF_FFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        UInt d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));
        }

    void testInt128()
        {
        console.println("\n** testInt128()");

        Int128 n1 = 42;
        console.println("n1=" + n1);

        Int128 n2 = 0x7FFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        Int128 d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));

        console.println("-------");

        Int128 n4 = -n2 - 1;
        console.println("n4=" + n4);
        console.println("+1=" + (++n4));
        console.println("-1=" + (--n4));

        Int128 d4 = n4 / 1000;
        console.println("d4=" + d4);
        console.println("n4=" + (d4*1000 + n4 % 1000));
        }

    void testUInt128()
        {
        console.println("\n** testUInt128()");

        UInt128 n1 = 42;
        console.println("n1=" + n1);

        UInt128 n2 = 0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        UInt128 d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));
        }
    }