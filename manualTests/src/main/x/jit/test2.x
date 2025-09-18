module test2.examples.org {

    import ecstasy.io.IOException;

    @Inject Console console;
    void run() {

        Color c = Blue;
        console.print(c.ordinal);
        console.print(c.text);
        console.print(c.rgb);
        console.print(c);

        Boolean b = True;
        console.print(b);
        console.print(b.not());
        console.print(b.toInt64());

        try {
            testThrow(False);
        } catch (IOException e) {
            console.print("IO Exception caught: ", True);
            console.print(e);
        }
    }

    void testThrow(Boolean io) {
        if (io) {
            throw new IOException("Test IO");
        } else {
            throw new Unsupported("Test Unsupported");
        }
    }

    enum Color(String text, Int rgb) {
        Red("R", 0), Green("G", 255), Blue("B", 255*255)
    }
}