
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
        shouldCreateWithCapacity();
        shouldCreateArrayInitializedWithZeroValue();
        shouldCreateArrayInitializedWithValue();
        shouldCreateConstantArray();
        shouldSliceConstantArray();
        shouldSliceUnicodeArray();
        shouldRejectSliceOutOfBounds();
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
        shouldIterateUsingForLoop();
        shouldIterateUsingIterator();
        shouldDeleteSpecificIndexFromArray();
        shouldInsertValueIntoArray();
    }

    void shouldCreateWithCapacity() {
        Char[] array = new Array(10);
        assert array.capacity >= 10;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        Char[] array = new Char[4](minChar());
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == minChar() && array[1] == minChar()
                && array[2] == minChar() && array[3] == minChar();
    }

    void shouldCreateArrayInitializedWithValue() {
        Char[] array = new Char[4]('z');
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 'z' && array[1] == 'z' && array[2] == 'z' && array[3] == 'z';
    }

    void shouldCreateConstantArray() {
        Char[] array = ['a', 'b', 'z'];
        assert array.size == 3;
        assert array[0] == 'a' && array[1] == 'b' && array[2] == 'z';
    }

    void shouldSliceConstantArray() {
        Char[] chars = "abcdefghijklmnop".chars;

        // the slice crosses a packed-storage boundary and starts between packed characters
        Char[] slice = chars[2 ..< 11];
        assert slice.mutability == Constant;
        assert new String(slice) == "cdefghijk";
        assert new String(chars[10 >.. 2]) == "jihgfedc";
        assert new String(chars[2 >..< 6]) == "def";
        assert new String(slice[1 .. 3]) == "def";
        assert new String(chars[4 .. 4]) == "e";
        assert chars[0 ..< 0].empty;
        assert chars[16 ..< 16].empty;
        assert "".chars[0 ..< 0].empty;
    }

    void shouldSliceUnicodeArray() {
        Char[] chars = "aΩ🙂b界c".chars;

        // Unicode uses three 21-bit codepoints per storage word, including supplementary chars
        assert chars[1] == 'Ω';
        assert chars[2].codepoint == 0x1F642;
        // TODO CP: the lexer represents Char literals as Java chars, truncating this codepoint
        // assert chars[2] == '\U0001F642';
        assert new String(chars[1 ..< 5]) == "Ω🙂b界";
        assert new String(chars[4 .. 1]) == "界b🙂Ω";
        assert new String(chars[2 .. 2]) == "🙂";
    }

    void shouldRejectSliceOutOfBounds() {
        Char[] chars = "abc".chars;
        try {
            Char[] slice = chars[-1 .. 1];
            assert as "expected OutOfBounds for a negative lower bound";
        } catch (OutOfBounds e) {
            // expected
        }
        try {
            Char[] slice = chars[0 .. 3];
            assert as "expected OutOfBounds for an upper bound past the end";
        } catch (OutOfBounds e) {
            // expected
        }
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
        assert array[0] == 'a' && array[1] == 'b' && array[2] == 'c' && array[3] == 'd';
        assert array[4] == 'e' && array[5] == 'f' && array[6] == 'g' && array[7] == 'h';
        assert array[8] == 'i' && array[9] == 'j';
    }

    void shouldPreInc() {
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        Char c = ++array[1];
        assert c == 'c';
        assert array[1] == 'c';
    }

// TODO requires utf21 support in ArrayᐸCharᐳ.java
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
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        Char c = array[1]++;
        assert c == 'b';
        assert array[1] == 'c';
    }

// TODO requires utf21 support in ArrayᐸCharᐳ.java
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
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        Char c = --array[2];
        assert c == 'y';
        assert array[2] == 'y';
    }

    void shouldPreDecOutOfBounds() {
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        array[1] = minChar();
        try {
            Char c = --array[1];
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldPostDec() {
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        Char c = array[2]--;
        assert c == 'z';
        assert array[2] == 'y';
    }

    void shouldPostDecOutOfBounds() {
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        array[1] = minChar();
        try {
            Char c = array[1]--;
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldAddInPlace() {
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        array[1] += 5;
        assert array[1] == 'g';
    }

// TODO requires utf21 support in ArrayᐸCharᐳ.java
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
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        array[2] -= 5;
        assert array[2] == 'u';
    }

    void shouldSubInPlaceOutOfBounds() {
        Char[] array = ['a', 'b', 'z'].toArray(Mutable);
        array[1] = minChar();
        try {
            array[1] -= 5;
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldIterateUsingForLoop() {
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

        Int i = 0;
        for (Char n : array) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldIterateUsingIterator() {
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

        Int i = 0;
        for (Char n : array.iterator()) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldDeleteSpecificIndexFromArray() {
        Char[] array = new Array();
        array.add('a');
        array.add('b');
        array.add('c');
        array.add('d');
        array.add('e');

        array.delete(2);
        assert array.size == 4;
        assert array[0] == 'a' && array[1] == 'b' && array[2] == 'd' && array[3] == 'e';
    }

    void shouldInsertValueIntoArray() {
        Char[] array = new Array();
        array.add('a');
        array.add('b');
        array.add('c');
        array.add('d');

        array.insert(2, 'z');
        assert array.size == 5;
        assert array[0] == 'a' && array[1] == 'b' && array[2] == 'z' && array[3] == 'c';
        assert array[4] == 'd';
    }
}
