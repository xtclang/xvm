module Game
    {
    @Inject Console console;

    void run()
        {
        }

    // this class used to compile even though it has an ambiguous default constructor
    class Test1
        {
        construct(Int i = 0)
            {
            }

        construct(String s = "0")
            {
            }
        }
    }