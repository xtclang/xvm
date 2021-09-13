module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test2();
        t.value = 1;
        t.value1 = 2;
        t.value2 = 1;
        Int i = t.value;
        console.println(i);
        }

    class Test2
            extends Test
        {
        @Override
        Int value
            {
            @Override
            Int get()
                {
                console.println("In Test2");
                return super();
                }
            }
        }

    class Test
        {
        Int value
            {
            @Override
            Int get()
                {
                Int n = calc();
                return n + super();
                }

            Int calc()
                {
                return 42;
                }

            @Override
            void set(Int n)
                {
                Int old = get(); // used to blow
                assert n != old;
                super(n);
                }
            }

        @Lazy
        Int value1
            {
            @Override
            Int calc()
                {
                return 42;
                }

            @Override
            void set(Int n)
                {
                if (assigned)
                    {
                    Int old = get(); // used to blow
                    assert n != old;
                    }
                super(n);
                }
            }

        Int value2.set(Int n)
            {
            Int old = get();  // used to blow
            assert n != old;
            super(n);
            console.println($"n={n} old={old}");
            }
        }
    }