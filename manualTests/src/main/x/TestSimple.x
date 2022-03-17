module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Byte[] bytes4 = [0xDE, 0xAD, 0xCA, 0xFE];
        console.println(bytes4);

        Nibble[] nibbles8 = bytes4.asNibbleArray();
        UInt32 n32 = nibbles8.toUInt32(); // this used to throw CCE

        console.println(n32);
        assert n32.toByteArray() == bytes4;
        }
    }