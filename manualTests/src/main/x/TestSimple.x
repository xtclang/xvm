module TestSimple {
    @Inject Console console;

    interface Iface {
        void foo();
    }

    class C1 implements Iface {
        @Override
        void foo() = console.print("C1");
    }

    class C2(C1 c1)
            implements Iface
            delegates Iface(c1) {

        @Override
        void foo() {
            console.print("C2");
            super(); // this used to throw at runtime
        }
    }

    void run() {
        C1 c1 = new C1();
        C2 c2 = new C2(c1);

        c2.foo();
    }
}