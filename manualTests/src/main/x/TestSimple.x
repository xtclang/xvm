module TestSimple {
    @Inject Console console;

    void run() {
        String? x;
        x = Null;
        if (x == Null) { // added "x is always Null" warning
            x = "f";
        }
    }
}
