module TestSimple {
    @Inject Console console;

    void run() {
    }

    class Base {
        class Child {
            void testOuter() {
                val o1 = outer;
                Outer o2; o2 = this.Outer;
                assert &o2 == &outer; // this used to blow up the compiler
            }
        }
    }
}
