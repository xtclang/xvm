module TestSimple {

    @Inject Console console;

    void run() {
    }

    void test(Boolean hex) {
        while (Char ch := next(),
               val n := hex ? ch.asciiHexit() : ch.asciiDigit()) { // used to fail to compile
            console.print(ch);
        }
    }

    conditional Char next() = False;
}