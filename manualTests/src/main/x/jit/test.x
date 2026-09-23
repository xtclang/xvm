module test.examples.org {

    @Inject Console console;

    void run() {
        @Inject(opts=42) Random rnd;
        console.print(rnd.int(100));
        console.print(rnd.int8());
        console.print(rnd.uint8());

        @Inject Clock clock;
        console.print($"{clock.resolution=} {clock.monotonic=}");
    }
}
