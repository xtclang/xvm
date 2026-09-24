/**
 * Native enum publication, enum structs, lazy singleton references and recursive initialization
 * must still work when their execution state lives outside the constant pool.
 */
module SingletonPaths {
    void run() {
        assert True != False;
        assert Null == Null;
        assert Color.Red.code == 11;
        assert Color.Blue.code == 22;
        assert Color.Red != Color.Blue;
        assert Color.values[0] == Color.Red;

        Ref<Int> ref = &lazyValue;
        assert ref.get() == 42;
        assert lazyValue == 42;
        assert ref.get() == 42;

        for (Int i : 0..<2) {
            Boolean failed = False;
            try {
                String ignored = first();
            } catch (IllegalState e) {
                assert String text ?= e.text, text.indexOf("Circular initialization");
                failed = True;
            }
            assert failed;
        }
    }

    enum Color(Int code) {
        Red(11), Blue(22)
    }

    static @Lazy Int lazyValue.calc() = 42;

    static String first() {
        static String value = second();
        return value;
    }

    static String second() {
        static String value = first();
        return value;
    }
}
