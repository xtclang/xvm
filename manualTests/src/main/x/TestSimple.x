module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test("abc");
        }

    void test(String text)
        {
        assert text.chars.all(Char.ascii); // this used to fail to compile
        }
    }