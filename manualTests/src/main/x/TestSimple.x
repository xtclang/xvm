module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        String s = "hello";
        String s2;
        console.println($"s={s2 <- s}");
        console.println($"s2={s2}");
        }
    }
