module TestSimple {
    @Inject Console console;

    void run() {
        Tuple result = console.print("Hello, world!"); // this used to blow up at runtime
        console.print(result);
    }
}