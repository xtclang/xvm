module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int[] ints  = [1, 2, 3];
        reportBits("const", ints.asBitArray());

        ints = ints.reify(Mutable);
        Bit[] bits = ints.asBitArray();
        reportBits("mutable", bits);

        reportBits("reified", bits.reify(Fixed)); // TODO GG: reify() is not working

        bits[61] = 1;
        console.println($"ints: {ints}");

        bits = ints[1..2].asBitArray();
        reportBits("slice", bits);

        reportBits("reified slice", bits.reify(Persistent));

        console.println($"\n*** bits as bytes");
        console.println(bits.reify(Persistent).asByteArray());

        console.println($"\n*** slice bits as bytes");
        console.println(bits.reify(Persistent)[0..63].asByteArray());

        console.println($"\n*** ints as bytes");
        console.println(ints.asByteArray());

        console.println($"\n*** bytes to ints");
        Int[] ints2 = ints.asByteArray().asInt64Array();
        console.println(ints2);
        assert ints == ints2;
        }

    void reportBits(String test, Bit[] bits)
        {
        console.println($"\n*** {test}");
        console.println($"bits: {bits.mutability} size={bits.size}");
        for (Int i : [0..bits.size))
            {
            if (bits[i] == 1)
                {
                console.print($"[{i}]=1, ");
                }
            }
        console.println();
        }
    }