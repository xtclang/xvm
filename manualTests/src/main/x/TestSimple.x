module TestSimple {
    @Inject Console console;

    void run() {
        String s = "hello    there";
        console.print(s.split(' '));       // old behavior
        console.print(s.split(' ', True)); // new behavior
    }

    class Test(String value) {
        private String values;
        construct(String value) {
            this.values = values; // causes a compiler warning
        }
    }
}
