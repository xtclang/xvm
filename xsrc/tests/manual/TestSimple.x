module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run( )
        {
        new Container().test();
        }

    class Container
        {
        void test()
            {
            console.println("\nM12:");
            new TestA12().f();

            console.println("\nM21:");
            new TestB21().f();

            console.println("\n@M1 @M2 A:");
            new @M1 @M2 TestA().f();

            console.println("\n@M2 @M1 B:");
            new @M2 @M1 TestB().f();
            }

        class TestBase
            {
            public void f()
                {
                console.println("TestA:f");
                }
            }

        class TestA
                extends TestBase
            {
            @Override
            public void f()
                {
                console.println("TestA:f");
                }
            }

        class TestB
                extends TestBase
            {
            @Override
            public void f()
                {
                console.println("TestB:f");
                }
            }

        @M1 @M2
        class TestA12
                extends TestA
            {
            @Override
            public void f()
                {
                console.println("TestA12:f");
                super();
                }
            }

        @M2 @M1
        class TestB21
                extends TestB
            {
            @Override
            public void f()
                {
                console.println("TestB21:f");
                super();
                }
            }

        mixin M1
            into (TestA | TestB)
            {
            @Override
            public void f()
                {
                console.println("M1:f");
                super();
                }
            }

        mixin M2
            into (TestA | TestB)
            {
            @Override
            public void f()
                {
                console.println("M2:f");
                super();
                }
            }
        }
    }
