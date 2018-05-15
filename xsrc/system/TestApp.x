import annotations.AtomicVar;

class TestApp
    {
    static void print(Object o)
        {
        @Inject io.Console console;
        console.println(o);
        }

    // entry point
    void run()
        {
        test1();
        test2();
        testService();
        testRef("hi");
        testArray();
        testTuple();
        testConst();
        testReal1();
        }

    static Int getIntValue()
        {
//        Int c = 5;
//        Int i = 0;
//        while (i < c)
//            {
//            i = i + 2;
//            }
//        return i;
        return 42;
        }

    static String getStringValue()
        {
        return "Hello " + "world!";
        }

    static void test1()
        {
        @Inject io.Console console;

        String s = getStringValue();
        console.print("\n*** ");
        console.println(s);
        console.println();

        Int i = getIntValue();
        print(i);

        if (Int of : s.indexOf("world"))
            {
            assert(of + s.size == 6 + 12);
            print(of.toString());
            }
        }

    static void test2()
        {
        TestClass t = new TestClass("Hello World!");

        print(t);
        print(t.prop1);
        print(t.method1());

        try
            {
            t.exceptional("handled");
            }
        catch (Exception e)
            {
            print(e);
            }

        TestClass t2 = new TestClass2(42, "Goodbye");
        print(t2);
        print(t2.prop1);
        print(t2.method1());

        Function fn = t2.method1;
        print(fn());
        }

    class TestClass(String prop1)
        {
        String prop1;

        construct(String s)
            {
            prop1 = s;
            }
        finally
            {
            print(s);
            }

        Int method1()
            {
            String s = prop1;
            (Boolean f, Int of) = s.indexOf("World", null);
            return of + s.size;
            }

        Int exceptional(String? s)
            {
            throw new Exception(s);
            }

        @Override
        String to<String>()
            {
            return super() + ": prop1=" + prop1;
            }
        }

    class TestClass2
            extends TestClass
        {
        Int prop2;
        Int temp;

        @Inject io.Console console;

        construct(Int i, String s)
            {
            prop2 = i;

            construct TestClass(s);
            }
        finally
            {
            print(i);
            }

        @Override
        Int method1()
            {
            console.print("*** In TestClass2.method1");
            temp = super();
            return temp + prop2;
            }

        @Override
        String to<String>()
            {
            return super() + "; prop2=" + prop2;
            }
        }

    static void lambda_1(Int c, Int r, Exception x) {} // TODO: remove

    static void testService()
        {
        TestService svc = new TestService();
        print(svc);

        svc.testConstant();

        Int c = svc.increment(); // counter = 49

        c += 47;
        c /= 2;
        assert(c == 48);

        svc.counter = 17;
        c = svc.counter;
        assert(c == 17);

        function Int() fnInc = &svc.increment(); // counter = 18
        c = fnInc();
        print(c);

        @Future Int fc = svc.increment();  // counter = 19
        FutureVar<Int> rfc = &fc;
        print(rfc);
        print(fc);
        print(rfc);

        FutureVar<Int> rfc2;
        @Future Int fc2 = svc.increment(); // counter = 20;
        rfc2 = &rfc;

        rfc2.whenComplete((r, x) ->
            {
            print(c);
            print(r);
            print(x);
            });

        // handled exception
        try
            {
            c = svc.exceptional(0);
            }
        catch (Exception e)
            {
            print(e);
            }

        // setting the "future" value should blow
        try
            {
            rfc.set(99);
            }
        catch (Exception e)
            {
            print(e);
            }

        assert(++svc.counter2 == 6); // counter2 was initialized with 5
        assert(svc.counter++ == 20); // counter = 21
        assert(svc.increment() == 22);

        print(rfc.RefType);

        svc.counter += 2;
        assert(svc.counter == 23);

        this:service.yield();

        print(svc.serviceName);

        // unhandled exception
        svc.exceptional(0);
        }

    static Int testBlockingReturn(TestService svc)
        {
        return svc.increment();
        }

    static void testService2()
        {
        TestService svc = new TestService();

        Int c = testBlockingReturn(svc);
        print(c);

        @Future Int fc = svc.increment();
        print(fc);
        try
            {
            // setting the "future" value should blow
            fc = getIntValue();
            }
        catch (Exception e)
            {
            print(e);
            }

        using (Timeout t = new Timeout(2000)) // compiles as try-finally
            {
            // this should complete normally
            c = svc.exceptional(1000);
            print(c);
            }

        try (Timeout t = new Timeout(500)) // compiles as try-catch-finally
            {
            // this should timeout
            c = svc.exceptional(1000);
            assert false;
            }
        catch (Exception e)
            {
            print(e);
            }
        }

    static service TestService(Int counter = 48)
        {
        @Inject io.Console console;

        Int counter
            {
            @Override
            Int get()
                {
                console.print("*** In counter.get");
                return super();
                }
            @Override
            void set(Int c)
                {
                console.print("*** In counter.set");
                // console.print(to<String>()); // TODO: what should that be?
                super(c);
                }
            }

        @Atomic Int counter2 = 5;

        private @Inject Clock runtimeClock;

        // pre-increment
        Int increment()
            {
            console.print("*** In TestService.increment");
            return ++counter;
            }

        void testConstant()
            {
            TestApp.Point origin = TestPackage.Origin;
            console.print("*** Origin=" + origin);
            }

        static void lambda_1(Var<Int> iRet, Int cDelay)
            {
            TODO // TODO remove
            }

        // exceptional
        Int exceptional(Int cDelay)
            {
            if (cDelay == 0)
                {
                throw new Exception("test");
                }
            else
                {
                @Future Int iRet;
                runtimeClock.scheduleAlarm(() -> {iRet = cDelay;}, cDelay); // &iRet.set(0)
                return iRet;
                }
            }

        @Override
        String to<String>()
            {
            return super() + ": counter2=" + counter2;
            }
        }

    static void testRef(String arg)
        {
        Var<String> ra = &arg;
        print(ra.get());
        ra.set("bye");
        print(arg);

        assert(ra.const_);

        Var<Int> ri;
            {
            Int i = 1;
            Var<Int> ri2 = &i;
            ri = &i;

            assert(ri == ri2);
            print(ri.get());

            ri.set(2);
            print(i);

            i = 3;
            print(ri2);
            }
        print(ri.get());

        TestClass tc = new TestClass("before");
        Var<String> rp = &tc.prop1;
        print(rp);
        rp.set("after");
        print(tc);

        TestService svc = new TestService();
        ri = &svc.counter;
        ri.get();
        print(ri);

        AtomicVar<Int> ari = &svc.counter2;
        ari.replace(5, 6);
        print(ari);
        }

    static String lambda_2(Var<Int> i)
        {
        TODO // TODO remove
        }

    static void testArray()
        {
        Int[] ai = new Int[]; // mutable Array<Int>
        ai[0] = 1;
        ai[1] = 2;
        print(ai);
        print(ai[0]);
        print(++ai[1]);

        String[] as1 = new Array<String>(5, i -> "value " + i); // fixed size
        // String[] as2 = new String[5] (i -> "value " + i); // alternative syntax
        print(as1);
        print(as1[4]);

        Ref<String> rs = &as1[0];
        print(rs.get());
        rs.set("zero");
        print(as1[0]);

        Int[] ai = [1, 2, 3]; // constant
        print(ai[2]);
        }

     static conditional String testConditional(Int i)
        {
        TODO // TODO remove
        }

     static void testTuple()
        {
        Tuple t = ("zero", 0); // literal, therefore constant
        print(t[0]);
        print(t[1]);

        Int i = 0;
        Tuple<String, Int> t2 = ("", i); // fixed-size (rigid)
        print(t2);

        t2[0] = "t";
        t2[1] = 2;
        print(t2);

        Int of = "the test".indexOf(t2); // same as "the test".indexOf("t", 2);
        assert(of == 4);

        if (String s : testConditional(1))
            {
            print(s);
            }

        Tuple<Boolean, String> t = testConditional(-1);
        print(t);

        static conditional String testConditional(Int i)
             {
             if (i > 0)
                {
                return true, "positive";
                }
             return false;
             }
        }

    static const Point(Int x, Int y)
        {
        Int x; // TODO: remove
        Int y; // TODO: remove

        @Override
        Int hash.get()
            {
            return x + y;
            }

        @Override
        String to<String>()
            {
            return "(" + x + ", " + y + ")";
            }
        }

    static const Rectangle(Point topLeft, Int bottomRight)
        {
        Point tl; // TODO: remove
        Point br; // TODO: remove
        }

    enum Color
        {
        Red(0), Green(256), Blue(256*256);
        // TODO: the following constructors should be generated
        // class Red   { construct() {construct Color(0)    ;}
        // class Green { construct() {construct Color(256)  ;}
        // class Blue  { construct() {construct Color(65536);}

        construct(Int i)
            {
            // TODO: re-introduce when constructors are generated
            // pix = i;
            }

        @Override
        String to<String>()
            {
            return super(); // + " " + pix;
            }

        // Int pix;
        }

    static void testConst()
        {
        Point p1 = new Point(0, 1);
        Point p2 = new Point(1, 0);

        print(p1);
        print(p2);
        print(p1 == p2);
        print(p2 > p1);

        Rectangle r = new Rectangle(p2, p1);
        print(r);
        print(r.hash);
        Int i = 42; print(i.hash); // print(42.hash);

        Color c = Blue;
        print(c);
        print(c.ordinal);

        c = Green;
        print(c);
        print(c.ordinal);
        }

    mixin Formatter(String prefix) into Object
        {
        String prefix; // TODO: remove

        @Override
        String to<String>()
            {
            return prefix + super();
            }
        }

    static const PrettyPoint
            extends Point
            incorporates Formatter
        {
        construct(Int x, Int y, String prefix)
            {
            construct Point(x, y);
            construct Formatter(prefix);
            }
        }

    static const PrettyRectangle
            extends Rectangle
            incorporates Formatter
        {
        construct(Point tl, Point br, String prefix)
            {
            construct Rectangle(tl, br);
            construct Formatter(prefix);
            }
        }

    static void testMixin()
        {
        PrettyPoint prp = new PrettyPoint(1, 2, "*** ");
        print(prp);

        Point p2 = new Point(2, 1);
        PrettyRectangle prr = new PrettyRectangle(prp, p2, "+++ ");
        print(prp);

        @BlackHole Int zero = 1;
        assert(zero == 0);

        @Atomic Int ai = 0;
        }

    static void testReal1()
        {
//        @Inject io.Console console;

        Int i;
        Int j = 1;

        i = j;

//        Class<Point> clzP = Point;

//        Class<Array<Point>> clzAP = Array<Point>;
        }

    mixin BlackHole
            into Var<Int>
        {
        @Override Int get() {return 0;}
        @Override void set(Int i) {}
        }
    }