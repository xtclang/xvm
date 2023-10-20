module TestSimple {
    @Inject Console console;

    void run() {

        Char ch = '1';
        assert Int n := ch.asciiDigit(), n == 1;

        console.print($"{n=}");

        if ((Int i, Int j) := foo()) {
            console.print($"{i=} {j=}");
        }
    }

    conditional (UInt8, Int8) foo() {
        return True, 1, -1;
    }
}