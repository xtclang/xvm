module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        C c = new C(0);
        c.validate(5);
        }

    class C(Int i)
        {
        construct(Int i)
            {
            this.i = validate(i);
            }

        private Int validate(Int i)
            {
            return 2*i;
            }
       }
    }