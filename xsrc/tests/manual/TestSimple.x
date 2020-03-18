module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        console.println("\nM12:");
        new TestM12().f();

        console.println("\nM21:");
        new TestM21().f();

        console.println("\n@M1 @M2:");
        new @M1 @M2 TestBase().f();

        console.println("\n@M2 @M1:");
        new @M2 @M1 TestBase().f();
        }

    class TestBase
        {
        public void f()
            {
            console.println("TestBase:f");
            }
        }

    @M1 @M2
    class TestM12
            extends TestBase
        {
        @Override
        public void f()
            {
            console.println("TestM12:f");
            super();
            }
        }

    @M2 @M1
    class TestM21
            extends TestBase
        {
        @Override
        public void f()
            {
            console.println("TestM21:f");
            super();
            }
        }

    mixin M1
        extends MBase
        into TestBase
        {
        private Int hidden1 = 1;

        @Override
        public void f()
            {
            console.println("M1:f");
            super();
            }
        }

    mixin M2
        extends MBase
        into TestBase
        {
        private Int hidden2 = 2;

        @Override
        public void f()
            {
            console.println("M2:f");
            super();
            }
        }

    mixin MBase
        into TestBase
        {
        private Int hidden0 = 0;

        @Override
        public void f()
            {
            console.println("MBase:f");
            super();
            }
        }
    }
