module test2.examples.org {

    void run() {
        @Inject Console console;

        Color c = Red;
        console.print(c.ordinal);
        console.print(c.name);
        console.print(c);
    }

    enum Color {Red, Green, Blue}
}