module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        import ecstasy.io.CharArrayReader;
        import ecstasy.io.TextPosition;

        Reader r = new CharArrayReader("abcde".toCharArray());

        TextPosition p0 = r.position;

        r.nextChar();

        TextPosition p1 = r.position;

        String s = r[p0..p1];
        console.println(s);
        }
    }