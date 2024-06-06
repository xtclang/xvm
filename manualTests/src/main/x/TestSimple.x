module TestSimple {
    @Inject Console console;

    void run() {
        @Volatile String? error = Null;
        function void (String) report =
            s -> {error = error? + ", " + s : s;}; // this used to fail to compile

        report("one");
        report("two");
        console.print(error);
    }
}
