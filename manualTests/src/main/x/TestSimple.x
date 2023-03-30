module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test1();
        console.print(test2());
        }

    void test1()
        {
        switch (f1())
            {
            case 0:
                console.print("zero");
                break;

            case 2, 4:
                console.print("even");
                break;

            // case (5, "True"): // used to assert the compiler
            case 5:
                console.print("five");
                break;

            default:
                console.print("other");
                break;
            }
        }

    (Int, String) f1()
        {
        return 4, "";
        }

    String test2()
        {
        switch (f2())
            {
            case (0, _):
                return "a";

            case (_, 4):
                return "b";

            case (4, 5):
                return "c";

            default:
                return "other";
            }
        }

    (Int, Int, String, String) f2()
        {
        return 4, 5, "", "";
        }
    }