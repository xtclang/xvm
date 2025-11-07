module TestSimple {
    @Inject Console console;

    void run() {
        console.print(new Test().value); // that used to fail to compile
    }

    class Test() {
        String value = "hello";

        construct(String s) {
            value = s;
        }
    }
}