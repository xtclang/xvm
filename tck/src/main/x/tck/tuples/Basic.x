/**
 * Very basic tuple operations
 */
class Basic {

    void run() {
        testBasic();
        testReturn();
        testUnpack();
    }

    @Test
    void testBasic() {
        Tuple<Int, String, Double> t = (3, "abc", 3.1415);
        assert t.size == 3;
        assert t[0] == 3;
        assert t[1] == "abc";
        assert t[2] == 3.1415;
    }

    @Test
    void testReturn() {
        (Int a, String b, Double c) = tupleReturn();
        assert a == 3;
        assert b == "abc";
        assert c == 3.1415;
    }
    (Int a, String b, Double) tupleReturn() { return 3, "abc", 3.1415; }


    @Test
    void testUnpack() {
        (Int a, String b, Double c) = tupleReturn();
        assert a == 3;
        assert b == "abc";
        assert c == 3.1415;
    }
    
}
