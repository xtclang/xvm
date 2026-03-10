package arrayTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running ArrayTests >>>>");

        testStringAsArray();
        testStringArray();
        testConstStringArray();
        testCharArray();
        testAnonArrayVar();
        testNamedArrayVar();
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

    void testConstStringArray() {
        String[] strings = ["hello", "world"];
        assert strings[0] == "hello";
        assert strings[1] == "world";
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

    void testAnonArrayVar() {
        Char[] chars = ['a', 'b'];
        Test   test  = new Test();
        test.setBufsAnon(chars);
        assert test.bufs.size == 1;
        Char[] c = test.bufs[0];
        assert c.size == 2;
        assert c[0] == 'a';
        assert c[1] == 'b';
    }

    void testNamedArrayVar() {
        Char[] chars = ['a', 'b'];
        Test   test  = new Test();
        test.setBufsNamed(chars);
        assert test.bufs.size == 1;
        Char[] c = test.bufs[0];
        assert c.size == 2;
        assert c[0] == 'a';
        assert c[1] == 'b';
    }

    class Test {
        Char[][] bufs = [];

        void setBufsAnon(Char[] buf) {
            bufs = [buf];
        }

        void setBufsNamed(Char[] buf) {
            Char[][] bufs = [buf];
            this.bufs = bufs;
        }
    }
}