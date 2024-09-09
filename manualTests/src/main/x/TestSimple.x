module TestSimple {
    @Inject Console console;

    void run() {
        (Int x2, Int y2) = new Test().test2^(); // used to fail to compile
        assert !&x2.assigned;
        &x2.whenComplete((r, e) -> {
            console.print(r);
        });
    }

    service Test {
        (Int, Int) test2() {
            return 42, 43;
        }
    }
}
