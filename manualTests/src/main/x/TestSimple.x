module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.print(new Base().value);
        console.print(new Derived().value); // used to blow at run time
        }

    class Base
        {
        @Lazy Int value.calc()
            {
            return 1;
            }
        }

    class Derived
            extends Base
        {
        @Override
        @Lazy Int value.calc()
            {
            return 2;
            }
        }
    }