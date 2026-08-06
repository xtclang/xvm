package refTests {

    void run() {
        @Inject Console console;

        console.print(">>>> Running RefTests >>>>");

        test1();
        test2();
        test3();
        test4();
        test5();
        test6();

        TestProp test = new TestProp();
        test.testStandard();
        test.testPrimitive();
        test.testConstant();

        new UnassignedProp().test();
    }

    void test1() {
        Test t1 = new Test();
        Test t2 = new Test();
        assert &t1 != &t2;
    }

    void test2() {
        Test t1 = new Test();

        Ref<Test> r1 = &t1;
        Var<Test> r2 = &t1;
        assert r1 == r2;
        assert r1.assigned;
        assert Test value := r1.peek();
        assert &value == r1;
    }

    void test3() {
        Test t1 = new Test();
        Test t2 = new Test();

        assert &t1 != &t2;
    }

    void test4() {
        Test t1 = new Test();
        Test t2 = new Test();

        // this is functionally equivalent to test3(), but produces a different op code stream
        Ref<Test> r1 = &t1;
        Var<Test> r2 = &t2;
        if (r1 == r2) {
            throw new Assertion($"r1 == r2");
        }
    }

    void test5() {
        Test t1 = new Test();
        Test t2 = new Test();

        assert t1.testRefEquality(t1);
        assert !t1.testRefEquality(t2);
    }

    void test6() {
        Test t = new Test(0);
        Var<Test> r = &t;
        setValue(r, 1);
        assert t.value == 1;
    }

    void setValue(Var<Test> r, Int value) {
        r.set(new Test(value));
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
            @Inject Console console;

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
