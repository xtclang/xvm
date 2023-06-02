module TestContained {
    @Inject Console console;
    @Inject String  description;

    void run() {
        @Inject Int value;

        console.print($"{description=} {value=}");
    }
}