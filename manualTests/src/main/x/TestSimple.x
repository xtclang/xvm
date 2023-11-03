module TestSimple {
    @Inject Console console;

    void run() {
        Section start = A;
        Section end   = A;

        for (Section s5 : start ..< end) { // this used to blow up at run-time with OutOfBounds
            console.print($"{s5=}");
        }
        for (Section s6 : end ..< start) { // this used to blow up at run-time with OutOfBounds
            console.print($"{s6=}");
        }
    }

    enum Section {A, B, C}
}
