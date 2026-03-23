
package int128ArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int128 Array Tests >>>>");

        shouldCreateWithCapacity();
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

        console.print(">>>> Running Int128 Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Int128[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateConstantArray() {
        Int128[] array = [10, 20, Int128.MaxValue];
        assert array.size == 3;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == Int128.MaxValue;
    }

    void shouldBeEmpty() {
        Int128[] array = new Array();
        assert array.size == 0;
//        assert array.empty;
    }

    void shouldAddElement() {
        Int128[] array = new Array();
        array.add(10);
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddElementUsingOperator() {
        Int128[] array = new Array();
        array += 10;
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddMultipleElement() {
        Int128[] array = new Array();
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
        Int128[] array = [10, 20, Int128.MaxValue];
        Int128 c = ++array[1];
        assert c == 21;
        assert array[0] == 10;
        assert array[1] == 21;
        assert array[2] == Int128.MaxValue;
    }

    void shouldPostInc() {
        Int128[] array = [10, 20, Int128.MaxValue];
        Int128 c = array[1]++;
        assert c == 20;
        assert array[0] == 10;
        assert array[1] == 21;
        assert array[2] == Int128.MaxValue;
    }

    void shouldPreDec() {
        Int128[] array = [10, 20, Int128.MaxValue];
        Int128 c = --array[2];
        assert c == (Int128.MaxValue - 1);
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (Int128.MaxValue - 1);
    }

    void shouldPostDec() {
        Int128[] array = [10, 20, Int128.MaxValue];
        Int128 c = array[2]--;
        assert c == Int128.MaxValue;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (Int128.MaxValue - 1);
    }

    void shouldAddInPlace() {
        Int128[] array = [10, 20, Int128.MaxValue];
        array[1] += 5;
        assert array[0] == 10;
        assert array[1] == 25;
        assert array[2] == Int128.MaxValue;
    }

    void shouldSubInPlace() {
        Int128[] array = [10, 20, Int128.MaxValue];
        array[2] -= 5;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (Int128.MaxValue - 5);
    }

    void shouldMultiplyInPlace() {
        Int128[] array = [10, 20, 30];
        array[1] *= 5;
        assert array[0] == 10;
        assert array[1] == 100;
        assert array[2] == 30;
    }

    void shouldDivideInPlace() {
        Int128[] array = [10, 20, 30];
        array[1] /= 5;
        assert array[0] == 10;
        assert array[1] == 4;
        assert array[2] == 30;
    }

    void shouldModulusInPlace() {
        Int128[] array = [10, 21, 30];
        array[1] %= 5;
        assert array[0] == 10;
        assert array[1] == 21 % 5;
        assert array[2] == 30;
    }

    void shouldShiftLeftInPlace() {
        Int128[] array = [10, 21, 30];
        array[1] <<= 5;
        assert array[0] == 10;
        assert array[1] == 21 << 5;
        assert array[2] == 30;
    }

    void shouldShiftRightInPlace() {
        Int128[] array = [10, 21, 30];
        array[1] >>= 5;
        assert array[0] == 10;
        assert array[1] == 21 >> 5;
        assert array[2] == 30;
    }

    void shouldUnsignedShiftRightInPlace() {
        Int128[] array = [10, 21, 30];
        array[1] >>>= 5;
        assert array[0] == 10;
        assert array[1] == 21 >>> 5;
        assert array[2] == 30;
    }

    void shouldAndInPlace() {
        Int128[] array = [10, 21, 30];
        array[1] &= 5;
        assert array[0] == 10;
        assert array[1] == 21 & 5;
        assert array[2] == 30;
    }

    void shouldOrInPlace() {
        Int128[] array = [10, 21, 30];
        array[1] |= 5;
        assert array[0] == 10;
        assert array[1] == 21 | 5;
        assert array[2] == 30;
    }

    void shouldXorInPlace() {
        Int128[] array = [10, 0x4A, 30];
        array[1] ^= 0x0F;
        assert array[0] == 10;
        assert array[1] == 0x45;
        assert array[2] == 30;
    }
}
