/**
 * Very basic tuple operations
 */
class Basic {

    void run() {
        testBasic();
    }

    @Test
    void testBasic() {
        Tuple<Int, String, Double> t = (3, "abc", 3.1415);
        assert t.size == 3;
        assert t[0] == 3;
        assert t[1] == "abc";
        assert t[2] == 3.1415;
    }

}
