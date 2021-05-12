module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    // that used to assert during compilation
    (@Unchecked Int32 - Unchecked) toChecked(Int32 n)
        {
        return Int32:0;
        }
    }