module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Object o = new Object()
            {
            @Override
            String toString()
                {
                return "Hello world!";
                }
            };

        console.println($"o={o}");
        }
    }