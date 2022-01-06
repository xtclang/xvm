module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        String? s = "hello";
        if (String s2 := s.is(String))
            {
            console.println($"s={s}, s2={s2}");
            }
        }
    }
