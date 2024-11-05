module TestSimple {

    @Inject Console console;

    void run() {
        Int[] array = new Int[0]; // used to generate an invalid BAST

        console.print(array);
    }
}