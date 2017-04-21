module TestApp
    {
    // entry point
    Void run()
        {
        test1();
        test2();
        testRef();
        testService();
        }

    static Int getIntValue()
        {
        return 42;
        }

    static Void test1()
        {
        String s = "Hello World";
        print(s);

        Int i = getIntValue();
        print(i);
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

        TestClass t2 = new TestClass2("Goodbye");
        print(t2.prop1);
        print(t2.method1());

        TestClass t3 = new TestClass2("ABC");
        Function fn = t3.method1;
        print(fn());
        }

    static Void testRef()
        {
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
        protected Int prop2;

        TestClass2 construct(String s)
            {
            prop2 = s.length();

            construct TestClass(s);
            }

        @Override
        Int method1()
            {
            return -super();
            }
        }

    static Void testService()
        {
        TestService service = new TestService();
        print(service);

        Int c = service.increment();
        print(c);

        service.counter = 17;
        c = service.counter;
        print(c);

        function Int() fnInc = service.increment;
        c = fnInc();
        print(c);

        @future Int fc = service.increment();
        FutureRef<Int> rfc = &fc;
        print(rfc);
        print(fc);
        print(rfc);

        // setting the "future" value should blow
        try
            {
            rfc.set(99);
            }
        catch (Exception e)
            {
            print(e);
            }

        // handled exception
        try
            {
            c = service.throwing();
            }
        catch (Exception e)
            {
            print(e);
            }

        // unhandled exception
        c = service.throwing();
        print(c);
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

        // post-increment
        Int increment()
            {
            return counter++;
            }

        // exceptional
        Int throwing()
            {
            throw new Exception("test");
            }
        }
    }