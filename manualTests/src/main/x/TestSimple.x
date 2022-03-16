module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Byte[] bytes4 = [0xDE, 0xAD, 0xCA, 0xFE];
        Bit[]  bits32 = bytes4.toBitArray();      // this used to produce a wrong result
        console.println(bits32);
        console.println(bits32.asByteArray().toInt32()); // this used to blow up

        Byte[] bytes8 = [0xDE, 0xAD, 0xCA, 0xFE, 0xCA, 0xFE, 0xDE, 0xAD];
        Bit[]  bits64 = bytes8.toBitArray();      // this used to produce a wrong result
        console.println(bits64);
        }
    }