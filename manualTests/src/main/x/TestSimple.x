module TestSimple {
    @Inject Console console;

    void run() {
        for (Int i : 1 ..< 1) { // this used to blow up in the BAST production
            console.print(i);
        }
    }
}