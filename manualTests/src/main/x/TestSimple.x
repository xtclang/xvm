module TestSimple {

    @Inject Console console;

    void run() {
        Dec value = 42.0;
        assert value.toDec() == 42.0; // this used to assert the compiler
    }
}
