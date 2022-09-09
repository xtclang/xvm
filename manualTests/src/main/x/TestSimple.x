module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println(new Char(Int:64));
        console.println(new Char([64]));
        console.println(new Char([128, 3, 62]));
        }
    }