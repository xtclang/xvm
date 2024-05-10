module TestSimple {

    @Inject Console console;

    void run() {
        String s;
        s = "?"; s := test(True, True);
        console.print($"1) {s}");

        s = "?"; s := test(True, False);
        console.print($"2) {s}");

        s = "?"; s := test(False, True);
        console.print($"3) {s}");

        s = "?"; s := test(False, False);
        console.print($"4) {s}");
    }

    conditional String test(Boolean b0, Boolean b1) {
        return b0                                 // this used to fail to compile
            ? (b1 ? False : (True,"def"))
            : (b1 ? (True,"abc") : (True, "xyz"));
    }
}