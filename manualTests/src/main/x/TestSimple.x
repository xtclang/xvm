module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test("     ");
        }

    void test(String text)
        {
        function Boolean(Char) fn = Char.isWhitespace;

        Boolean white = fn(text[0]);
        console.println(white);

        assert text.chars.all(Char.isWhitespace);
        }
    }