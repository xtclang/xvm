
package bitArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Bit Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateArrayInitializedWithZeroValue();
        shouldCreateArrayInitializedWithValue();
        shouldCreateConstantArray();
        shouldBeEmpty();
        shouldAddElement();
        shouldAddElementUsingOperator();
        shouldAddMultipleElement();
        shouldIterateUsingForLoop();
        shouldIterateUsingIterator();
        shouldDeleteSpecificIndexFromArray();
        shouldInsertValueIntoArray();

        console.print(">>>> Running Bit Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Bit[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        Bit[] array = new Bit[4](0);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 0 && array[1] == 0 && array[2] == 0 && array[3] == 0;
    }

    void shouldCreateArrayInitializedWithValue() {
        Bit[] array = new Bit[4](1);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == 1 && array[1] == 1 && array[2] == 1 && array[3] == 1;
    }

    void shouldCreateConstantArray() {
        Bit[] array = [0, 1, 1];
        assert array.size == 3;
        assert array[0] == 0;
        assert array[1] == 1;
        assert array[2] == 1;
    }

    void shouldBeEmpty() {
        Bit[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        Bit[] array = new Array();
        array.add(1);
        assert array.size == 1;
        assert array[0] == 1;
    }

    void shouldAddElementUsingOperator() {
        Bit[] array = new Array();
        array += 1;
        assert array.size == 1;
        assert array[0] == 1;
    }

    void shouldAddMultipleElement() {
        Bit[] array = new Array();
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);
        array.add(0);
        array.add(0);
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);
        assert array.size == 10;
        assert array[0] == 1;
        assert array[1] == 0;
        assert array[2] == 1;
        assert array[3] == 1;
        assert array[4] == 0;
        assert array[5] == 0;
        assert array[6] == 1;
        assert array[7] == 0;
        assert array[8] == 1;
        assert array[9] == 1;
    }

    void shouldIterateUsingForLoop() {
        Bit[] array = new Array();
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);
        array.add(0);
        array.add(0);
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);

        Int i = 0;
        for (Bit n : array) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldIterateUsingIterator() {
        Bit[] array = new Array();
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);
        array.add(0);
        array.add(0);
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);

        Int i = 0;
        for (Bit n : array.iterator()) {
            assert n == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldDeleteSpecificIndexFromArray() {
        Bit[] array = new Array();
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(1);
        array.add(0);

        // delete from middle
        array.delete(2);
        assert array.size == 4;
        assert array[0] == 1;
        assert array[1] == 0;
        assert array[2] == 1;
        assert array[3] == 0;

        // delete from index zero
        array.delete(0);
        assert array.size == 3;
        assert array[0] == 0;
        assert array[1] == 1;
        assert array[2] == 0;

        // delete from last index
        array.delete(2);
        assert array.size == 2;
        assert array[0] == 0;
        assert array[1] == 1;
    }

    void shouldInsertValueIntoArray() {
        Bit[] array = new Array();
        array.add(1);
        array.add(0);
        array.add(1);
        array.add(0);

        // insert in middle
        array.insert(2, 1);
        assert array.size == 5;
        assert array[0] == 1;
        assert array[1] == 0;
        assert array[2] == 1;
        assert array[3] == 1;
        assert array[4] == 0;

        // insert at index zero
        array.insert(0, 0);
        assert array.size == 6;
        assert array[0] == 0;
        assert array[1] == 1;
        assert array[2] == 0;
        assert array[3] == 1;
        assert array[4] == 1;
        assert array[5] == 0;

        // insert at the end
        array.insert(array.size, 1);
        assert array.size == 7;
        assert array[0] == 0;
        assert array[1] == 1;
        assert array[2] == 0;
        assert array[3] == 1;
        assert array[4] == 1;
        assert array[5] == 0;
        assert array[6] == 1;
    }
}
