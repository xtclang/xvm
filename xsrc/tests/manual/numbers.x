module TestNumbers.xqiz.it
    {
    import X.numbers.Int8;
    import X.numbers.Int128;
    import X.numbers.UInt128;

    @Inject X.io.Console console;

    void run()
        {
        testUInt();
        testByte();
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

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }

        Int un1 = Int.maxvalue.toInt().toUnchecked();
        Int un2 = un1 + 1;

        assert un2 == Int.minvalue; // wraps around w/out exception
        assert un2.is(@Unchecked Int);

        UInt un3 = UInt.maxvalue.toUInt().toUnchecked();
        UInt un4 = ++un3;
        assert un4 == 0;

        assert un4.is(@Unchecked UInt);
        }

    void testByte()
        {
        console.println("\n** testByte()");

        Byte n1 = 42;
        console.println("n1=" + n1);

        Byte n2 = 0xFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        Byte d3 = n2 / 10;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*10 + n2 % 10));

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }

        // Byte == UInt8
        Byte un1 = Byte.maxvalue.toByte().toUnchecked();
        Byte un2 = un1 + 1;

        assert un2 == 0; // wraps around w/out exception
        assert un2.is(@Unchecked Byte);

        Int8 un3 = Int8.maxvalue.toInt8().toUnchecked();
        Int8 un4 = ++un3;
        assert un4 == Int8.minvalue;

        assert un4.is(@Unchecked Int8);
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

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }
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

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }
        }
    }