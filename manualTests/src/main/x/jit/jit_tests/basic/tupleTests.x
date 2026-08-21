package tupleTests {

    void run() {
        testSimple();
        testEquals();
        testConv();
        testConstElement();
//        testConstSlice();
        testMultiAssign();
//        testMutability();
    }

    void testSimple() {
        Tuple<String, String, Int> tuple = ("hello", "world", 17);
        assert tuple.toString() == "(hello, world, 17)";

        String s0 = tuple[0];
        String s1 = tuple[1];
        Int    i2 = tuple[2];
        assert s0 == "hello" && s1 == "world" && i2 == 17;

        // TODO: ListMap.makeImmutable() fails verification by returning ListMapIndex as ListMap
//        Tuple<String, Map<Int, String>> tuple2 = ("goodbye", [4="now"]);
//        assert tuple2[0] == "goodbye";
//        assert tuple2[1][4] == "now";
//
//        Tuple<String, Map<Int, String>> tuple3 = Tuple:(BYE, [4="now"]);
//        assert tuple3 == tuple2;
//
//        private @Lazy String BYE.calc() {
//            return "goodbye";
//        }
    }

    void testEquals() {
        Tuple<Int, Char, Boolean> tuple1 = (17, 'x', True);
        Tuple<Int, Char, Boolean> tuple2 = (17, 'x', True);
        Tuple<Int, Char, Boolean> tuple3 = (18, 'x', True);

        assert tuple1 == tuple2;
        assert tuple1 != tuple3;
    }

    void testConv() {
        // TODO: JIT doesn't implement CALL_0T for a void return converted to Tuple
//        Tuple tv = getVoid();
//        assert tv.size == 0;

        // TODO: JIT doesn't implement CALL_0T for a single return converted to Tuple
//        Tuple<Int> ti = getInt();
//        assert ti[0] == 4;

        // TODO: JIT doesn't implement CALL_0T for multiple returns converted to Tuple
//        Tuple<String, Int> tsi = getSI();
//        assert tsi[0] == "Hello" && tsi[1] == 4;

        Tuple<String, IntLiteral> tsiT = getTupleSI();
        assert tsiT[0] == "Hello" && tsiT[1] == 4;

        private static void getVoid() {}

        private static Int getInt() {
            return 4;
        }

        private static (String, Int) getSI() {
            return "Hello", 4;
        }

        private static Tuple<String, IntLiteral> getTupleSI() {
            return ("Hello", 4);
        }
    }

    void testConstElement() {
        String blind = (3, "blind", "mice", "!")[1];
        assert blind == "blind";

        Int num = (3, "blind", "mice", "!")[0];
        assert num == 3;
    }

    // TODO: nType.equals$p cannot route boxed String equality
//    void testConstSlice() {
//        Tuple<Int, String> blind = (3, "blind", "mice", "!")[0..1];
//        assert blind == (3, "blind");
//
//        Tuple<String, Int> blind2 = (3, "blind", "mice", "!")[1..0];
//        assert blind2 == ("blind", 3);
//    }

    void testMultiAssign() {
        (String s, Int i) = ("hello", 3);
        assert s == "hello" && i == 3;
    }

    // TODO: nTuple.add() retains its randomized JIT name
//    void testMutability() {
//        Tuple<Int, String, Char> tuple1 = (1, "big", '?');
//        Tuple tuple1a = ().add(Int:1).add("big").add('?');
//        assert tuple1a == tuple1;
//
//        Tuple<Int, String, Char> tuple2 = tuple1.replace(1, "small");
//        assert tuple2 == (1, "small", '?');
//
//        Tuple<String, Char> tuple3 = tuple2[1..2];
//        assert tuple3 == ("small", '?');
//
//        Tuple tuple4 = tuple2.slice(1..2);
//        assert tuple4 == tuple3;
//
//        Tuple tuple5 = (1.toInt(),).addAll(tuple4);
//        assert tuple5 == tuple2;
//    }
}
