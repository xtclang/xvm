package enumTests {

    import ecstasy.io.IOException;

    void run() {
        @Inject Console console;

        console.print(">>>> Running EnumTests >>>>");

        Color c = Blue;
        assert c.ordinal == 2;
        assert c.text == "B";
        assert c.rgb == 65_025;
        console.print(c);

        assert c != Green;
        assert c > Green;

        assert !testRedOrNull(c);
        assert !testRed(c);

        Boolean f = False;
        Boolean t = True;
        assert !t.not();
        assert f.toInt64() == 0;
        assert t.toInt64() == 1;

        assert f == f;
        assert f != t;
        assert f <=> t == Lesser;
        assert t <=> f == Greater;
        assert t <=> t == Equal;

        Color|Int cint = Red;
        assert testRed1(cint);
        assert testRed2(cint);
        assert testRed3(cint);

        Ordered lesser = Lesser;
        assert lesser.reversed.ordinal == Greater.ordinal;
    }

    Boolean testRedOrNull(Color? c) {
        return c == Null || c == Red;
    }

    Boolean testRed(Color? c) {
        return c == Red;
    }

    Boolean testRed1(Color|Int cint) {
        if (Blue == cint) {
            assert;
        }
        return Red == cint;
    }

    Boolean testRed2(Color|Int cint) {
        if (cint.is(Color)) {
            cint = 111;
            cint = -cint;
            cint = cint * 43;
        }
        return cint != 42;
    }

    Boolean testRed3(Color|Int cint) {
        if (cint.is(Color)) {
            cint = 111;
            cint = cint * 43;
            cint = Blue;
        }
        return cint != 42;
    }

    enum Color(String text, Int rgb) {
        Red("R", 0), Green("G", 255), Blue("B", 255*255)
    }
}
