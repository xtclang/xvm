module TestSimple {
    @Inject Console console;

    void run() {
        new Test().run();
    }

    service Test {
        annotation Data into Session {

            const InsideConst(Int i);

            void foo() {
                val c = new InsideConst(0); // this used to blow up at runtime
                console.print(c);
            }
        }

        class SessionImpl(String name) implements Session;

        void run() {
            for (Int i : 0..3) {
                Data d = new @Data SessionImpl($"Test {i}");
                d.foo();
            }
        }
    }

    // we used to allow having the mixed-in class outside; now it would be a compiler error
    // class SessionImpl(String name) implements Session;

    interface Session {}
}
