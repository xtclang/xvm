module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new Test().test();
        }

    service Test()
        {
        @LogVar() Int value2 = 1;

        void test()
            {
            value2 = value2 + 1; // this used to blow up
            value2 = 3;
            }

        }

    mixin LogVar<Referent>()
            into Var<Referent>
        {
        @Override
        void set(Referent value)
            {
            super(value);
            console.println($"log {value}");
            }
        }
    }