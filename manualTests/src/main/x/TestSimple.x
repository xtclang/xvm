module TestSimple {

    void run() {
        @Inject Console console;

        (Int x2, Int y2) = new Test().test2^();
        assert !&x2.assigned;

        &x2.whenComplete((r, e) -> {
            console.print($"{x2=} {y2=}");
        });
        assert !&x2.assigned; // used to fail
    }

    service Test {
        (Int, Int) test2() {
            return 42, 43;
        }
    }
}
