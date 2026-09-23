package assignTests {

    void run() {
        testNullablePrimitiveAssignment();
    }

    void testNullablePrimitiveAssignment() {
        // a constructor returns a non-null value, but the destination also stores a null flag
        Int? value = new Int("42");
        assert value == 42;
        assert !isNull(value);

        value = Null;
        assert isNull(value);
        value = new Int("7");
        assert value == 7;
        assert !isNull(value);

        // literal and method-result assignments must also clear the null flag
        value = Null;
        assert isNull(value);
        value = 42;
        assert value == 42;
        assert !isNull(value);

        value = Null;
        assert isNull(value);
        value = identity(7);
        assert value == 7;
        assert !isNull(value);

        // cover both a single-slot primitive and a multi-slot primitive
        Int32? small = new Int32(#0000002A);
        assert small == 42;
        Int128? large = new Int128(#0000000000000000000000000000002A);
        assert large == 42;

        Boolean isNull(Int? number) = number == Null;
        Int identity(Int number) = number;
    }
}
