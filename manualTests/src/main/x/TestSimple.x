module TestSimple {
    @Inject Console console;

    void run() {
        Int      n     = 0x123456789ABCDEF0;
        Nibble[] nibs  = n.toNibbleArray();
        Byte[]   bytes = nibs.toByteArray(); // used to blow up at run-time
    }
}