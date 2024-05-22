/**
 * Basic conditional tests.
 */
class Basic {

    void run() {
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
    }

    @Test
    void test1() {
        assert !test();
        conditional String test() = False;
    }

    @Test
    void test2() {
        assert String s := test(), s=="abc";
        conditional String test() = (True,"abc");
    }

    @Test
    void test3() {
        if( String s := test() ) { assert s=="abc"; }
        else { assert False; }
        conditional String test() = (True,"abc");
    }

    @Test
    void test4() {
        assert !test(True);
        assert String s := test(False), s=="abc";
        conditional String test(Boolean b0) = b0 ? False : (True,"abc");
    }

    @Test
    void test5() {
        assert !test(True,True);
        assert String s0 := test( True,False), s0=="def";
        assert String s1 := test(False, True), s1=="abc";
        assert String s2 := test(False,False), s2=="xyz";
        conditional String test(Boolean b0, Boolean b1) =
            b0 ? (b1 ? False : (True,"def")) : (b1 ? (True,"abc") : (True, "xyz"));
    }

    @Test
    void test6() {
        assert !test0(True,True);
        assert String s0 := test0( True,False), s0=="def";
        assert String s1 := test0(False, True), s1=="abc";
        assert String s2 := test0(False,False), s2=="abc";
        conditional String test0(Boolean b0, Boolean b1) = b0 ? test1(b1) : (True,"abc");
        conditional String test1(Boolean b1) = b1 ? False : (True,"def");
    }

}
