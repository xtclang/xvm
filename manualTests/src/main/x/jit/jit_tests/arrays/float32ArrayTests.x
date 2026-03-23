
package float32ArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Float32 Array Tests >>>>");

        shouldCreateWithCapacity();
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

        console.print(">>>> Running Float32 Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        Float32[] array = new Array(10);
        assert array.capacity == 10;
    }

    void shouldCreateConstantArray() {
        Float32[] array = [10, 20, 100];
        assert array.size == 3;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == 100;
    }

    void shouldBeEmpty() {
        Float32[] array = new Array();
        assert array.size == 0;
//        assert array.empty;
    }

    void shouldAddElement() {
        Float32[] array = new Array();
        array.add(10);
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddElementUsingOperator() {
        Float32[] array = new Array();
        array += 10;
        assert array.size == 1;
        assert array[0] == 10;
    }

    void shouldAddMultipleElement() {
        Float32[] array = new Array();
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

    void shouldAddInPlace() {
        Float32[] array = [10, 20, 100];
        array[1] += 5;
        assert array[0] == 10;
        assert array[1] == 25;
        assert array[2] == 100;
    }

    void shouldSubInPlace() {
        Float32[] array = [10, 20, 100];
        array[2] -= 5;
        assert array[0] == 10;
        assert array[1] == 20;
        assert array[2] == (100 - 5);
    }

    void shouldMultiplyInPlace() {
        Float32[] array = [10, 20, 30];
        array[1] *= 5;
        assert array[0] == 10;
        assert array[1] == 100;
        assert array[2] == 30;
    }

    void shouldDivideInPlace() {
        Float32[] array = [10, 20, 30];
        array[1] /= 5;
        assert array[0] == 10;
        assert array[1] == 4;
        assert array[2] == 30;
    }

    void shouldModulusInPlace() {
        Float32[] array = [10, 21, 30];
        array[1] %= 5;
        assert array[0] == 10;
        assert array[1] == 21 % 5;
        assert array[2] == 30;
    }
}
