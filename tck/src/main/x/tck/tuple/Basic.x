/**
 * Basic tuple tests.
 */
class Basic {

    @Test
    void testConstAccess() {
        Tuple<String, String, Int> t = ("hello", "world", 17);
        assert t[0] == "hello";
        assert t[2] == 17;
    }

    @Test
    void testIndexAccess() {
        Tuple t = ("hello", Int:17);
        for (Int i : 0 ..< t.size) {
            switch (i) {
            case 0:
                assert t[i].as(String) == "hello";
                break;
            case 1:
                assert t[i].as(Int) == 17;
                break;
            }
        }
    }

    @Test
    void testEquality() {
        static String BYE() = "goodbye";
        Int    four = 4;
        String now  = "now";

        Tuple<String, Map<Int, String>> t1 = ("goodbye", [4="now"]);
        Tuple<String, Map<Int, String>> t2 = Tuple:(BYE(), [four=now]);
        assert t1 == t2;

        Tuple<Int, String, Char> t3 = (1, "big", '?');
        Tuple t4 = Tuple:().add(Int:1).add("big").add('?');
        assert t3 == t4;
    }

    @Test
    void testVoidConv() {
        private static void getVoid() {}

        Tuple tv = getVoid();
        assert tv.size == 0;
    }

    @Test
    void testIntConv() {
        private static Int getInt() = 4;

        Tuple<Int> ti = getInt();
        assert ti.size == 1 && ti[0] == 4;
    }

    @Test
    void testSlice() {
        Int three = 3;
        Tuple<Int, String, String, Char> t = (three, "blind", "mice", '!');
        Tuple<String, String, Char> s = t[1..3];
        assert s[three-1].as(Char) == s[2] == '!';
    }
}
