module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println(parse());
        }

     protected Int[] | Int parse()
        {
        return [1, 2];
        }
    }
