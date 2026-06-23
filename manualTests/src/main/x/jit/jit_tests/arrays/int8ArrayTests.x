
package int8ArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int8 Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateFixedSize();
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

        console.print(">>>> Running Int8 Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Int8[] array = new Int8[](10);
        assert array.mutability == Mutable;
        assert array.capacity == 10;
    }

    void shouldCreateFixedSize() {
        Int8[] array = new Int8[4];
        assert array.mutability == Fixed;
        assert array.size == 4 && array[3] == 0;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        Int8[] array = new Int8[4](0);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 0 && array[1] == 0 && array[2] == 0 && array[3] == 0;
    }

    void shouldCreateArrayInitializedWithValue() {
        Int8[] array = new Int8[4](19);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 19 && array[1] == 19 && array[2] == 19 && array[3] == 19;
    }

    void shouldCreateConstantArray() {
        Int8[] array = [10, 20, Int8.MaxValue];
        assert array.mutability == Constant;
        assert array.size == 3;
        assert array[0] == 10 && array[1] == 20 && array[2] == Int8.MaxValue;
    }

    void shouldBeEmpty() {
        Int8[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        Int8[] array = new Array();
        array.add(10);
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddElementUsingOperator() {
        Int8[] array = new Array();
        array += 10;
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddMultipleElement() {
        Int8[] array = new Array();
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
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(Int8.MaxValue);
        Int8 c = ++array[1];
        assert c == 21;
        assert array[0] == 10 && array[1] == 21 && array[2] == Int8.MaxValue;
    }

    void shouldPostInc() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(Int8.MaxValue);
        Int8 c = array[1]++;
        assert c == 20;
        assert array[0] == 10 && array[1] == 21 && array[2] == Int8.MaxValue;
    }

    void shouldPreDec() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(Int8.MaxValue);
        Int8 c = --array[2];
        assert c == (Int8.MaxValue - 1);
        assert array[0] == 10 && array[1] == 20 && array[2] == (Int8.MaxValue - 1);
    }

    void shouldPostDec() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(Int8.MaxValue);
        Int8 c = array[2]--;
        assert c == Int8.MaxValue;
        assert array[0] == 10 && array[1] == 20 && array[2] == (Int8.MaxValue - 1);
    }

    void shouldAddInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(Int8.MaxValue);
        array[1] += 5;
        assert array[0] == 10 && array[1] == 25 && array[2] == Int8.MaxValue;
    }

    void shouldSubInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(Int8.MaxValue);
        array[2] -= 5;
        assert array[0] == 10 && array[1] == 20 && array[2] == (Int8.MaxValue - 5);
    }

    void shouldMultiplyInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array[1] *= 5;
        assert array[0] == 10 && array[1] == 100 && array[2] == 30;
    }

    void shouldDivideInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array[1] /= 5;
        assert array[0] == 10 && array[1] == 4 && array[2] == 30;
    }

    void shouldModulusInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] %= 5;
        assert array[0] == 10 && array[1] == 21 % 5 && array[2] == 30;
    }

    void shouldShiftLeftInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] <<= 2;
        assert array[0] == 10 && array[1] == 21 << 2 && array[2] == 30;
    }

    void shouldShiftRightInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] >>= 5;
        assert array[0] == 10 && array[1] == 21 >> 5 && array[2] == 30;
    }

    void shouldUnsignedShiftRightInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] >>>= 5;
        assert array[0] == 10 && array[1] == 21 >>> 5 && array[2] == 30;
    }

    void shouldAndInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] &= 5;
        assert array[0] == 10 && array[1] == 21 & 5 && array[2] == 30;
    }

    void shouldOrInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] |= 5;
        assert array[0] == 10 && array[1] == 21 | 5 && array[2] == 30;
    }

    void shouldXorInPlace() {
        Int8[] array = new Array();
        array.add(10);
        array.add(0x4A);
        array.add(30);
        array[1] ^= 0x0F;
        assert array[0] == 10 && array[1] == 0x45 && array[2] == 30;
    }

    void shouldIterateUsingForLoop() {
        Int8[] array = new Array();
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
        for (Int8 n : array) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldIterateUsingIterator() {
        Int8[] array = new Array();
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
        for (Int8 n : array.iterator()) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldDeleteSpecificIndexFromArray() {
        Int8[] array = new Array();
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
        Int8[] array = new Array();
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
        assert array[0] == 5 && array[1] == 10 && array[2] == 20 && array[3] == 25;
        assert array[4] == 30 && array[5] == 40;

        // insert at the end
        array.insert(array.size, 50);
        assert array.size == 7;
        assert array[0] == 5 && array[1] == 10 && array[2] == 20 && array[3] == 25;
        assert array[4] == 30 && array[5] == 40 && array[6] == 50;
    }
}
