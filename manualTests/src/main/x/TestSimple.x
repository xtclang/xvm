module TestSimple {
    @Inject Console console;

    void run() {
        function (Boolean, String)(Int) f = i -> {
            return False; // used to NPE the compiler
        };
    }
}