/**
 * Medium complexity array tests.
 */
class Medium {

    void run() {
        fixedBooleans();
        persistentStrings();
        constantChars();
    }

    @Test
    void fixedBooleans() {
        Boolean[] array = create(Fixed);
        array += True;
        array += False;
        checkElements(array, Boolean);
    }

    @Test
    void persistentStrings() {
        String[] array = create(Persistent);
        array += "hello";
        array += "world";
        checkElements(array, String);
    }

    @Test
    void constantChars() {
        Char[] array = create(Constant);
        array += 'x';
        array += 'j';
        checkElements(array, Char);
    }

    private static <T> T[] create(Array.Mutability mutability) {
        return new Array<T>(mutability, []);
    }

    private static <T> void checkElements(T[] array, Type<T> t) {
        assert array.Element == t;
        for (array.Element e : array) {
            assert e.is(T);
        }
    }
}