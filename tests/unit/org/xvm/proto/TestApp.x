module TestApp
    {
    // entry point
    Void run()
        {
        test1();

        test2();

        testCounter();
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

        Int c = service.counter;
        print(c);

        c = service.increment();
        print(c);
        c = service.counter;
        print(c);

        // handled exception
        try
            {
            c = service.throwing();
            print(c);
            }
        catch (Exception e)
            {
            print(e);
            }

        // un handled exception
        try
            {
            c = service.throwing();
            }
        catch (Exception e)
            {
            print(e);
            }
        print(c);
        }

    service Counter(Int counter = 48)
        {
        // post-increment
        Int next()
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