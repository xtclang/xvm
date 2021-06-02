module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int16[] ints16 = [-1, 1];
        console.println($"{ints16} {ints16.asByteArray().toInt16Array()}");

        UInt16[] uints16 = [65535, 1];
        console.println($"{uints16} {uints16.asByteArray().toInt16Array()}");

        Byte[] bytes = [1, 1, 0, 255];
        console.println($"{bytes} {bytes.asBitArray()} {bytes.asInt16Array()}");

        Int[] ints64 = [1, -1];
        console.println($"{ints64} {ints64.asByteArray().toInt64Array()}");

        UInt[] uints64 = [0x8000_0000_0000_0000];
        console.println($"{uints64} {uints64.asByteArray().toInt64Array()}");

        Int8[] ints8 = [1, 1, 0, -1];
        console.println($"{ints8} {ints8.asByteArray().toInt16Array()}");
        }
    }