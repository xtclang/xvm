package primitiveRangeTests {

    @Inject Console console;

    void run() {
        testBooleanRange();
        testInt16Range();
        testUInt32Range();
        testFloat32Range();
        testFloat64Range();
        testInt128Range();
    }

    void testBooleanRange() {
        Boolean first = False;
        Boolean last  = True;
        Range<Boolean> range = first..last;

        assert range.first == False;
        assert range.last  == True;
    }

    void testInt16Range() {
        Int16 first = 10;
        Int16 last  = 20;
        Range<Int16> range = first>..<last;

        assert range.lowerBound == 10;
        assert range.upperBound == 20;
        assert range.lowerExclusive && range.upperExclusive;
    }

    void testUInt32Range() {
        UInt32 first = 100;
        UInt32 last  = 200;
        Range<UInt32> range = first..<last;

        assert range.lowerBound == 100;
        assert range.upperBound == 200;
        assert !range.lowerExclusive && range.upperExclusive;
    }

    void testFloat32Range() {
        Float32 first = 1.25;
        Float32 last  = 2.5;
        Range<Float32> range = first..last;

        assert range.first == 1.25;
        assert range.last  == 2.5;
    }

    void testFloat64Range() {
        Float64 first = 3.5;
        Float64 last  = 4.75;
        Range<Float64> range = first..last;

        assert range.first == 3.5;
        assert range.last  == 4.75;
    }

    void testInt128Range() {
        Int128 first = -18446744073709551616;
        Int128 last  =  18446744073709551616;
        Range<Int128> range = first..last;

        assert range.first == first;
        assert range.last  == last;
    }
}
