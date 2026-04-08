
package nibbleArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Nibble Array Tests >>>>");

        shouldCreateWithCapacity();
        shouldCreateConstantArray();
        shouldBeEmpty();
        shouldAddElement();
        shouldAddElementUsingOperator();
        shouldAddMultipleElement();

        console.print(">>>> Running Nibble Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Nibble[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateConstantArray() {
        Nibble[] array = [0x0A, 0x05, 0x0F];
        assert array.size == 3;
        assert array[0] == 0x0A;
        assert array[1] == 0x05;
        assert array[2] == 0x0F;
    }

    void shouldBeEmpty() {
        Nibble[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        Nibble[] array = new Array();
        array.add(0x0A);
        assert array.size == 1;
        assert array[0] == 0x0A;
    }

    void shouldAddElementUsingOperator() {
        Nibble[] array = new Array();
        array += 0x0A;
        assert array.size == 1;
        assert array[0] == 0x0A;
    }

    void shouldAddMultipleElement() {
        Nibble[] array = new Array();
        array.add(0x01);
        array.add(0x02);
        array.add(0x03);
        array.add(0x04);
        array.add(0x05);
        array.add(0x06);
        array.add(0x07);
        array.add(0x08);
        array.add(0x09);
        array.add(0x0A);
        assert array.size == 10;
        assert array[0] == 0x01;
        assert array[1] == 0x02;
        assert array[2] == 0x03;
        assert array[3] == 0x04;
        assert array[4] == 0x05;
        assert array[5] == 0x06;
        assert array[6] == 0x07;
        assert array[7] == 0x08;
        assert array[8] == 0x09;
        assert array[9] == 0x0A;
    }
}
