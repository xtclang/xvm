module TestSimple {

    @Inject static Console console;

    static void out(Object o = "") {
        console.print(o);
    }

    void run() {
        "hello".{out($"{size=}");};
    }
}
