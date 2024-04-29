module TestMisc {
    @Inject Console console;

    void run() {
        console.print("hello world!");

        testBools();
        testAsciiDigit();
        testInts();
        testTernary();
        testSpaceship();
        testElvis();
        testLoop();
        testSwitchStmt();
        testElseExpr();
        testSwitchExpr();
        testSwitchExpr3();
        testSwitchExpr4();
        testSwitchNatural();
        testStmtExpr();
        testAssignOps();

        testAssertTrue();
        testAssertTrueExpr();
        testAssertFalseExpr();
        testAssertDecl();

        testInterval();
        testException();
        testConditional();
        testBind();
        testConstants();
        testImport();

        countdown();
    }

    void testInts() {
        console.print("\n** testInts()");

        Int a0 = -1217;
        Int a = -a0;
        Int b = 3;
        console.print("a=" + a);
        console.print("b=" + b);

        console.print("a + b = "   + (a + b));
        console.print("a - b = "   + (a - b));
        console.print("a * b = "   + (a * b));
        console.print("a / b = "   + (a / b));
        console.print("a % b = "   + (a % b));
        console.print("a & b = "   + (a & b));
        console.print("a | b = "   + (a | b));
        console.print("a ^ b = "   + (a ^ b));
        console.print("a << b = "  + (a << b));
        console.print("a >> b = "  + (a >> b));
        console.print("a >>> b = " + (a.toInt64() >>> b));

        console.print("\n** pre/post inc/dec");
        console.print("a   = " + a);
        console.print("a++ = " + a++);
        console.print("a   = " + a);
        console.print("++a = " + ++a);
        console.print("a   = " + a);
        console.print("a-- = " + a--);
        console.print("a   = " + a);
        console.print("--a = " + --a);
        console.print("a   = " + a);
    }

    void testBools() {
        console.print("\n** testBools()");

        console.print("!True=" + !True);
        console.print("!False=" + !False);

        Boolean a = True;
        Boolean b = False;
        console.print("a=" + a);
        console.print("b=" + b);
        console.print("!a=" + !a);
        console.print("!b=" + !b);
        console.print("~a=" + ~a);
        console.print("~b=" + ~b);
    }

    void testAsciiDigit() {
        console.print("\n** testAsciiDigit()");
        Char[] chars = "1aA!\n$£€".toCharArray();
        // this also tests the conditional UInt8 to Int conversion
        assert Int n := chars[0].asciiDigit(), n == 1;
        assert !chars[1].asciiDigit();
    }

    void testTernary() {
        console.print("\n** testTernary()");

        Int i = 42;
        console.print("i=" + i);
        console.print("i%2=" + (i % 2));

        console.print("i>40?greater:not -> " + (i > 40 ? "greater" : "not greater"));
        console.print("i%2==0?even:odd  -> " + (i % 2 == 0 ? "even" : "odd"));
    }

    void testSpaceship() {
        console.print("\n** testSpaceship()");

        Int a = 42;
        Int b = 45;
        console.print("a=" + a + ", b=" + b + ", a<=>b=" + (a <=> b));
    }

    void testElvis() {
        console.print("\n** testElvis()");

        Int a = 42;
        Int b = 45;
        // this is an error: console.print("a=" + a + ", b=" + b + ", a?:b=" + (a ?: b));

        Int? c = a > 0 ? a : Null;
        console.print("c=" + c + ", b=" + b + ", c?:b=" + (c ?: b));

        static Int? trustMeItMightNotBeNull() {
            return Null;
        }

        c = trustMeItMightNotBeNull();
        console.print("c=" + c + ", b=" + b + ", c?:b=" + (c ?: b));
    }

    void testElseExpr() {
        console.print("\n** testElseExpr()");

        Int b = 7;
        IntLiteral? a = b > 0 ? Null : 17;
        console.print("a=" + a + ", b=" + b + ", a?.toInt64():b=" + (a?.toInt64():b));

        if (b==7) {
            a = 4;
        }
        console.print("a=" + a + ", b=" + b + ", a?.toInt64():b=" + (a?.toInt64():b));
    }

    void testLoop() {
        console.print("\n** testLoop()");

        Int i = 10;
        while (i > 0) {
            console.print(i--);
        }
        console.print("We Have Lift-Off!!!");
    }

    void testSwitchStmt(Int value=4) {
        console.print("\n** testSwitchStmt()");

        switch (value) {
        case 2, 3:
            console.print("2 or 3");
            break;

        case 4..5:
            console.print("4");
            if (value != 4) {
                break;
            }
            continue;           // TODO: Handle continue in the middle not at the end

        case 7:
            console.print("7");
            break;

        default:
            console.print("other");
            break;
        }
    }

    void testSwitchExpr() {
        console.print("\n** testSwitchExpr()");

        Int i = 0;
        while (i++ < 10) {
            console.print("result for " + i + "=" + switch (i) {
                // default: "other";
                case 2, 3:
                case 4:    "between 2 and 4";
                case 5..6: "between 5 and 6";

                case 7: "sieben";

                default: "other";
            });
        }
    }

    void testSwitchExpr3() {
        console.print("\n** testSwitchExpr3()");

        Int i = 0;
        while (++i < 6) {
            console.print("result for (" + i + "<=>3)=" + switch (i <=> 3) {
                case Lesser:  "less";
                case Equal:   "same";
                case Greater: "more";
            });
        }
    }

    void testSwitchExpr4() {
        console.print("\n** testSwitchExpr4()");

        Int i = 0;
        while (++i < 8) {
            console.print("result for (" + i + "<=>3)=" + switch (i <=> 3, i) {
                case (Lesser, 2):       "less #2";
                case (Lesser, _):       "less";
                case (Equal, 3):        "same #3";
                case (Equal, 4):        "same #4";
                case (Equal, _):        "same";
                case (Greater, 4):      "more #4";
                case (Greater, 2..6):   "more #2..6";
                default:                "default";
            });
        }
    }

    // Requires nested inner Point class, which is missing the BAST constructor
    void testSwitchNatural() {
        console.print("\n** testSwitchNatural()");

        assert test(new Point(0, 0)) == "min";
        assert test(new Point(2, 2)) == "between";
        assert test(new Point(4, 4)) == "max";
        assert test(new Point(5, 5)) == "outside";

        static Point MIN = new Point(0, 0);
        static Point MAX = new Point(4, 4);

        static String test(Point p) {
            return switch (p) {
                case MIN: "min";
                case MAX: "max";
                default : MIN < p < MAX ? "between" : "outside";
            };
        }
    }

    void testStmtExpr() {
        console.print("\n** testStmtExpr()");
        console.print("5+3=" + {return Int:5 + 3;});
    }

    void testAssertTrue() {
        console.print("\n** testAssertTrue()");
        assert True;
        console.print("(done)");
    }

    void testAssertTrueExpr() {
        console.print("\n** testAssertTrueExpr()");
        assert True != False;
        console.print("(done)");
    }

    void testAssertFalseExpr() {
        console.print("\n** testAssertFalseExpr()");
        try {
            assert True == False;
        } catch (IllegalState e) {
            Int x = 3;
            console.print("(done)");
            console.print(x);
        }
    }

    void testAssertDecl() {
        console.print("\n** testAssertDecl()");
        Int[] array = [1];
        Iterator<Int> iter = array.iterator();
        assert Int i := iter.next();
        console.print("i=" + i);
    }

    void testInterval() {
        console.print("\n** testInterval()");

        Int a = 2;
        Int b = 5;
        Object c = a..b;
        console.print("interval=" + c);

        Interval<Int> r = a+1..b+1;
        console.print("interval=" + r);
    }

    void testException() {
        console.print("\n** testException()");

        Exception e = new Exception("test");
        console.print("e=" + e);

        e = new IllegalArgument("test");
        console.print("e=" + e);
    }

    void testConditional() {
        console.print("\n** testConditional()");
        if (String s := checkPositive(17)) {
            console.print($"should be positive: {s}");
        }

        if (String s := checkPositive(-17)) {
            console.print($"should be negative: {s} (but this cannot happen)");
            assert;
        }

        String s = "negative";
        s := checkPositive(-99);
        console.print($"-99 => {s}");
        s := checkPositive(99);
        console.print($"99 => {s}");

        String? s2 = s.size > 0 ? s : Null;
        if (String s3 ?= s2) {
            console.print($"value is not Null: {s3}");
        } else {
            console.print($"value is Null: {s2}");
            assert;
        }

        static String? foolCompiler(String s) {
            return s;
        }

        s2 = foolCompiler(s2); // reintroduce possibility that s2 is Null

        s ?= s2;
        console.print($"s={s}");

        // this will assert (unless s2 is Null)
        // assert s2?.size>=0, False;

        // this will not assert
        assert s2?.size>=0, True;
    }

    private conditional String checkPositive(Int i) {
        return i < 0 ? False : (True, "positive");
    }

    void testAssignOps() {
        console.print("\n** testAssignOps()");

        Boolean f0 = True;
        Int? n = f0 ? Null : -1;
        n ?:= 4;
        console.print("n=" + n + " (should be 4)");

        private Int? pretendNullable(Int n) { return n; }
        n = pretendNullable(n);

        n ?:= 7;
        console.print("n=" + n + " (should be 4)");

        Boolean f1 = False;
        f1 &&= True;
        console.print("f1=" + f1 + " (should be False)");

        Boolean f2 = False;
        f2 ||= True;
        console.print("f2=" + f2 + " (should be True)");

        Boolean f3 = True;
        f3 &&= False;
        console.print("f3=" + f3 + " (should be False)");

        Boolean f4 = True;
        f4 ||= False;
        console.print("f4=" + f4 + " (should be True)");

        Boolean f5 = True;
        f5 &&= True;
        console.print("f5=" + f5 + " (should be True)");

        Boolean f6 = False;
        f6 ||= False;
        console.print("f6=" + f6 + " (should be False)");
    }

    void testBind() {
        console.print("\n** testBind()");

        foo(y="a", x=3);

        function void (Int) fn1 = &foo(y="a");
        fn1(3);

        function void () fn1b = &fn1(3);
        fn1b();

        function void () fn2 = &foo(y="a", x=3);
        fn2();

        bar(3, z=4, y=2);

        function void (Int, Int) fn3 = &bar(y=2);
        fn3(3, 4);

        function void (Int, Int) fn4 = &bar(3);
        fn4(2, 4);

        private void foo(Int x = 0, String y = "") {
            console.print($"foo: x={x}, y={y}");
        }

        private void bar(Int x, Int y, Int z = 1) {
            console.print($"bar: x={x}, y={y}, z={z}");
        }
    }

    void testConstants() {
        import ecstasy.collections.Hasher;
        import ecstasy.collections.NaturalHasher;

        console.print("\n** testConstants()");

        IntLiteral lit = 42;
        console.print("lit=" + lit);

        Point point1 = new Point(0, 2);
        console.print($"point1={point1} hypo={point1.hypo}");

        NamedPoint point2 = new NamedPoint("top-left", 1, 0);
        console.print($"point2={point2} hypo={point2.hypo}");

        Hasher<Point>      hasherP = new NaturalHasher<Point>();
        Hasher<NamedPoint> hasherN = new NaturalHasher<NamedPoint>();

        assert Point.hashCode(point1)      == Point.hashCode(point2);
        assert Point.hashCode(point1)      == hasherP.hashOf(point1);
        assert Point.hashCode(point2)      == hasherP.hashOf(point2);
        assert NamedPoint.hashCode(point2) == hasherN.hashOf(point2);

        Point point3 = point2;

        assert point1 == point3;
        assert point1.equals(point3);
        assert Point.equals(point1, point3);
        assert point1 <=> point3 == Equal;
        assert Point.compare(point1, point3) == Equal;

        AnyValue foo = new AnyValue(1, "foo");
        AnyValue bar = new AnyValue(1, "bar");
        assert foo == bar;
    }

    const Point(Int x, Int y) {
        @Lazy(() -> x*x + y*y) Int hypo;
    }

    const NamedPoint(String name, Int x, Int y)
            extends Point(2*y, x + 1) {
        @Override
        Int estimateStringLength() {
            return super() + name.size;
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            name.appendTo(buf.add('('));
            x.appendTo(buf.addAll(": x="));
            y.appendTo(buf.addAll(", y="));
            return buf.add(')');
        }
    }

    const AnyValue(Int key, String value) {
        @Override
        static <CompileType extends AnyValue> Boolean equals(CompileType value1, CompileType value2) {
            return value1.key == value2.key;
        }

        @Override
        static <CompileType extends AnyValue> Ordered compare(CompileType value1, CompileType value2) {
            return value1.key <=> value2.key;
        }
    }

    void testImport() {
        console.print("\n** testImport()");

        import Int as Q;
        Q x = 42;
        console.print("x=" + x);
    }

    void countdown() {
        console.print("Countdown!");
        for (Int i : 10..1) {
            console.print($"{i} ...");
        }
        console.print("We have lift-off!");
    }
}