package invocationTests {

    @Inject Console console;

    void run() {

        testInvokePrivateMethodAfterInterfaceCast();
        testOrderable();

    }

    void testInvokePrivateMethodAfterInterfaceCast() {
        Test t1 = new Test(0);
        Test t2 = new Test(0);
        t1.test(t2);
        assert t2.i == 19;

        interface TestInterface {
            void test(TestInterface t);
        }

        class Test(Int i)
             implements TestInterface {
            @Override
            void test(TestInterface t) {
                if (t.is(Test)) {
                    t.privateMethod();
                }
            }

            private void privateMethod() {
                i = 19;
            }
        }
    }

    void testOrderable() {
        Derived d1 = new Derived(0, 1);
        Derived d2 = new Derived(0, 2);

        assert comp(asBase(d1), asBase(d2));
        assert !comp(d1, d2);

        assert order(asBase(d1), asBase(d2)) == Equal;
        assert order(d1, d2) == Lesser;

        assert asBase(d1).maxOf(asBase(d2)) == asBase(d1);
        assert d1.maxOf(d2) == d2;

        Base asBase(Base base) = base;

        <CompileType extends Base> Boolean comp(CompileType b1, CompileType b2) {
            return b1 == b2;
        }

        <CompileType extends Base> Ordered order(CompileType b1, CompileType b2) {
            return b1 <=> b2;
        }

        class Base(Int base) implements Orderable {
            @Override
            static <CompileType extends Base> Ordered compare(CompileType v1, CompileType v2) =
                v1.base <=> v2.base;

            @Override
            static <CompileType extends Base> Boolean equals(CompileType v1, CompileType v2) =
                v1.base == v2.base;
        }

        class Derived(Int base, Int derived) extends Base(base) {
            @Override
            static <CompileType extends Derived> Ordered compare(CompileType v1, CompileType v2) {
                Ordered order = v1.base <=> v2.base;
                return order == Equal
                    ? v1.derived <=> v2.derived
                    : order;
            }

            @Override
            static <CompileType extends Derived> Boolean equals(CompileType v1, CompileType v2) =
                v1.base == v2.base && v1.derived == v2.derived;
        }
    }
}
