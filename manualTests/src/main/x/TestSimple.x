module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new Test("hello").foo();
        }

    const Test(String? text)
        {
        void foo()
            {
            if (String text ?= text)
                {
                text = "replace"; // used to fail at run-time
                console.print(text);
                }
            console.print(text);
            }
        }
    }