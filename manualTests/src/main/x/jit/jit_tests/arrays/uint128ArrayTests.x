
package uint128ArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt128 Array Tests >>>>");

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

        console.print(">>>> Running UInt128 Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        UInt128[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        UInt128[] array = new UInt128[4](0);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 0 && array[1] == 0 && array[2] == 0 && array[3] == 0;
    }

    void shouldCreateArrayInitializedWithValue() {
        UInt128[] array = new UInt128[4](19);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 19 && array[1] == 19 && array[2] == 19 && array[3] == 19;
    }

    void shouldCreateConstantArray() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        assert array.size == 3;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == UInt128.MaxValue;
    }

    void shouldBeEmpty() {
        UInt128[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        UInt128[] array = new Array();
        array.add(10);
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddElementUsingOperator() {
        UInt128[] array = new Array();
        array += 10;
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddMultipleElement() {
        UInt128[] array = new Array();
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
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == 30;
        assert array[3] == 40;
        assert array[4] == 50;
        assert array[5] == 60;
        assert array[6] == 70;
        assert array[7] == 80;
        assert array[8] == 90;
        assert array[9] == 100;
    }

    void shouldPreInc() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        UInt128 c = ++array[1];
        assert c == 21;
        assert array[0] == 10;
        assert array[1] == 21;
        assert array[2] == UInt128.MaxValue;
    }

    void shouldPostInc() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        UInt128 c = array[1]++;
        assert c == 20;
        assert array[0] == 10;
        assert array[1] == 21;
        assert array[2] == UInt128.MaxValue;
    }

    void shouldPreDec() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        UInt128 c = --array[2];
        assert c == (UInt128.MaxValue - 1);
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (UInt128.MaxValue - 1);
    }

    void shouldPostDec() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        UInt128 c = array[2]--;
        assert c == UInt128.MaxValue;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (UInt128.MaxValue - 1);
    }

    void shouldAddInPlace() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        array[1] += 5;
        assert array[0] == 10;
        assert array[1] == 25;
        assert array[2] == UInt128.MaxValue;
    }

    void shouldSubInPlace() {
        UInt128[] array = [10, 20, UInt128.MaxValue];
        array[2] -= 5;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (UInt128.MaxValue - 5);
    }

    void shouldMultiplyInPlace() {
        UInt128[] array = [10, 20, 30];
        array[1] *= 5;
        assert array[0] == 10;
        assert array[1] == 100;
        assert array[2] == 30;
    }

    void shouldDivideInPlace() {
        UInt128[] array = [10, 20, 30];
        array[1] /= 5;
        assert array[0] == 10;
        assert array[1] == 4;
        assert array[2] == 30;
    }

    void shouldModulusInPlace() {
        UInt128[] array = [10, 21, 30];
        array[1] %= 5;
        assert array[0] == 10;
        assert array[1] == 21 % 5;
        assert array[2] == 30;
    }

    void shouldShiftLeftInPlace() {
        UInt128[] array = [10, 21, 30];
        array[1] <<= 5;
        assert array[0] == 10;
        assert array[1] == 21 << 5;
        assert array[2] == 30;
    }

    void shouldShiftRightInPlace() {
        UInt128[] array = [10, 21, 30];
        array[1] >>= 5;
        assert array[0] == 10;
        assert array[1] == 21 >> 5;
        assert array[2] == 30;
    }

    void shouldUnsignedShiftRightInPlace() {
        UInt128[] array = [10, 21, 30];
        array[1] >>>= 5;
        assert array[0] == 10;
        assert array[1] == 21 >>> 5;
        assert array[2] == 30;
    }

    void shouldAndInPlace() {
        UInt128[] array = [10, 21, 30];
        array[1] &= 5;
        assert array[0] == 10;
        assert array[1] == 21 & 5;
        assert array[2] == 30;
    }

    void shouldOrInPlace() {
        UInt128[] array = [10, 21, 30];
        array[1] |= 5;
        assert array[0] == 10;
        assert array[1] == 21 | 5;
        assert array[2] == 30;
    }

    void shouldXorInPlace() {
        UInt128[] array = [10, 0x4A, 30];
        array[1] ^= 0x0F;
        assert array[0] == 10;
        assert array[1] == 0x45;
        assert array[2] == 30;
    }
}
