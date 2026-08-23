import ecstasy.collections.Aggregator;

package arrayTests {
    @Inject Console console;

    void run() {

        testStringAsArray();
        testStringArray();
        testConstStringArray();
        testAnonArrayVar();
        testNamedArrayVar();
        testPrimitiveArrayVar();
        testNullablePrimitiveArrayVar();
//        testDistinct(); // TODO: DeferredCollection compilation fails in assembleCapRouting()
        shouldIterateUsingForLoop();
        shouldBeReadOnly();
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

    void testPrimitiveArrayVar() {
        Int[] values = makePrimitiveArray(1, 2);
        assert values[0] == 1;
        assert values[1] == 2;

        Int[] makePrimitiveArray(Int first, Int second) {
            return [first, second];
        }
    }

    void testNullablePrimitiveArrayVar() {
        Array<Int?> values = makeNullablePrimitiveArray(1, Null);
        assert values[0] == 1;
        assert values[1] == Null;

        Array<Int?> makeNullablePrimitiveArray(Int? first, Int? second) {
            return [first, second];
        }
    }

    void testDistinct() {
        Int[] values = [1, 2, 1, 3, 2];
        Int[] result = values.distinct().toArray();

        assert result.size == 3;
        assert result[0] == 1;
        assert result[1] == 2;
        assert result[2] == 3;
    }

    void shouldIterateUsingForLoop() {
        String[] array = ["one", "two", "three"];

        Int i = 0;
        for (String n : array) {
            assert n == array[i];
            i++;
        }
    }

    void shouldBeReadOnly() {
        Int[] array = [0, 1, 2, 4];
        try {
            array[1] += 100;
            assert as "expected ReadOnly exception";
        } catch (ReadOnly e) {
        }
        assert array[1] == 1;
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
