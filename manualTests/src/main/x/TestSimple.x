module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Unchecked Int x = 5;

        test(x);
        }

    void test(Int o)
        {
        switch (o.is(_))
            {
            case Unchecked:                     // this used to assert the compilation
                console.println("Unchecked");
                break;

            default:
                console.println("Checked");
                break;
            }
        }
    }