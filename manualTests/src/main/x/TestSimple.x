module TestSimple
    {
    @Inject Console console;

    void run(    )
        {
        console.println("Starting");

        TestProperty t = new TestProperty();
        console.println(t.value1);

        t.value2 = 7;
        console.println(t.value2);
        }

    class TestProperty
        {
        @Lazy Int value1.get()
            {
            if (!assigned)
                {
                return 0;
                }
            return super();
            }

        @Stupid Int value2
            {
            Int base = 1;
            @Override
            Int get()
                {
                if (assigned)
                    {
                    base++;
                    return super() + base;
                    }
                return base;
                }

            @Override
            void set(Int i)
                {
                base = i;
                super(i);
                }
            }

        Int value3
            {
            Int base = 1;

            @Override
            Int get()
                {
                return base++;
                }
            }
        }

    mixin Stupid<Referent>
            into Var<Referent>
        {
        @Override
        void set(Referent i)
            {
            super(i);
            }
        }
    }