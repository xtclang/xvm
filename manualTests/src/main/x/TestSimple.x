module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Derived d = new Derived();
        d.report();
        d.report();
        }

    class Base
        {
        private Int value  = 1;
        private Int valueB = 11;
        void report()
            {
            console.println($"Base {value++} {valueB++}");
            }
        }

    class Derived
            extends Base
        {
        private Int value = 2;
        public Int valueD = 21;

        @Override
        void report()
            {
            console.println($"Derived {value++} {valueD++}");
            super();
            }
        }
    }