module TestTuples {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("Tuple tests:");

        testSimple();
        testConv();
        testConstElement();
        testConstSlice();
        testMultiAssign();
        testMutability();
    }

    void testSimple() {
        console.print("\n** testSimple()");

        Tuple<String, String, Int> t = ("hello", "world", 17);
        console.print($"t={t}");
        for (Int i = 0; i < 3; ++i) {
            console.print($"tuple[{i}]={t[i]}");
        }

        String s0 = t[0];
        String s1 = t[1];
        Int    i2 = t[2];
        console.print($"fields: {s0}, {s1}, {i2}");

        Tuple<String, Map<Int, String>> t2 = ("goodbye", [4="now"]);
        console.print($"fields: {t2[0]}, {t2[1]}");

        Tuple<String, Map<Int, String>> t3 = Tuple:(BYE, [4="now"]);
        assert t3 == t2;

        private @Lazy String BYE.calc() {
            return "goodbye";
        }
    }

    void testConv() {
        console.print("\n** testConv()");

        Tuple tv = getVoid();
        console.print($"tv = {tv}");

        Tuple<Int> ti = getInt();
        console.print($"ti = {ti}");

        Tuple<String, Int> tsi = getSI();
        console.print($"tsi = {tsi}");

        Tuple<String, IntLiteral> tsiT = getTupleSI();
        console.print($"tsiT = {tsiT}");

        // TODO: should the following compile?
        // Tuple<String, Int> tsiT2 = getTupleSI();

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
        console.print("\n** testConstElement()");

        String blind = (3, "blind", "mice", "!") [1];
        console.print("tuple(1)=" + blind);

        Int num = (3, "blind", "mice", "!") [0];
        console.print("tuple(0)=" + num);
    }

    void testConstSlice() {
        console.print("\n** testConstSlice()");

        Tuple<Int, String> blind = (3, "blind", "mice", "!") [0..1];
        console.print("tuple[0..1]=" + blind);

        Tuple<String, Int> blind2 = (3, "blind", "mice", "!") [1..0];
        console.print("tuple[1..0]=" + blind2);
    }

    void testMultiAssign() {
        console.print("\n** testMultiAssign()");
        (String s, Int i) = ("hello", 3);
        console.print("s=" + s + ", i=" + i);
    }

    void testMutability() {
        console.print("\n** testMutability()");

        Tuple<Int, String, Char> t1 = (1, "big", '?');
        Tuple t1a = Tuple:().add(Int:1).add("big").add('?');
        assert t1a == t1;

        Tuple<Int, String, Char> t2 = t1.replace(1, "small");
        console.print($"{t2=}");

        Tuple<String, Char> t3 = t2[1..2];
        console.print($"{t3=}");

        Tuple t4 = t2.slice(1..2); // "small", ?
        assert t4 == t3;

        Tuple t5 = Tuple:(1.toInt()).addAll(t4); // 1, "small", ?
        assert t5 == t2;
    }
}