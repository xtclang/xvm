module test2.examples.org {

    import ecstasy.io.IOException;

    @Inject Console console;
    void run() {
        Color c = Blue;
        console.print(c.ordinal);
        console.print(c.text);
        console.print(c.rgb);
        console.print(c);

        assert c != Green;
        assert c > Green;

        assert !testRedOrNull(c);

        Boolean b = True;
        console.print(b);
        console.print(b.not());
        console.print(b.toInt64()); // this won't work correctly until we compile Boolean
    }

    Boolean testRedOrNull(Color? c) {
        return c == Null || c == Red;
    }

    enum Color(String text, Int rgb) {
        Red("R", 0), Green("G", 255), Blue("B", 255*255)
    }
}