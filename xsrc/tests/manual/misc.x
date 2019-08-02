module TestMisc.xqiz.it
    {
    @Inject Console console;
    @Inject Clock clock;

    void run()
        {
        console.println("hello world!");

        testBools();
        testInts();
        testIsA();
        testCast();
        testTernary();
        testSpaceship();
        testElvis();
        testLoop();
        testSwitchStmt();
        testElseExpr();
        testSwitchExpr();
        testSwitchExpr2();
        testSwitchExpr3();
        testSwitchExpr4();
        testStmtExpr();
        testAssignOps();

        // TODO make a new "test asserts" module? something that can tolerate assertions?
        // testAssert();
        // testAssertTrue();
        // testAssertFalse();
        // testAssertTrueExpr();
        // testAssertFalseExpr();
        // testAssertDecl();

        testInterval();
        testException();
        // testTupleConv();
        // testMap();
        testConditional();
        testConstants();
        testImport();
        testChild();

        countdown();
        }

    void testInts()
        {
        console.println("\n** testInts()");

        Int a0 = -1217;
        Int a = -a0;
        Int b = 3;
        console.println("a=" + a);
        console.println("b=" + b);

        console.println("a + b = "   + (a + b));
        console.println("a - b = "   + (a - b));
        console.println("a * b = "   + (a * b));
        console.println("a / b = "   + (a / b));
        console.println("a % b = "   + (a % b));
        console.println("a & b = "   + (a & b));
        console.println("a | b = "   + (a | b));
        console.println("a ^ b = "   + (a ^ b));
        console.println("a << b = "  + (a << b));
        console.println("a >> b = "  + (a >> b));
        console.println("a >>> b = " + (a >>> b));

        console.println("\n** pre/post inc/dec");
        console.println("a   = " + a);
        console.println("a++ = " + a++);
        console.println("a   = " + a);
        console.println("++a = " + ++a);
        console.println("a   = " + a);
        console.println("a-- = " + a--);
        console.println("a   = " + a);
        console.println("--a = " + --a);
        console.println("a   = " + a);
        }

    void testBools()
        {
        console.println("\n** testBools()");

        console.println("!true=" + !true);
        console.println("!false=" + !false);

        Boolean a = true;
        Boolean b = false;
        console.println("a=" + a);
        console.println("b=" + b);
        console.println("!a=" + !a);
        console.println("!b=" + !b);
        console.println("~a=" + ~a);
        console.println("~b=" + ~b);
        }

    void testIsA()
        {
        console.println("\n** testIsA()");

        console.println("\n(testing a string in a variable of type object)");
        Object o = "hello";
        console.println("o           =" + o);
        console.println("o.is(Object)=" + o.is(Object));
        console.println("o.is(String)=" + o.is(String));
        console.println("o.is(Int)   =" + o.is(Int));

        console.println("\n(testing an int in a variable of type int)");
        Int i = 5;
        if (i.is(Object))
            {
            console.println("i.is(Object) true (good)");
            }
        else
            {
            console.println("i.is(Object) false (bad)");
            }

        if (i.is(Int))
            {
            console.println("i.is(Int) true (good)");
            }
        else
            {
            console.println("i.is(Int) false (bad)");
            }

        if (i.is(String))
            {
            console.println("i.is(String) true (bad)");
            }
        else
            {
            console.println("i.is(String) false (good)");
            }
        }

    void testCast()
        {
        console.println("\n** testCast()");

        Int    i = 42;
        Object o = i;
        console.println("o=" + o);
        Int    n = o.as(Int);
        console.println("n=" + n);

        Object o2 = 4.to<Int>();
        console.println("o2=" + o2);

        try
            {
            console.println("i.as(String) should throw " + i.as(String));
            }
        catch (Exception e)
            {
            console.println("i.as(String) failed correctly: \"" + e.text + '"');
            }
        }

    void testTernary()
        {
        console.println("\n** testTernary()");

        Int i = 42;
        console.println("i=" + i);
        console.println("i%2=" + (i % 2));

        console.println("i>40?greater:not -> " + (i > 40 ? "greater" : "not greater"));
        console.println("i%2==0?even:odd  -> " + (i % 2 == 0 ? "even" : "odd"));
        }

    void testSpaceship()
        {
        console.println("\n** testSpaceship()");

        Int a = 42;
        Int b = 45;
        console.println("a=" + a + ", b=" + b + ", a<=>b=" + (a <=> b));
        }

    void testElvis()
        {
        console.println("\n** testElvis()");

        Int a = 42;
        Int b = 45;
        // this is an error: console.println("a=" + a + ", b=" + b + ", a?:b=" + (a ?: b));

        Int? c = a;
        console.println("c=" + c + ", b=" + b + ", c?:b=" + (c ?: b));

        c = null;
        console.println("c=" + c + ", b=" + b + ", c?:b=" + (c ?: b));
        }

    void testElseExpr()
        {
        console.println("\n** testElseExpr()");

        IntLiteral? a = null;
        Int b = 7;
        console.println("a=" + a + ", b=" + b + ", a?.to<Int>():b=" + (a?.to<Int>():b));
        // [11] VAR #-238, Ecstasy:Int64 #2                             // create temp var "#2" to hold the result of the else expression (ok!)
        // [12] VAR #-256, Ecstasy:Nullable | Ecstasy:IntLiteral #3     // create temp var #3 to hold ... um ... wrong! (wasted)     TODO?
        // [13] MOV #0, #3                                              // ... and here's proof: it's just a one-time-use, read-only copy
        // [14] JMP_NULL #3 :else1                                      // here is the '?' operator (ok!)
        // [15] NVOK_01 #3.to() -> #4                                   // here is the to<Int>() (ok!)
        // [16] MOV #4, #2                                              // here's the non-null result (ok!)
        // [17] JMP :end1                                               // all done; skip the else (ok!)
        // [18] :else1: MOV #1, #2                                      // else: move int to int (ok!)
        // [19] :end1: GP_ADD this:stack, #2, this:stack                // all done; do some string concat (ok!)

        if (b==7)
            {
            a = 4;
            }
        console.println("a=" + a + ", b=" + b + ", a?.to<Int>():b=" + (a?.to<Int>():b));
        // [28] VAR #-238, Ecstasy:Int64 #5                             // create temp var "#5" to hold the result of the else expression (ok!)
        // [29] VAR #-256, Ecstasy:Nullable | Ecstasy:IntLiteral #6     // create temp var #6 to hold ... um ... wrong! (wasted)
        // [30] MOV #0, #6                                              // ... and here's proof: it's just a one-time-use, read-only copy
        // [31] JMP_NULL #6 :else2                                      // here is the '?' operator (ok!)
        // [32] NVOK_01 #6.to() -> #7                                   // here is the to<Int>() (ok!)
        // [33] MOV #7, #5                                              // here's the non-null result (ok!)
        // [34] JMP :end2                                               // all done; skip the else (ok!)
        // [35] :else2: MOV #1, #5                                      // else: move int to int (ok!)
        // [36] :end2: GP_ADD this:stack, #5, this:stack                // all done; do some string concat (ok!)
        //
        // Line [33] generates:
        //      Suspicious assignment from: Ecstasy:Int64 to: Ecstasy:Nullable | Ecstasy:IntLiteral
        }

    void testLoop()
        {
        console.println("\n** testLoop()");

        Int i = 10;
        while (i > 0)
            {
            console.println(i--);
            }
        console.println("We Have Lift-Off!!!");
        }

    Int FOUR = 4;
    void testSwitchStmt()
        {
        console.println("\n** testSwitchStmt()");

        switch (FOUR)
            {
            case 2, 3:
                console.println("2 or 3");
                break;

            case 4..5:
                console.println("4");
                if (FOUR == 4)
                    {
                    continue;
                    }
                break;

            case 7:
                console.println("7");
                break;

            default:
                console.println("other");
                break;
            }
        }

    void testSwitchExpr()
        {
        console.println("\n** testSwitchExpr()");

        Int i = 0;
        while (i++ < 10)
            {
            console.println("result for " + i + "=" + switch(i)
                {
                // default: "other";
                case 2, 3:
                case 4:    "between 2 and 4";
                case 5..6: "between 5 and 6";

                case 7: "sieben";

                default: "other";
                });
            }
        }

    void testSwitchExpr2()
        {
        console.println("\n** testSwitchExpr2()");

        Int i = 0;
        while (i++ < 10)
            {
            console.println("result for " + i + "=" + switch()
                {
                case i >= 2 && i <= 4: "between 2 and 4";

                case i == 7: "sieben";

                default: "other";
                });
            }
        }

    void testSwitchExpr3()
        {
        console.println("\n** testSwitchExpr3()");

        Int i = 0;
        while (++i < 6)
            {
            console.println("result for (" + i + "<=>3)=" + switch(i <=> 3)
                {
                case Lesser:  "less";
                case Equal:   "same";
                case Greater: "more";
                });
            }
        }

    void testSwitchExpr4()
        {
        console.println("\n** testSwitchExpr4()");

        Int i = 0;
        while (++i < 8)
            {
            console.println("result for (" + i + "<=>3)=" + switch(i <=> 3, i)
                {
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

    void testStmtExpr()
        {
        console.println("\n** testStmtExpr()");
        console.println("5+3=" + {return 5.to<Int>() + 3;});
        }

    void testAssertTrue()
        {
        console.println("\n** testAssertTrue()");
        assert true;
        console.println("(done)");
        }

    void testAssertTrueExpr()
        {
        console.println("\n** testAssertTrueExpr()");
        assert True != False;
        console.println("(done)");
        }

    void testAssertFalseExpr()
        {
        console.println("\n** testAssertFalseExpr()");
        assert True == False;
        console.println("(done)");
        }

    void testAssertDecl()
        {
        console.println("\n** testAssertDecl()");
        // REVIEW BUGBUG NPE: Iterator<Int> iter = [1].iterator();
        Int[] array = [1];
        Iterator<Int> iter = array.iterator();
        assert Int i := iter.next();
        console.println("i=" + i);
        }

    void testInterval()
        {
        console.println("\n** testInterval()");

        Int a = 2;
        Int b = 5;
        Object c = a..b;
        console.println("range=" + c);

        Range<Int> r = a+1..b+1;
        console.println("range=" + r);
        }

    void testException()
        {
        console.println("\n** testException()");

        Exception e = new Exception("test");
        console.println("e=" + e);

        e = new IllegalArgument("test");
        console.println("e=" + e);
        }

    void testTupleConv()
        {
        console.println("\n** testTupleConv()");

        Tuple<String, IntLiteral> t1 = getTupleSI();
        console.println("t1 = " + t1);

        // TODO: should the following compile?
        // Tuple<String, Int> t2 = getTupleSI();
        }

    Tuple<String, IntLiteral> getTupleSI()
        {
        return ("Hello", 4);
        }

    void testConditional()
        {
        console.println("\n** testConditional()");
        if (String s := checkPositive(17))
            {
            console.println($"should be positive: {s}");
            }

        if (String s := checkPositive(-17))
            {
            console.println($"should be negative: {s} (but this cannot happen)");
            assert;
            }

        String s = "negative";
        s := checkPositive(-99);
        console.println($"-99 => {s}");
        s := checkPositive(99);
        console.println($"99 => {s}");

        String? s2 = s;
        if (String s3 ?= s2)
            {
            console.println($"value is not null: {s3}");
            }
        else
            {
            console.println($"value is null: {s2}");
            assert;
            }

        // should be compiler error:
        // String s3 ?= s2;

        s = "hello world";
        console.println($"s={s}");
        s ?= s2;
        console.println($"s={s}");

        // this will assert (unless s2 is null)
        // assert s2?.size>=0, False;

        // this will not assert
        assert s2?.size>=0, True;
        }

    private conditional String checkPositive(Int i)
        {
        return i < 0 ? false : (true, "positive");
        }

    void testMap()
        {
        console.println("\n** testMap()");

        console.println("Map:[1=one, 2=two]=" + Map:[1="one", 2="two"]);
        }

    void testAssignOps()
        {
        console.println("\n** testAssignOps()");

        Int? n = null;
        n ?:= 4;
        console.println("n=" + n + " (should be 4)");
        n ?:= 7;
        console.println("n=" + n + " (should be 4)");

        Boolean f1 = false;
        f1 &&= true;
        console.println("f1=" + f1 + " (should be false)");

        Boolean f2 = false;
        f2 ||= true;
        console.println("f2=" + f2 + " (should be true)");

        Boolean f3 = true;
        f3 &&= false;
        console.println("f3=" + f3 + " (should be false)");

        Boolean f4 = true;
        f4 ||= false;
        console.println("f4=" + f4 + " (should be true)");

        Boolean f5 = true;
        f5 &&= true;
        console.println("f5=" + f5 + " (should be true)");

        Boolean f6 = false;
        f6 ||= false;
        console.println("f6=" + f6 + " (should be false)");
        }

    void testConstants()
        {
        import Ecstasy.collections.Hasher;
        import Ecstasy.collections.NaturalHasher;

        console.println("\n** testConstants()");

        IntLiteral lit = 42;
        console.println("lit=" + lit);

        Point point1 = new Point(0, 2);
        console.println($"point1={point1}");

        NamedPoint point2 = new NamedPoint("top-left", 1, 0);
        console.println($"point2={point2}");

        Hasher<Point>      hasherP = new NaturalHasher<Point>();
        Hasher<NamedPoint> hasherN = new NaturalHasher<NamedPoint>();

        assert Point.hashCode(point1)      == Point.hashCode(point2);
        assert Point.hashCode(point1)      == hasherP.hashOf(point1);
        assert Point.hashCode(point2)      == hasherP.hashOf(point2);
        assert NamedPoint.hashCode(point2) == hasherN.hashOf(point2);

        Point point3 = point2;

        assert point1 == point3;
        assert Point.equals(point1, point3);
        assert point1 <=> point3 == Equal;
        assert Point.compare(point1, point3) == Equal;
        }

    const Point(Int x, Int y);

    const NamedPoint(String name, Int x, Int y)
            extends Point(2*y, x + 1)
        {
        @Override
        Int estimateStringLength()
            {
            return super() + name.size;
            }

        @Override
        void appendTo(Appender<Char> appender)
            {
            name.appendTo(appender.add('('));
            x.appendTo(appender.add(": x="));
            y.appendTo(appender.add(", y="));
            appender.add(')');
            }
        }

    void testImport()
        {
        console.println("\n** testImport()");

        import Int as Q;
        Q x = 42;
        console.println("x=" + x);
        }

    void testChild()
        {
        console.println("\n** testChild()");

        Order order = new Order("Order-17");
        console.println("order=" + order);

        Order.OrderLine line = order.addLine("item-5");
        console.println("line=" + line);

        order = new EnhancedOrder("Order-18");
        line = order.addLine("item-6");
        console.println("line=" + line);
        }

    class Order(String id)
        {
        Int lineCount;
        DateTime date = clock.now;

        @Override
        String toString()
            {
            return id;
            }

        OrderLine addLine(String descr)
            {
            return new OrderLine(++lineCount, descr);
            }

        class OrderLine(Int lineNumber, String descr)
            {
            @Override
            String toString()
                {
                return this.Order.toString() + ": " + descr;
                }
            }
        }

    class EnhancedOrder(String id)
            extends Order(id)
        {
        @Override
        class OrderLine(Int lineNumber, String descr)
            {
            @Override
            String toString()
                {
                return this.EnhancedOrder.toString() +
                    ": " + lineNumber + ") " + descr + " @ " + date;
                }
            }
        }

    void countdown()
        {
        console.println("Countdown!");
        for (Int i : 10..1)
            {
            console.println($"{i} ...");
            }
        console.println("We have lift-off!");
        }
    }
