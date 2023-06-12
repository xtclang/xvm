module TestSimple
    {
    @Inject Console console;

    void run() {
        Test t = new Test();
        console.print(t.p2);
    }

    class Test {
        Int[] p1 = [1, 2];

        @Lazy Int p2.calc() {

            // assert !this.assigned; // compiler error; "this" refers to Test
            assert !&p2.assigned;
            assert !this.&p2.assigned;
            assert !assigned;           // used to fail at run-time
            assert !this.Test.p1.empty; // used to fail at run-time

            p1.forEach(i -> {
                console.print($"{i} {p1.size=}");
                console.print($"{i} {assigned=}"); // used to fail at run-time
            });
            return this.p1.size;
        }
    }
}