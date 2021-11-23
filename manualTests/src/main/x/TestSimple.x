module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int i1 = 1;
        Int i2 = 2;

        Int i3 = i1 > 5
            ? i2 > 4 ? 0 : 1 // TODO CP: requires parenthesis (i2 > 4 ? 0 : 1) to compile
            : 2;
        }
    }