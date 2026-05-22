package stringTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running String tests >>>>");

        shouldGetCharArrayForEmptyString();
        shouldGetCharArrayString();
        createStringFromEmptyArray();
        createStringFromArray();

        console.print("<<<< Finished String tests <<<<");
    }

    void shouldGetCharArrayForEmptyString() {
        String s     = "";
        Char[] chars = s.chars;
        assert chars.size == 0;
        assert chars.is(immutable);
    }

    void shouldGetCharArrayString() {
        String s     = "abcdef";
        Char[] chars = s.chars;
        assert chars.size == s.size;
        assert chars.is(immutable);
        assert chars[0] == 'a';
        assert chars[1] == 'b';
        assert chars[2] == 'c';
        assert chars[3] == 'd';
        assert chars[4] == 'e';
        assert chars[5] == 'f';
    }

    void createStringFromEmptyArray() {
        Char[] chars = new Array();
        String s     = new String(chars);
        assert s.empty;
        assert s.size == 0;
        chars.add('a');
        assert chars.size == 1;
        assert s.empty;
        assert s.size == 0;
        assert s == "";
    }

    void createStringFromArray() {
        Char[] chars = new Array();
        chars.add('a');
        chars.add('b');
        chars.add('c');

        String s = new String(chars);
        assert s.size == 3;

        chars.add('d');
        assert chars.size == 4;
        assert s.size == 3;
        assert s == "abc";
    }
}
