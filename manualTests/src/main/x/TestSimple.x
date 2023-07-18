module TestSimple.examples.org {
    @Inject Console console;

    void run() {
    }

    Int test(Int x) {
        switch (x) {
        case 0:
            return 0;

        case 1:
            Int bug = x + 1;
            function Int (Int) supply = i -> {
                return bug + i; // "bug" used to be not "effectively final"
            };
            return 1;

        default:
            return 0;
        }
    }
}