module TestSimple
    {
    @Inject Console console;
    void run()
        {
        String? s = Null;
        String s2;
        if (String test ?= s)
            {
            s2 = s; // this used to fail to compile
            }
        else
            {
            s2 = test; // compiler used to allow that to compile, crashing at RT
            }
        }
    }