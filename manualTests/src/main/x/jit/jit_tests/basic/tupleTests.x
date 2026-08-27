package tupleTests {

    void run() {
        testSimple();
        testEquals();
        testConv();
        testCallReturns();
        testInvokeReturns();
        testPackedReturn();
        testConstElement();
        testConstSlice();
        testMultiAssign();
        testMutability();
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
        Tuple tv = getVoid();
        assert tv.size == 0;

        Tuple<Int> ti = getInt();
        assert ti[0] == 4;

        Tuple<String, Int> tsi = getSI();
        assert tsi[0] == "Hello" && tsi[1] == 4;

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

    void testCallReturns() {
        Tuple<String, Int> one = call1Many(1);
        assert one[0] == "one" && one[1] == 1;

        Tuple<String, Int> many = callNMany("many", 2);
        assert many[0] == "many" && many[1] == 2;

        private static (String, Int) call1Many(Int value) {
            return "one", value;
        }

        private static (String, Int) callNMany(String text, Int value) {
            return text, value;
        }
    }

    void testInvokeReturns() {
        Test test = new Test();

        Tuple<String, Int> zero = test.invoke0();
        assert zero[0] == "zero" && zero[1] == 0;

        Tuple<String, Int> one = test.invoke1(1);
        assert one[0] == "one" && one[1] == 1;

        Tuple<String, Int> many = test.invokeN("many", 2);
        assert many[0] == "many" && many[1] == 2;

        class Test {
            (String, Int) invoke0() {
                return "zero", 0;
            }

            (String, Int) invoke1(Int value) {
                return "one", value;
            }

            (String, Int) invokeN(String text, Int value) {
                return text, value;
            }
        }
    }

    void testPackedReturn() {
        (String text, Boolean flag) = compute(() -> "hello");
        assert text == "hello" && flag;

        Tuple<Int128, Int?, Int128?> numbers = (123, 456, 789);
        (Int128 wide, Int? small, Int128? nullableWide) = unpackNumbers(numbers);
        assert wide == 123 && small == 456 && nullableWide == 789;

        numbers = (123, Null, Null);
        (wide, small, nullableWide) = unpackNumbers(numbers);
        assert wide == 123 && small == Null && nullableWide == Null;

        (String, Boolean) compute(function String() compute) {
            return process(() -> (compute(), True));
        }

        (Int128, Int?, Int128?) unpackNumbers(Tuple<Int128, Int?, Int128?> numbers) {
            return numbers;
        }

        <Result> Result process(function Result() compute) {
            return compute();
        }
    }

    void testConstElement() {
        String blind = (3, "blind", "mice", "!")[1];
        assert blind == "blind";

        Int num = (3, "blind", "mice", "!")[0];
        assert num == 3;
    }

    void testConstSlice() {
        Tuple<Int, String> blind = (3, "blind", "mice", "!")[0..1];
        assert blind == (3, "blind");

        Tuple<String, Int> blind2 = (3, "blind", "mice", "!")[1..0];
        assert blind2 == ("blind", 3);
    }

    void testMultiAssign() {
        (String s, Int i) = ("hello", 3);
        assert s == "hello" && i == 3;
    }

    void testMutability() {
        Tuple<Int, String, Char> tuple1 = (1, "big", '?');
        Tuple tuple1a = ().add(Int:1).add("big").add('?');
        assert tuple1a == tuple1;

        Tuple<Int, String, Char> tuple2 = tuple1.replace(1, "small");
        assert tuple2 == (1, "small", '?');

        Tuple<String, Char> tuple3 = tuple2[1..2];
        assert tuple3 == ("small", '?');

        Tuple tuple4 = tuple2.slice(1..2);
        assert tuple4 == tuple3;

        Tuple tuple5 = (1.toInt(),).addAll(tuple4);
        assert tuple5 == tuple2;
    }
}
