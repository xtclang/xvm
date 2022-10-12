module TestSimple
    {
    @Inject Console console;

    void run(String[] args=[])
        {
        console.println(args);

        String pwd = console.readLine(False);
        console.println(pwd);
        }
    }