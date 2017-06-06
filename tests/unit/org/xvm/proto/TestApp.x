module TestApp
    {
    // entry point
    Void run()
        {
        test1();
        test2();
        testService();
        testRef("hi");
        testArray();
        }

    static Int getIntValue()
        {
        return 42;
        }

    static Void test1()
        {
        String s = "Hello world";
        print(s);

        Int i = getIntValue();
        print(i);

        Int of = s.indexOf("world");
        print(of + s.length);
        print(of.toString());
        }

    static Void test2()
        {
        TestClass t = new TestClass("Hello World!");

        print(t.prop1);
        print(t.method1());

        try
            {
            t.throwing("handled");
            }
        catch (Exception e)
            {
            print(e);
            }

        TestClass t2 = new TestClass2(42, "Goodbye");
        print(t2.prop1);
        print(t2.method1());

        Function fn = t2.method1;
        print(fn());
        }

    class TestClass(String prop1)
        {
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
            Int of = s.indexOf("World");
            return of + s.length();
            }

        Int throwing(String? s)
            {
            throw new Exception(s);
            }
        }

    class TestClass2
            extends TestClass
        {
        Int prop2;

        TestClass2 construct(Int i, String s)
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
            return super() + prop2;
            }
        }

    static Void testService()
        {
        TestService svc = new TestService();
        print(svc);

        Int c = svc.increment();
        print(c);

        svc.counter = 17;
        c = svc.counter;
        print(c);

        function Int() fnInc = svc.increment;
        c = fnInc();
        print(c);

        @future Int fc = svc.increment();
        FutureRef<Int> rfc = &fc;
        print(rfc);
        print(fc);
        print(rfc);

        FutureRef<Int> rfc2 = &svc.increment();
        @future Int rfc3 = rfc2;
        rfc2.whenComplete((r, x) ->
            {
            print(c);
            print(r);
            print(x);
            }

        // handled exception
        try
            {
            c = svc.throwing();
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

        print(++svc.counter2);
        print(svc.counter++);
        print(svc.increment()));

        // unhandled exception
        svc.throwing();
        }

    service TestService(Int counter = 48)
        {
        Int counter
            {
            Int get()
                {
                print("In counter.get");
                return super();
                }
            Void set(Int c)
                {
                print("In counter.set");
                super(c);
                }

        @atomic Int counter2 = 5;

        // pre-increment
        Int increment()
            {
            return ++counter;
            }

        // exceptional
        Int throwing()
            {
            throw new Exception("test");
            }
        }

    static Void testRef(String arg)
        {
        Ref<String> ra = &arg;
        print(ra.get());
        ra.set("bye");
        print(arg);

        Ref<Int> ri;
            {
            Int i = 1;
            Ref<Int> ri2 = &i;
            ri = &i;

            print(ri.get())

            ri.set(2);
            print(i);

            i = 3;
            print(ri2);
            }
        print(ri.get())
        }

    static Void testArray()
        {
        Int[] ai = new Int[]; // mutable Array<Int>
        ai[0] = 1;
        ai[1] = 2;
        print(ai);
        print(ai[0]);
        print(++ai[1]);

        String[] as = new Array<String>(5, i -> "value " + i); // fixed size
        String[] as = new String[5] (i -> "value " + i); // alternative syntax
        print(as);
        print(as[4]);

        Ref<String> rs = &as[0];
        print(rs.get());
        rs.set("zero");
        print(as[0]);
        }

     static testTuple()
        {
        Tuple t = ("zero", 0); // literal, therefore constant
        print(t[0]);
        print(t[1]);

        Tuple<String, Int> t2 = new Tuple("", 0); // fixed-size (rigid)
        print(t2);

        t2[0] = "t";
        t2[1] = 2;
        print(t2);

        int of = "the test".indexOf(t2); // same as "the test".indexOf("t", 2);
        assert(of == 4);

        if (String s : testConditional(1))
            {
            print(s);
            }

        Tuple<Boolean, String> t = testConditional(-1));
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
    }