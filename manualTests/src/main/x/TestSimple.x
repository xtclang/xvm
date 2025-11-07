module TestSimple {
    @Inject Console console;

    // to see the returned value, run this from the terminal:
    // xec TestSimple; echo $?
    Int run() {
        return 42;
    }
}