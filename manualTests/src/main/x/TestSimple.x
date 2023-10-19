module TestSimple {
    @Inject Console console;

    void run() {
        Int      n     = 0x123456789ABCDEF0;
        Nibble[] nibs  = n.toNibbleArray();
        console.print($"{nibs=}");
        Byte[]   bytes = nibs.toByteArray();  // used to produce a wrong array (size=2)
        console.print($"{bytes=}");
        Int[]    ints  = bytes.toInt64Array();
        console.print($"{ints=}");
    }
}