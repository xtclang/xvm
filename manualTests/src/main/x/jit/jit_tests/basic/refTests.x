package refTests {

    void run() {
        testEquality();
        testEquality2();
        testPeek();
        testRefEquality();
        testSet();
        testWatch();
        testDelegatingVar();

        TestProp test = new TestProp();
        test.testStandard();
        test.testPrimitive();
        test.testConstant();

        new UnassignedProp().test();
    }

    void testEquality() {
        Test t1 = new Test();
        Test t2 = new Test();
        assert &t1 != &t2;
    }

    void testEquality2() {
        Test t1 = new Test();
        Test t2 = new Test();

        // this is functionally equivalent to testEquality(), but produces a different op code stream
        Ref<Test> r1 = &t1;
        Var<Test> r2 = &t2;
        if (r1 == r2) {
            throw new Assertion($"r1 == r2");
        }
    }

    void testPeek() {
        Test t1 = new Test();

        Ref<Test> r1 = &t1;
        Var<Test> r2 = &t1;
        assert r1 == r2;
        assert r1.assigned;
        assert Test value := r1.peek();
        assert &value == r1;
    }

    void testRefEquality() {
        Test t1 = new Test();
        Test t2 = new Test();

        assert t1.testRefEquality(t1);
        assert !t1.testRefEquality(t2);
    }

    void testSet() {
        Test t = new Test(0);
        Var<Test> r = &t;
        setValue(r, 1);
        assert t.value == 1;

        void setValue(Var<Test> r, Int value) {
            r.set(new Test(value));
        }
    }

    void testWatch() {
        // property-based
        watchConsole.reset();
        for (@Watch(logger) Int i = 3; i > 0; --i) {}
        assert watchConsole.output() == "->3->2->1->0";

        // var-based
        TestConsole console = new TestConsole();
        function void(Int) log = (Int value) -> console.print($"{value}->", True);

        @Watch(log) Int value;
        value = 1;
        value++;
        assert console.output() == "1->2->";

        // lambda-based
        console.reset();
        @Watch((Int v) -> {console.print($"{v}, ", True);}) Int k;
        for (k = 3; k > 0; --k) {}
        assert console.output() == "3, 2, 1, 0, ";
    }

    static TestConsole watchConsole = new TestConsole();
    function void(Int) logger = (Int value) -> watchConsole.print($"->{value}", True);

    void testDelegatingVar() {
        import ecstasy.collections.UniformIndexed;

        // exercise the SimpleVar returned by UniformIndexed.elementAt(), which delegates Var
        IndexedValue<String> indexed = new IndexedValue("before");
        Var<String> ref = indexed.elementAt(0);
        assert ref.assigned;
        assert ref.get() == "before";

        ref.set("after");
        assert indexed.getElement(0) == "after";

        indexed.setElement(0, "direct");
        assert ref.get() == "direct";
        assert String value := ref.peek();
        assert value == "direct";

        class IndexedValue<Element>(Element value)
                implements UniformIndexed<Int, Element> {
            @Override
            Element getElement(Int index) = value;

            @Override
            void setElement(Int index, Element value) {
                this.value = value;
            }
        }
    }

    class Test(Int value = 0) {
        Boolean testRefEquality(Test that) {
            return &this == &that;
        }
    }

    class UnassignedProp() {
        @Unassigned String value;

        void test() {
            Ref<String> ref = &value;
            assert !ref.assigned;
            assert !ref.peek();

            value = "assigned";
            assert ref.assigned;
            assert String actual := ref.peek();
            assert actual == value;
        }
    }

    class TestProp {
        static Boolean c = True;

        String r.get() = "RO";
        String v = "orig";
        Int n = 1;

        void testStandard() {
            Ref<String> ref = &r;
            assert ref.get() == "RO";
            assert ref.assigned;
            assert String value := ref.peek();
            assert value == "RO";

            Var<String> var = &v;
            var.set("updated");
            assert v == "updated";
        }

        void testPrimitive() {
            Ref<Int> nRef = &n;
            Var<Int> nVar = &n;
            assert nRef.get() == 1;
            assert nRef.assigned;
            assert Int value := nRef.peek();
            assert value == 1;
            nVar.set(2);
            assert n == 2;
            assert nRef.get() == 2;
        }

        void testConstant() {
            Ref<Boolean> cRef = &c;
            assert cRef.get();
            assert cRef.assigned;
            assert Boolean value := cRef.peek();
            assert value;
        }
    }
}
