module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Map<String, Int> m = Map:["answer"=answer];
        console.println(m);
        }

    @Lazy Int answer.calc()
        {
        return 42;
        }
    }