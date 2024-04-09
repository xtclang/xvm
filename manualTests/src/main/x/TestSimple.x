module TestSimple {
    @Inject Console console;

    void run() {
        String|Int x;
        x = "";
        x := f();
        Int size = x.size; // this should not compile; x is not guaranteed to be String

        (Boolean, Int) f() {
            return False, 5;
        }
    }
}
