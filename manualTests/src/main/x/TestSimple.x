module TestSimple
    {
    @Inject Console console;
    @Inject Timer timer;

    void run()
        {
        console.println($"{this:class.name}");
        }
    }
