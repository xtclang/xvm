module TestSimple {
    @Inject Console console;

    void run() {
         assert !(String s := never());

        // above could be re-written and should produce the same result as
        // String s;
        // assert !never();
    }

    conditional String never() = False;
}
