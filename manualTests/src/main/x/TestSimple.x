module TestSimple {
    @Inject Console console;

    void run() {
        @Inject(resourceName="test") String value;

        console.print($"{&value.assigned ? value : "not assigned"}");
    }
}