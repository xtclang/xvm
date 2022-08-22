module TestSimple
    {
    @Inject Console console;

    void run()
        {
        for (String s : ["test", "Hello", "World!", "r1785909fhIFDSY6gp@9y8"])
            {
            console.println($"orig={s}, lower={s.toLowercase()}, upper={s.toUppercase()}");
            }

// TODO GG
//        import ecstasy.collections.CaseInsensitive;
//        assert CaseInsensitive.stringStartsWith("This is a test", "this");
//        assert !CaseInsensitive.stringStartsWith("Blah is a test", "this");
//        assert CaseInsensitive.stringEndsWith("This is a test", "TEST");
//        assert !CaseInsensitive.stringEndsWith("Blah is a test", "BLAH");
        }
    }