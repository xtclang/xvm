module TestSimple
    {
    @Inject Console console;

    void run() {
        Byte[] mutableBytes = new Byte[];
        console.print(mutableBytes.duplicate()); // this used to assert at run0time
    }
}