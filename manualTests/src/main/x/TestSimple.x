module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String|String[] extension = []; // this used to fail to compile

        console.println(extension);
        }
    }