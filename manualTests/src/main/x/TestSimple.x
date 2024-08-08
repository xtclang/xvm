module TestSimple {
    @Inject Console console;

    void run() {
        console.print(test(Null));
        console.print(test(Red));
        console.print(test(Blue));
    }

    enum Color {Red, Green, Blue}

    Color test(Color? c) {
        if (c == Red || c == Green) {
            return c; // this used to fail to compile
        }

        return Blue;
    }
}