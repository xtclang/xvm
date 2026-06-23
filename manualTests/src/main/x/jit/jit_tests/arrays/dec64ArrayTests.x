
package dec64ArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec64 Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateArrayInitializedWithZeroValue();
        shouldCreateArrayInitializedWithValue();
        shouldCreateConstantArray();
        shouldBeEmpty();
        shouldAddElement();
        shouldAddElementUsingOperator();
        shouldAddMultipleElement();
        shouldAddInPlace();
        shouldSubInPlace();
        shouldMultiplyInPlace();
        shouldDivideInPlace();
        shouldModulusInPlace();
        shouldIterateUsingForLoop();
        shouldIterateUsingIterator();
        shouldDeleteSpecificIndexFromArray();
        shouldInsertValueIntoArray();

        console.print(">>>> Running Dec64 Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Dec64[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        Dec64[] array = new Dec64[4](0.0);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 0.0 && array[1] == 0.0 && array[2] == 0.0 && array[3] == 0.0;
    }

    void shouldCreateArrayInitializedWithValue() {
        Dec64[] array = new Dec64[4](3.14);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 3.14 && array[1] == 3.14 && array[2] == 3.14 && array[3] == 3.14;
    }

    void shouldCreateConstantArray() {
        Dec64[] array = [10, 20, 100];
        assert array.size == 3;
        assert array[0] == 10 && array[1] == 20 && array[2] == 100;
    }

    void shouldBeEmpty() {
        Dec64[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        Dec64[] array = new Array();
        array.add(10);
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddElementUsingOperator() {
        Dec64[] array = new Array();
        array += 10;
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddMultipleElement() {
        Dec64[] array = new Array();
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

    void shouldAddInPlace() {
        Dec64[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(100);
        array[1] += 5;
        assert array[0] == 10 && array[1] == 25 && array[2] == 100;
    }

    void shouldSubInPlace() {
        Dec64[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(100);
        array[2] -= 5;
        assert array[0] == 10 && array[1] == 20 && array[2] == (100 - 5);
    }

    void shouldMultiplyInPlace() {
        Dec64[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array[1] *= 5;
        assert array[0] == 10 && array[1] == 100 && array[2] == 30;
    }

    void shouldDivideInPlace() {
        Dec64[] array = new Array();
        array.add(10);
        array.add(20);
        array.add(30);
        array[1] /= 5;
        assert array[0] == 10 && array[1] == 4 && array[2] == 30;
    }

    void shouldModulusInPlace() {
        Dec64[] array = new Array();
        array.add(10);
        array.add(21);
        array.add(30);
        array[1] %= 5;
        assert array[0] == 10 && array[1] == 21 % 5 && array[2] == 30;
    }

    void shouldIterateUsingForLoop() {
        Dec64[] array = new Array();
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
        for (Dec64 n : array) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldIterateUsingIterator() {
        Dec64[] array = new Array();
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
        for (Dec64 n : array.iterator()) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldDeleteSpecificIndexFromArray() {
        Dec64[] array = new Array();
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
        Dec64[] array = new Array();
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
