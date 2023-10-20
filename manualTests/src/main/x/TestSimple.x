module TestSimple {
    @Inject Console console;

    void run() {
        test();
    }

    void test() {
        for (Int index : [0, 1]) {
            Int info;
            if (info := get(), info == 0 && index == 0) {
                break;
            }

            try {
                info = TODO
            } catch (Exception e) {
                @Inject Console console;
                 // this used to blow at run-time caused by the incorrect computation of the
                 // exception handle placed by the Catch op-code
                console.print($"Failure at {index}; {e.text}");
                continue;
            }
        }
    }

    conditional Int get() {
        return False;
    }
}