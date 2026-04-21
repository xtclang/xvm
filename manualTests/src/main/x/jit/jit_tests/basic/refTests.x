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
}