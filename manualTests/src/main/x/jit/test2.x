module test2.examples.org {

    import ecstasy.io.IOException;

    @Inject Console console;
    void run() {
        Color c = Blue;
        console.print(c.ordinal);
        console.print(c.text);
        console.print(c.rgb);
        console.print(c);

        Ordered order = Greater;
        console.print(order);

//        console.print(c < Green);

        Boolean b = True;
        console.print(b);
        console.print(b.not());
        console.print(b.toInt64());
    }

    enum Color(String text, Int rgb) {
        Red("R", 0), Green("G", 255), Blue("B", 255*255)
    }
}