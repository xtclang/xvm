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
            bundle = Null;
            }
        while (serviceInfo);

        report(bundle?); // used to fail to compile
        }

    void report(String message)
        {
        console.println(message);
        }
    }