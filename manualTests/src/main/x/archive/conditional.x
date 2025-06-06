module ConditionalTest {
    @Inject Console console;

    void run() {
        console.print("Normal class incorporation:");
        new C().foo();

        console.print("\nDerived with conditional incorporation not applied:");
        new D<String>().foo();

        console.print("\nDerived with conditional incorporation applied:");
        new D<Int>().foo();
    }

    interface I<Element> {
        void foo() = console.print($"I.foo() for {Element}");
    }

    mixin M<Element> into I<Element> {
        @Override
        void foo() {
            console.print("M.foo()");
            super();
        }
    }

    class C
            implements I
            incorporates M {
        @Override
        void foo() {
            console.print("C.foo()");
            super();
        }
    }

    class B<Element>
            implements I<Element> {
        @Override
        void foo() {
            console.print("B.foo()");
            super();
        }
    }

    class D<Element>
            extends B<Element>
            incorporates conditional M<Element extends Int> {
        @Override
        void foo() {
            console.print("D.foo()");
            super();
        }
    }
}