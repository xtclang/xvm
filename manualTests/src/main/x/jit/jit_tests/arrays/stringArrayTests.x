
package stringArrayTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running String Array Tests >>>>");

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

        console.print(">>>> Running String Array Tests >>>>");
    }

    void shouldCreateWithCapacity() {
        String[] array = new Array(10);
        assert array.capacity >= 10;
    }

    void shouldCreateArrayInitializedWithZeroValue() {
        String[] array = new String[4]("");
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == "" && array[1] == "" && array[2] == "" && array[3] == "";
    }

    void shouldCreateArrayInitializedWithValue() {
        String[] array = new String[4]("abc");
        assert array.mutability == Fixed;
        assert array.size == 4
                && array[0] == "abc" && array[1] == "abc" && array[2] == "abc" && array[3] == "abc";
    }

    void shouldCreateConstantArray() {
        String[] array = ["apple", "banana", "cherry"];
        assert array.size == 3;
        assert array[0] == "apple" && array[1] == "banana" && array[2] == "cherry";
    }

    void shouldBeEmpty() {
        String[] array = new Array();
        assert array.size == 0;
        assert array.empty;
    }

    void shouldAddElement() {
        String[] array = new Array();
        array.add("hello");
        assert array.size == 1;
        assert array[0] == "hello";
    }

    void shouldAddElementUsingOperator() {
        String[] array = new Array();
        array += "world";
        assert array.size == 1;
        assert array[0] == "world";
    }

    void shouldAddMultipleElement() {
        String[] array = new Array();
        array.add("one");
        array.add("two");
        array.add("three");
        array.add("four");
        array.add("five");
        array.add("six");
        array.add("seven");
        array.add("eight");
        array.add("nine");
        array.add("ten");
        assert array.size == 10;
        assert array[0] == "one"  && array[1] == "two" && array[2] == "three" && array[3] == "four";
        assert array[4] == "five" && array[5] == "six" && array[6] == "seven" && array[7] == "eight";
        assert array[8] == "nine" && array[9] == "ten";
    }

    void shouldIterateUsingForLoop() {
        String[] array = new Array();
        array.add("a");
        array.add("b");
        array.add("c");
        array.add("d");
        array.add("e");

        Int i = 0;
        for (String s : array) {
            assert s == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldIterateUsingIterator() {
        String[] array = new Array();
        array.add("x");
        array.add("y");
        array.add("z");

        Int i = 0;
        for (String s : array.iterator()) {
            assert s == array[i];
            i++;
        }
        assert i == array.size;
    }

    void shouldDeleteSpecificIndexFromArray() {
        String[] array = new Array();
        array.add("A");
        array.add("B");
        array.add("C");
        array.add("D");
        array.add("E");

        // delete from middle
        array.delete(2);
        assert array.size == 4;
        assert array[0] == "A" && array[1] == "B" && array[2] == "D" && array[3] == "E";

        // delete from index zero
        array.delete(0);
        assert array.size == 3;
        assert array[0] == "B" && array[1] == "D" && array[2] == "E";

        // delete from last index
        array.delete(2);
        assert array.size == 2;
        assert array[0] == "B" && array[1] == "D";
    }

    void shouldInsertValueIntoArray() {
        String[] array = new Array();
        array.add("first");
        array.add("second");
        array.add("third");
        array.add("fourth");

        // insert in middle
        array.insert(2, "middle");
        assert array.size == 5;
        assert array[0] == "first" && array[1] == "second" && array[2] == "middle" && array[3] == "third";
        assert array[4] == "fourth";

        // insert at index zero
        array.insert(0, "start");
        assert array.size == 6;
        assert array[0] == "start" && array[1] == "first" && array[2] == "second" && array[3] == "middle";
        assert array[4] == "third" && array[5] == "fourth";

        // insert at the end
        array.insert(array.size, "end");
        assert array.size == 7;
        assert array[0] == "start" && array[1] == "first" && array[2] == "second" && array[3] == "middle";
        assert array[4] == "third" && array[5] == "fourth" && array[6] == "end";
    }
}
