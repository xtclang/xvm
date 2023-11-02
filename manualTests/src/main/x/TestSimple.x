module TestSimple  {
    @Inject Console console;

    Int q = 7;
    void run() {
        q = 11; // used to assert in BAST production
    }
}