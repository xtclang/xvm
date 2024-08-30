module TestSimple {
    @Inject Console console;

    void run() {
        String? failure = Null;
        for (Int i : 0..10) {
            if (i > 8) {
                failure ?:= "bug"; // this used to fail to compile
                continue;
            }
        }
    }
}
