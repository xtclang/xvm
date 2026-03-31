
package charArrayTests {

    @Inject Console console;

    // Obtain the largest valid Char
    Char maxChar() {
        Int MaxChar = 0x10FFFF;
        Char c = 'a';
        return c + (MaxChar - 97);
    }

    // Obtain the smallest valid Char (0x0000)
    Char minChar() {
        Char c = 'a';
        return c - 97;
    }

    void run() {
        console.print(">>>> Running Char Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateConstantArray();
        shouldBeEmpty();
        shouldAddElement();
        shouldAddElementUsingOperator();
        shouldAddMultipleElement();
        shouldPreInc();
        shouldPreIncOutOfBounds();
        shouldPostInc();
        shouldPostIncOutOfBounds();
        shouldPreDec();
        shouldPreDecOutOfBounds();
        shouldPostDec();
        shouldPostDecOutOfBounds();
        shouldAddInPlace();
        shouldAddInPlaceOutOfBounds();
        shouldSubInPlace();
        shouldSubInPlaceOutOfBounds();

        console.print(">>>> Running Char Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Char[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateConstantArray() {
        Char[] array = ['a', 'b', 'z'];
        assert array.size == 3;
        assert array[0] == 'a';
        assert array[1] == 'b';
        assert array[2] == 'z';
    }

    void shouldBeEmpty() {
        Char[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        Char[] array = new Array();
        array.add('a');
        assert array.size == 1;
        assert array[0] == 'a';
    }

    void shouldAddElementUsingOperator() {
        Char[] array = new Array();
        array += 'a';
        assert array.size == 1;
        assert array[0] == 'a';
    }

    void shouldAddMultipleElement() {
        Char[] array = new Array();
        array.add('a');
        array.add('b');
        array.add('c');
        array.add('d');
        array.add('e');
        array.add('f');
        array.add('g');
        array.add('h');
        array.add('i');
        array.add('j');
        assert array.size == 10;
        assert array[0] == 'a';
        assert array[1] == 'b';
        assert array[2] == 'c';
        assert array[3] == 'd';
        assert array[4] == 'e';
        assert array[5] == 'f';
        assert array[6] == 'g';
        assert array[7] == 'h';
        assert array[8] == 'i';
        assert array[9] == 'j';
    }

    void shouldPreInc() {
        Char[] array = ['a', 'b', 'z'];
        Char c = ++array[1];
        assert c == 'c';
        assert array[1] == 'c';
    }

// ToDo requires utf21 support in ArrayᐸCharᐳ.java
    void shouldPreIncOutOfBounds() {
//        Char[] array = ['a', 'b', 'z'];
//        array[1] = maxChar();
//        try {
//            Char c = ++array[1];
//            assert as "expected OutOfBounds to be thrown";
//        } catch (OutOfBounds e) {
//            // expected
//        }
    }

    void shouldPostInc() {
        Char[] array = ['a', 'b', 'z'];
        Char c = array[1]++;
        assert c == 'b';
        assert array[1] == 'c';
    }

// ToDo requires utf21 support in ArrayᐸCharᐳ.java
    void shouldPostIncOutOfBounds() {
//        Char[] array = ['a', 'b', 'z'];
//        array[1] = maxChar();
//        try {
//            Char c = array[1]++;
//            assert as "expected OutOfBounds to be thrown";
//        } catch (OutOfBounds e) {
//            // expected
//        }
    }

    void shouldPreDec() {
        Char[] array = ['a', 'b', 'z'];
        Char c = --array[2];
        assert c == 'y';
        assert array[2] == 'y';
    }

    void shouldPreDecOutOfBounds() {
        Char[] array = ['a', 'b', 'z'];
        array[1] = minChar();
        try {
            Char c = --array[1];
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldPostDec() {
        Char[] array = ['a', 'b', 'z'];
        Char c = array[2]--;
        assert c == 'z';
        assert array[2] == 'y';
    }

    void shouldPostDecOutOfBounds() {
        Char[] array = ['a', 'b', 'z'];
        array[1] = minChar();
        try {
            Char c = array[1]--;
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldAddInPlace() {
        Char[] array = ['a', 'b', 'z'];
        array[1] += 5;
        assert array[1] == 'g';
    }

// ToDo requires utf21 support in ArrayᐸCharᐳ.java
    void shouldAddInPlaceOutOfBounds() {
//        Char[] array = ['a', 'b', 'z'];
//        array[1] = maxChar();
//        try {
//            array[1] += 5;
//            assert as "expected OutOfBounds to be thrown";
//        } catch (OutOfBounds e) {
//            // expected
//        }
    }

    void shouldSubInPlace() {
        Char[] array = ['a', 'b', 'z'];
        array[2] -= 5;
        assert array[2] == 'u';
    }

    void shouldSubInPlaceOutOfBounds() {
        Char[] array = ['a', 'b', 'z'];
        array[1] = minChar();
        try {
            array[1] -= 5;
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }
}
