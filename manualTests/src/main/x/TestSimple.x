module TestSimple {
    @Inject Console console;

    void run() {
        report(UInt:123);
    }

    typedef UInt64 as ID;

    void report(ID id) {
        console.print("ID");
    }

    void report(UInt64 id) {
        console.print("UInt");
    }
}