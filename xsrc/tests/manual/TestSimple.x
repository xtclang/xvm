module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestService ts = new TestService();

        @Future Tuple t = ts.getVoid();
        console.println(&t.completion);
        console.println(t);

        @Future Tuple t2 = ts.f.getVoid();
        console.println(&t2.completion);
        console.println(t2);

        @Future Int i = ts.getInt();
        console.println(&i.completion);
        console.println(i);

        @Future Int i2 = ts.f.getInt();
        console.println(&i2.completion);
        console.println(i2);
        }

    interface Foo
        {
        Int  getInt();
        void getVoid();
        }

    service TestService
            delegates Foo(f)
        {
        construct()
            {
            f = new FooImpl();
            }

        @Atomic Foo f;

        static service FooImpl
                implements Foo
            {
            @Override
            Int getInt()
                {
                for (Int i : [0..1000))
                    {
                    }

                return 42;
                }

            @Override
            void getVoid()
                {
                for (Int i : [0..1000))
                    {
                    }
                }
            }
        }
    }