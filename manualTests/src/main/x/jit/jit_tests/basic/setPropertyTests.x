package setPropertyTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running SetPropertyTests >>>>");

        Test t = new Test(0, 0.0, "");

        t.testSpecificToPrimitive(Int:1);
        assert t.i == 1;

        t.testSpecificToWidened("Foo");
        assert t.si == "Foo";

        t.testSpecificToNullablePrimitive();
        assert t.ni == Null;

        t.testSpecificToNullablePrimitive(Int:1);
        assert t.ni == 1;

        t.testSpecificToXvmPrimitive(Dec64:10.5);
        assert t.d == 10.5;

        t.testSpecificToNullableXvmPrimitive();
        assert t.nd == Null;

        t.testSpecificToNullableXvmPrimitive(Dec64:19.5);
        assert t.nd == 19.5;

        t.testPrimitiveToSpecific(1);
        assert t.si == 1;

        t.testPrimitiveToNullablePrimitive(1);
        assert t.ni == 1;

        t.testNullablePrimitiveToSpecific(1);
        assert t.nsi == 1;

        t.testNullablePrimitiveToSpecific(Null);
        assert t.nsi == Null;

        t.testXvmPrimitiveToSpecific(10.2);
        assert t.sd == 10.2;

        t.testNullableXvmPrimitiveToSpecific(1.0);
        assert t.nsd == 1.0;

        t.testNullableXvmPrimitiveToSpecific(Null);
        assert t.nsd == Null;

        t.i = 0;
        t.testBinaryOpResult(19);
        assert t.i == 20;

        t.i = 0;
        t.testUnaryOpResult(19);
        Int expected = ~19;
        assert t.i == expected;

        t.i = 10;
        t.testInPlaceOpResult(19);
        assert t.i == 29;

        console.print("<<<< Finished SetPropertyTests <<<<<");
    }

    class Test(Int i, Dec64 d, String s) {
        Int?         ni;
        String?      ns;
        StringOrInt  si = "0";
        StringOrInt? nsi = "0";
        Dec?         nd;
        StringOrDec  sd = "0";
        StringOrDec? nsd = "0";
        Int          i = 0;

        Int size.get() = 0;

        typedef String|Int as StringOrInt;
        typedef String|Dec as StringOrDec;

        void testSpecificToPrimitive(Object i) {
            assert this.i := i.is(Int);
        }

        void testSpecificToWidened(String s) {
            this.si = s;
        }

        void testSpecificToNullablePrimitive() {
            this.ni = Null;
        }

        void testSpecificToNullablePrimitive(Object i) {
            assert this.ni := i.is(Int);
        }

        void testSpecificToNullableXvmPrimitive() {
            this.nd = Null;
        }

        void testSpecificToXvmPrimitive(Object d) {
            assert this.d := d.is(Dec64);
        }

        void testSpecificToNullableXvmPrimitive(Object i) {
            assert this.nd := i.is(Dec64);
        }

        void testPrimitiveToSpecific(Int i) {
            this.si = i;
        }

        void testPrimitiveToNullablePrimitive(Int i) {
            this.ni = i;
        }

        void testXvmPrimitiveToSpecific(Dec d) {
            this.sd = d;
        }

        void testNullablePrimitiveToSpecific(Int? i) {
            this.nsi = i;
        }

        void testNullableXvmPrimitiveToSpecific(Dec? d) {
            this.nsd = d;
        }

        void testXvmPrimitiveToNullableXvmPrimitive(Dec d) {
            this.nd = d;
        }

        void testBinaryOpResult(Int n) {
            this.i = n + 1;
        }

        void testUnaryOpResult(Int n) {
            this.i = ~n;
        }

        void testInPlaceOpResult(Int n) {
            this.i += n;
        }
    }
}
