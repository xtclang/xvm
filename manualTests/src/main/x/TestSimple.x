module TestSimple
    {
    @Inject Console console;

    enum TestEnum(String name)
        {
        Val("value"), // used to be able to override implicit "name"
        }

    void run()
        {
        console.print(TestEnum.Val.name);
        }
    }