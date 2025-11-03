module TestSimple {
    @Inject Console console;

    void run() {
        Int a = 0;
        Int b = 1;

        while (b >= a > 0) { // this used to enter the loop
            console.print($"1) {a=} {b=} {b >= a > 0}");
            break;
        }
    }
}
