package arrayTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running ArrayTests >>>>");

        testStringAsArray();
        testStringArray();
        testCharArray();
    }

    void testStringAsArray() {
        String s  = "hello";
        Char   ch = s[0];
        assert ch == 'h';
    }

    void testStringArray() {
        String[] strings = new Array<String>(3);
        strings.add("hello");
        console.print(strings[0]);

        strings.add("?");
        strings[1] = "world";
        console.print(strings[1]);

        strings = strings.delete(0);
        assert strings[0] == "world";
    }

    void testCharArray() {
        Char[] chars = new Array<Char>(3);
        chars.add('a');
        console.print(chars[0]);
        chars[0] = 'b';
        console.print(chars[0]);
        chars[0]++;
        assert chars[0] == 'c';
        assert ++chars[0] == 'd';
        chars[0] += 2;
        assert chars[0] == 'f';
    }
}