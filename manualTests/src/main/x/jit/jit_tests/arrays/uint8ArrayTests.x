
package uint8ArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt8 Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateArrayInitializedWithZeroValue();
        shouldCreateArrayInitializedWithValue();
        shouldCreateConstantArray();
        shouldBeEmpty();
        shouldAddElement();
        shouldAddElementUsingOperator();
        shouldAddMultipleElement();
        shouldPreInc();
        shouldPostInc();
        shouldPreDec();
        shouldPostDec();
        shouldAddInPlace();
        shouldSubInPlace();
        shouldMultiplyInPlace();
        shouldDivideInPlace();
        shouldModulusInPlace();
        shouldShiftLeftInPlace();
        shouldShiftRightInPlace();
        shouldUnsignedShiftRightInPlace();
        shouldAndInPlace();
        shouldOrInPlace();
        shouldXorInPlace();
        shouldIterateUsingForLoop();
        shouldIterateUsingIterator();
        shouldDeleteSpecificIndexFromArray();
        shouldInsertValueIntoArray();

        console.print(">>>> Running UInt8 Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        UInt8[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        UInt8[] array = new UInt8[4](0);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 0 && array[1] == 0 && array[2] == 0 && array[3] == 0;
    }

    void shouldCreateArrayInitializedWithValue() {
        UInt8[] array = new UInt8[4](19);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 19 && array[1] == 19 && array[2] == 19 && array[3] == 19;
    }

    void shouldCreateConstantArray() {
        UInt8[] array = [10, 20, UInt8.MaxValue];
        assert array.size == 3;
        assert array[0] == 10 && array[1] == 20 && array[2] == UInt8.MaxValue;
    }

    void shouldBeEmpty() {
        UInt8[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        UInt8[] array = new Array();
        array.add(10);
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddElementUsingOperator() {
        UInt8[] array = new Array();
        array += 10;
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddMultipleElement() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array.add(40);
        array.add(50);
        array.add(60);
        array.add(70);
        array.add(80);
        array.add(90);
        array.add(100);
        assert array.size == 10;
        assert array[0] == 10 && array[1] == 20 && array[2] == 30 && array[3] == 40;
        assert array[4] == 50 && array[5] == 60 && array[6] == 70 && array[7] == 80;
        assert array[8] == 90 && array[9] == 100;
    }

    void shouldPreInc() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(UInt8.MaxValue);
        UInt8 c = ++array[1];
        assert c == 21;
        assert array[0] == 10 && array[1] == 21 && array[2] == UInt8.MaxValue;
    }

    void shouldPostInc() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(UInt8.MaxValue);
        UInt8 c = array[1]++;
        assert c == 20;
        assert array[0] == 10 && array[1] == 21 && array[2] == UInt8.MaxValue;
    }

    void shouldPreDec() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(UInt8.MaxValue);
        UInt8 c = --array[2];
        assert c == (UInt8.MaxValue - 1);
        assert array[0] == 10 && array[1] == 20 && array[2] == (UInt8.MaxValue - 1);
    }

    void shouldPostDec() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(UInt8.MaxValue);
        UInt8 c = array[2]--;
        assert c == UInt8.MaxValue;
        assert array[0] == 10 && array[1] == 20 && array[2] == (UInt8.MaxValue - 1);
    }

    void shouldAddInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(UInt8.MaxValue);
        array[1] += 5;
        assert array[0] == 10 && array[1] == 25 && array[2] == UInt8.MaxValue;
    }

    void shouldSubInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(UInt8.MaxValue);
        array[2] -= 5;
        assert array[0] == 10 && array[1] == 20 && array[2] == (UInt8.MaxValue - 5);
    }

    void shouldMultiplyInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array[1] *= 5;
        assert array[0] == 10 && array[1] == 100 && array[2] == 30;
    }

    void shouldDivideInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array[1] /= 5;
        assert array[0] == 10 && array[1] == 4 && array[2] == 30;
    }

    void shouldModulusInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] %= 5;
        assert array[0] == 10 && array[1] == 21 % 5 && array[2] == 30;
    }

    void shouldShiftLeftInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] <<= 5;
        assert array[0] == 10 && array[1] == 21 << 5 && array[2] == 30;
    }

    void shouldShiftRightInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] >>= 5;
        assert array[0] == 10 && array[1] == 21 >> 5 && array[2] == 30;
    }

    void shouldUnsignedShiftRightInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] >>>= 5;
        assert array[0] == 10 && array[1] == 21 >>> 5 && array[2] == 30;
    }

    void shouldAndInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] &= 5;
        assert array[0] == 10 && array[1] == 21 & 5 && array[2] == 30;
    }

    void shouldOrInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] |= 5;
        assert array[0] == 10 && array[1] == 21 | 5 && array[2] == 30;
    }

    void shouldXorInPlace() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(0x4A);
        array.add(30);
        array[1] ^= 0x0F;
        assert array[0] == 10 && array[1] == 0x45 && array[2] == 30;
    }

    void shouldIterateUsingForLoop() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array.add(40);
        array.add(50);
        array.add(60);
        array.add(70);
        array.add(80);
        array.add(90);
        array.add(100);

        Int i = 0;
        for (UInt8 n : array) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldIterateUsingIterator() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array.add(40);
        array.add(50);
        array.add(60);
        array.add(70);
        array.add(80);
        array.add(90);
        array.add(100);

        Int i = 0;
        for (UInt8 n : array.iterator()) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldDeleteSpecificIndexFromArray() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array.add(40);
        array.add(50);

        // delete from middle
        array.delete(2);
        assert array.size == 4;
        assert array[0] == 10 && array[1] == 20 && array[2] == 40 && array[3] == 50;

        // delete from index zero
        array.delete(0);
        assert array.size == 3;
        assert array[0] == 20 && array[1] == 40 && array[2] == 50;

        // delete from last index
        array.delete(2);
        assert array.size == 2;
        assert array[0] == 20 && array[1] == 40;
    }

    void shouldInsertValueIntoArray() {
        UInt8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array.add(40);

        // insert in middle
        array.insert(2, 25);
        assert array.size == 5;
        assert array[0] == 10 && array[1] == 20 && array[2] == 25 && array[3] == 30;
        assert array[4] == 40;

        // insert at index zero
        array.insert(0, 5);
        assert array.size == 6;
        assert array[0] == 5  && array[1] == 10 && array[2] == 20 && array[3] == 25;
        assert array[4] == 30 && array[5] == 40;

        // insert at the end
        array.insert(array.size, 50);
        assert array.size == 7;
        assert array[0] == 5  && array[1] == 10 && array[2] == 20 && array[3] == 25;
        assert array[4] == 30 && array[5] == 40 && array[6] == 50;
    }
}
