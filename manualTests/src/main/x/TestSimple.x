module TestSimple {
    @Inject Console console;

    void run() {
        if (False) { // this used to fail to compile
            console.print("");
        }
    }
}
