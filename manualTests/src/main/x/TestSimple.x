module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Boolean f = True;
        String? p = "hello";
        String s;

        while (!(s := foo()))
            {
            console.println("no foo to print");
            return;
            }
        console.println($"foo={s}");

        while (!(s := bar()))
            {
            console.println("no bar to print");
            return;
            }
        }

    conditional String foo()
        {
        return True, "hello";
        }

    conditional String bar()
        {
        return False;
        }
    }