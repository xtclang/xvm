
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
}
