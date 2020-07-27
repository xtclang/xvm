module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        TestProperty t = new TestProperty();
        console.println(t.value);
        }

    class TestProperty
        {
        @Future Int future;

        Int value
            {
            Int base = 1;
            @Override
            Int get()
                {
                return base;
                }
            @Override
            void set(Int i)
                {
                base = i + 1;
                super(i);
                }
            }
        }
    }
