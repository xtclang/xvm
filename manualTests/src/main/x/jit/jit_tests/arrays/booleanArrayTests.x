
package booleanArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Boolean Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateArrayInitializedWithFalse();
        shouldCreateArrayInitializedWithTrue();
        shouldCreateConstantArray();
        shouldBeEmpty();
        shouldAddElement();
        shouldAddElementUsingOperator();
        shouldAddMultipleElement();

        console.print("<<<< Finished Boolean Array Tests <<<<");
    }

    void shouldCreateWithCapacity() {
        Boolean[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateArrayInitializedWithFalse() {
        Boolean[] array = new Boolean[4](False);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == False && array[1] == False && array[2] == False && array[3] == False;
    }

    void shouldCreateArrayInitializedWithTrue() {
        Boolean[] array = new Boolean[4](True);
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == True && array[1] == True && array[2] == True && array[3] == True;
    }

    void shouldCreateConstantArray() {
        Boolean[] array = [False, True, True];
        assert array.size == 3;
        assert array[0] == False;
        assert array[1] == True;
        assert array[2] == True;
    }

    void shouldBeEmpty() {
        Boolean[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        Boolean[] array = new Array();
        array.add(True);
        assert array.size == 1;
        assert array[0] == True;
    }

    void shouldAddElementUsingOperator() {
        Boolean[] array = new Array();
        array += True;
        assert array.size == 1;
        assert array[0] == True;
    }

    void shouldAddMultipleElement() {
        Boolean[] array = new Array();
        array.add(True);
        array.add(False);
        array.add(True);
        array.add(True);
        array.add(False);
        array.add(False);
        array.add(True);
        array.add(False);
        array.add(True);
        array.add(True);
        assert array.size == 10;
        assert array[0] == True;
        assert array[1] == False;
        assert array[2] == True;
        assert array[3] == True;
        assert array[4] == False;
        assert array[5] == False;
        assert array[6] == True;
        assert array[7] == False;
        assert array[8] == True;
        assert array[9] == True;
    }
}
