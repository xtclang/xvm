module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println(test());
        }

    static Int test()
        {
        return compute(1);

        private Int compute(Int i) // used to fail to compile because of "no this"
            {
            return i*i;
            }
        }
    }