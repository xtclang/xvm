module test5.examples.org {
    @Inject Console console;

    void run() {
        test1();
    }

    void test1() {
        String[] strings = new Array<String>(3);
        // strings.add("hello");

        console.print(strings);
    }
}