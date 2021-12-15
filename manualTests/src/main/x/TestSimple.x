module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        String s = ""; // TODO should not need to be assigned here

        if (!(s := foo()))
            {
            console.println("no string to print");
            return;
            }

        console.println($"s={s}");
        }

    conditional String foo()
        {
        return True, "hello";
        }
    }