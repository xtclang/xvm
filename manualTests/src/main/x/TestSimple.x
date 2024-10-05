module TestSimple.org { // this used to assert the compiler; should be an error
    @Inject Console console;

    void run() {
        console.print();
    }
}
