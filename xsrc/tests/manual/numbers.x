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
        testFloat64();
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

    void testFloat64()
        {
        console.println("\n** testFloat64()");

        Float n1 = 4.2;
        console.println("n1=" + n1);

        Float n2 = n1 + 1;
        console.println("-1=" + n2);
        console.println("+1=" + (n2 - 1));

        Float n3 = n1*10;
        console.println("*10=" + n3);
        console.println("/10=" + (n3 / 10));

        console.println("PI=" + FPNumber.PI);
        Float pi64 = FPNumber.PI;
        console.println("pi64=" + pi64);

        // see http://www.cplusplus.com/reference/cmath/round/
        Float f1 = 2.3;
        Float f2 = 3.8;
        Float f3 = 5.5;
        Float f4 = -f1;
        Float f5 = -f2;
        Float f6 = -f3;

        console.println();
        console.println("value\tround\tfloor\tceil\ttoZero");
        console.println("-----\t-----\t-----\t----\t-----");
        console.println($"{f1},\t{f1.round()},\t{f1.floor()},\t{f1.ceil()},\t{f1.round(TowardZero)}");
        console.println($"{f2},\t{f2.round()},\t{f2.floor()},\t{f2.ceil()},\t{f2.round(TowardZero)}");
        console.println($"{f3},\t{f3.round()},\t{f3.floor()},\t{f3.ceil()},\t{f3.round(TowardZero)}");
        console.println($"{f4},\t{f4.round()},\t{f4.floor()},\t{f4.ceil()},\t{f4.round(TowardZero)}");
        console.println($"{f5},\t{f5.round()},\t{f5.floor()},\t{f5.ceil()},\t{f5.round(TowardZero)}");
        console.println($"{f6},\t{f6.round()},\t{f6.floor()},\t{f6.ceil()},\t{f6.round(TowardZero)}");
        }
    }