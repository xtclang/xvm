module test.examples.org {

    @Inject Console console;

    void run() {
        @Inject Clock clock;

        console.print($"{clock.now=}");
    }
}
