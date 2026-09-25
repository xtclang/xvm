module test.examples.org {

    @Inject Console console;

    void run() {
        @Inject("localClock") Clock clock;

        console.print($"{clock.now=}");
    }
}
