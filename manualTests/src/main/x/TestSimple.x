module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int[] ints = [1, 2];
        ints = ints.reify(Mutable);
        test(ints.asByteArray());

        UInt32[] uints = [1, 2];
        uints = uints.reify(Mutable);
        test(uints.asByteArray());

        Bit[] bits = Int:1.toBitArray();
        bits = bits.reify(Mutable);
        test(bits.asByteArray());
        }

    void test(Byte[] bytes)
        {
        console.println($"bytes = {bytes}");

        Byte[] bytes2 = bytes[0..7];
        console.println($"bytes2 = {bytes2}");

        //  CCE
        Bit[] bits = bytes.asBitArray();
        console.println($"bits = {bits}");

        Byte[] bytes3 = bits.asByteArray();
        console.println($"bytes3 = {bytes3}");

        Byte[] bytes4 = bytes[0..7];
        console.println($"bytes4 = {bytes4}");

        Bit[] bits2 = bytes4.asBitArray();
        console.println($"bits2 = {bits2}");

        UInt64 value = bytes4.toUInt64();
        console.println($"value={value}");
        }
    }