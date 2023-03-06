module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new Derived().f(new Narrow());
        }

    class Wide {}
    class Narrow extends Wide {}

    class Base
        {
        void f(Narrow n)
            {
            }
        }

    class Derived extends Base
        {
        @Override
        void f(Wide n)
            {
            assert n.is(Narrow);
            super(n);
            }
        }
    }