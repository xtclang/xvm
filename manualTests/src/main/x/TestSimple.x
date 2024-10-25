module TestSimple
    {
    @Inject Console console;

    void run() {
    }

    void test(String s) {
        Char[] chars = new Char[];
        chars += s[0]; // this used to fail to compile
    }
}