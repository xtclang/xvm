/**
 * Very basic array tests.
 */
class Basic {

    void run() {
        emptyLiteral();
        emptyFixed();
        emptyMutable();
        literalInts();
        defaultFixed();
        fixedInts();
        fixedStrings();
        fixedChars();
        mutEmpty();
        mutFix();
        mutEmptyMutable();
        mutImteralInts();
        mutDefaultFixed();
        mutFixedInts();
        mutFixedStrings();
        mutFixedChars();
        addStrings();
        clonedMutableChars();
        clonedConstantBytes();
        elementAccess();
        deleteMutable();
        deleteConstant();
        slice();
        mutClonedMutableChars();
        mutClonedConstantBytes();
        mutDeleteMutable();
        mutDeleteConstant();
        deleteUnordered();
    }

    // -----------------------------
    // Create; set; get; size
    @Test
    void emptyLiteral() {
        Int[] array = [];
        assert array.size == 0;
        assert array.empty;
    }

    @Test
    void emptyFixed() {
        Int[] array = new Int[0];
        assert array.size == 0;
        assert array.empty;
    }

    @Test
    void emptyMutable() {
        String[] array = new String[];
        assert array.size == 0;
        assert array.empty;
    }

    @Test
    void literalInts() {
        Int[] array = [0, 1];
        assert array.size == 2;
        assert !array.empty;
        assert array[0] + array[1] == 1;
    }

    @Test
    void defaultFixed() {
        Int[] array = new Int[2];
        assert array.size == 2;
        assert !array.empty;
        assert array[0] == array[1] == 0;
    }

    @Test
    void fixedInts() {
        Int[] array = new Int[2](i -> i+1);
        assert array.size == 2;
        assert array[0] == 1;
        assert array[1] == 2;
    }

    @Test
    void fixedStrings() {
        String[] array = new String[3](i -> ["one","two","three"][i]);
        assert array.size == 3;
        assert array[2] == "three";
    }

    @Test
    void fixedChars() {
        Char[] array = new Char[3](i -> 'a' + i.toUInt32());
        assert array.size == 3;
        assert array[2] == 'c';
    }

    // -----------------------------
    @Test
    void mutEmpty() {
        Int[] array = [];
        assert array.mutability == Constant;
    }
    @Test
    void mutFix() {
        Int[] array = [0];
        assert array.mutability == Constant;
    }
    @Test
    void mutEmptyMutable() {
        String[] array = new String[];
        assert array.mutability == Mutable;
    }

    @Test
    void mutImteralInts() {
        Int[] array = [0, 1];
        assert array.mutability == Constant;
    }

    @Test
    void mutDefaultFixed() {
        Int[] array = new Int[2];
        assert array.mutability == Fixed;
    }

    @Test
    void mutFixedInts() {
        Int[] array = new Int[2](i -> i+1);
        assert array.mutability == Fixed;
    }

    @Test
    void mutFixedStrings() {
        String[] array = new String[3](i -> ["one","two","three"][i]);
        assert array.mutability == Fixed;
    }

    @Test
    void mutFixedChars() {
        Char[] array = new Char[3](i -> 'a' + i.toUInt32());
        assert array.mutability == Fixed;
    }


    // -----------------------------
    // Plus add, delete, create allowing mutation
    @Test
    void addStrings() {
        String[] array = new String[];
        array.add("a");
        array.add("b");
        assert array.size == 2;
        assert array[0] =="a" && array[1] == "b";
    }

    @Test
    void clonedMutableChars() {
        Char[] array = new Char[];
        array.add('a');
        array.add('b');
        array = new Array(array);
        assert !array.empty;
        assert array[1] > array[0];
    }

    @Test
    void clonedConstantBytes() {
        Byte[] array = new Byte[];
        array.add(0);
        array.add(1);
        array = new Array(Constant, array);
        assert array.size == 2;
        assert array[1] > array[0];
    }

    @Test
    void elementAccess() {
        Boolean[] array = new Array<Boolean>(Mutable, [False, True]);
        assert array.getElement(1);
        array.setElement(1, False);
        assert array[0] == array[1];
    }

    @Test
    void deleteMutable() {
        Int[] array = new Array<Int>(Mutable, [1, 2, 3]);
        array.delete(1);
        assert array.size == 2;
        assert array[1] == 3;
    }

    @Test
    void deleteConstant() {
        Int[] array = [1, 2, 3];
        array = array.delete(1);
        assert array.size == 2;
        assert array[1] == 3;
    }

    // -----------------------------
    // Slicing
    @Test
    void slice() {
        String[] array = new Array<String>(Mutable, ["one", "two", "three"]);
        array += "four";
        String[] slice = array[1 ..< 4];
        assert slice[2] == "four";
    }


    // -----------------------------

    @Test
    void mutClonedMutableChars() {
        Char[] array = new Char[];
        array.add('a');
        array = new Array(array);
        assert array.mutability == Mutable;
    }

    @Test
    void mutClonedConstantBytes() {
        Byte[] array = new Byte[];
        array.add(0);
        array.add(1);
        array = new Array(Constant, array);
        assert array.mutability == Constant;
    }

    @Test
    void mutDeleteMutable() {
        Int[] array = new Array<Int>(Mutable, [1, 2, 3]);
        array.delete(1);
        assert array.mutability == Mutable;
    }

    @Test
    void mutDeleteConstant() {
        Int[] array = [1, 2, 3];
        array = array.delete(1);
        assert array.mutability == Constant;
    }

    // -----------------------------
    // Remove, delete

    @Test
    void deleteUnordered() {
        Int[] array = new Int[].addAll([7, 2, 5, 21, 13, 42]);
        array.removeUnordered(5);
        array.deleteUnordered(3);
        array.removeUnordered(13);
        array.deleteUnordered(array.size-1);
        array.removeUnordered(7);
        array.deleteUnordered(0);
        assert array.empty;
    }
}
