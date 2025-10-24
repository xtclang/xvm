module test5.examples.org {
    @Inject Console console;

    void run() {
        test1();
        test2();
    }

    void test1() {
        String[] strings = new Array<String>(3);
        strings.add("hello");

        console.print(strings);
        console.print(strings[0]);
    }

    void test2() {
        Char[] chars = new Array<Char>(3);
        chars.add('a');

        console.print(chars[0]);
    }
}