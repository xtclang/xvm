module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    void test(Boolean serviceInfo)
        {
        String? bundle;

        do
            {
            if (!serviceInfo)
                {
                bundle = "";
                break;
                }
            bundle = Null; // used to be able to compile without this line
            break;
            }
        while (serviceInfo);

        report(bundle?);
        }

    void report(String message)
        {
        console.println(message);
        }
    }