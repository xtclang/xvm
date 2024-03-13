/**
 * Medium complexity array tests.
 */
class Medium {
    @Test
    void mutableInts() {
        Int[] array = create(Mutable);
        array += 1;
        array += 2;
        checkElements(array, Int.as(Type));
    }

    @Test
    void fixedBooleans() {
        Boolean[] array = new Boolean[2] (i -> i % 2 == 0);
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