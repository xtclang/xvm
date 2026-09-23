
package propertyInitTests {

    typedef String|Int as StringOrInt;

    @Inject static Console console;

    void run() {
        testSimple();
        testConstructor();
        testMethodProperty();
        testMethodStaticProperties();
        testMethodResultProperty();
        testDefaultProperty();
        testNullablePropertyTarget();
        testNullablePrimitiveProperty();
        testNarrowUnsignedPropertyEquality();
        testStaticServiceProperty();
        testSingletonService();
        testSingletonConstWithService();
        testPipOps();
    }

    void testSimple() {
        Test t = new Test();

        assert t.i    == 100;
        assert t.ni1  == Null;
        assert t.ni2  == 200;
        assert t.s    == "hello";
        assert t.ns1  == "world";
        assert t.ns2  == Null;
        assert t.si1  == "Foo";
        assert t.si2  == 300;
        assert t.nsi1 == "Bar";
        assert t.nsi2 == 301;
        assert t.nsi3 == Null;
        assert t.x    == 400;
        assert t.nx1  == 500;
        assert t.nx2  == Null;
    }

    void testConstructor() {
        ConstructorTest withNulls =
                new ConstructorTest(600, Null, 700, Null, new Derived("hello"));
        ConstructorTest withValues =
                new ConstructorTest(601, 602, 703, 704, new Derived("hello"));

    }

    void testMethodProperty() {
        MethodPropertyTest counter = new MethodPropertyTest();
        assert counter.first();
        assert !counter.first();

        class MethodPropertyTest {
            Boolean first() {
                private Boolean called = False;
                if (!called) {
                    called = True;
                    return True;
                }
                return False;
            }
        }
    }

    void testMethodStaticProperties() {
        // method-local static properties share the containing JVM class and require unique fields
        interface Values {
            Int first() {
                static Int value = 1;
                return value;
            }

            Int second() {
                static Int value = 2;
                return value;
            }
        }

        class Test implements Values {}

        Values values = new Test();
        assert values.first()  == 1;
        assert values.second() == 2;
    }

    void testMethodResultProperty() {
        class Test {
            String value = "before";

            void update() {
                value = compute();
            }

            private String compute() = "after";
        }

        Test test = new Test();
        test.update();
        assert test.value == "after";
    }

    void testDefaultProperty() {
        class Test {
            String?  string;
            Boolean  bool;
            Int      int;
            Int128   int128;
            Duration duration;
        }

        Test t = new Test();

        assert t.string == Null;
        assert t.bool == False;
        assert t.int == 0;
        assert t.int128 == 0;
        assert t.duration == None;
    }

    void testNullablePropertyTarget() {
        class Test(String value) {}

        String? read(Test? test) = test?.value : Null;

        assert read(new Test("set")) == "set";
        assert read(Null) == Null;
    }

    /**
     * A property whose type is a nullable Java primitive is stored as the narrow primitive plus a
     * separate "is Null" flag, so reading it leaves two values on the Java stack. Materializing it
     * where a reference is expected, as a string template does, has to consume both and turn them
     * into either a boxed value or Null; getting that wrong produced unverifiable bytecode.
     */
    void testNullablePrimitiveProperty() {
        class Test {
            Byte?   b    = 0xFD;
            Int8?   i8   = -5;
            UInt16? u16  = 60000;
            Bit?    bit  = 1;
            Int64?  i64  = 1234567890123;
            Byte?   none = Null;
        }

        Test t = new Test();

        String sb   = $"{t.b}";
        String si8  = $"{t.i8}";
        String su16 = $"{t.u16}";
        String sbit = $"{t.bit}";
        String si64 = $"{t.i64}";
        String snil = $"{t.none}";

        assert sb   == "253";
        assert si8  == "-5";
        assert su16 == "60000";
        assert sbit == "1";
        assert si64 == "1234567890123";
        assert snil == "Null";
    }

    /**
     * Byte/UInt8 lives in a signed byte field and UInt16 in a signed short, but their register form
     * is unsigned, so a field load sign-extends them. Without masking that back off, a value whose
     * high bit is set compares unequal to its own literal. Printing such a property hid this,
     * because boxing masks on the way through; comparing does not box.
     */
    void testNarrowUnsignedPropertyEquality() {
        class Test {
            Byte    b    = 0xFD;
            UInt16  u16  = 60000;
            Int8    i8   = -3;
            Byte    low  = 5;
            Nibble  nib  = 0xF;
            Byte?   nb   = 0xFD;
            UInt16? nu16 = 60000;
        }

        Test t = new Test();

        assert t.b   == 0xFD;
        assert t.u16 == 60000;
        assert t.nb  == 0xFD;
        assert t.nu16 == 60000;

        // the cases that were already correct, so the masking must not disturb them
        assert t.i8  == -3;
        assert t.low == 5;
        assert t.nib == 0xF;
    }

    void testStaticServiceProperty() {
        assert Test.counter.next() == 1;
        assert Test.counter.next() == 2;

        class Test {
            static Counter counter = new CounterService();
        }
    }

    interface Counter {
        Int next();
    }

    service CounterService
            implements Counter {
        Int count;

        @Override
        Int next() = ++count;
    }

    void testSingletonService() {
        // exercise the container-scoped $INSTANCE path and verify that the service retains state
        assert SingletonService.next() == 1;
        assert SingletonService.next() == 2;
    }

    void testSingletonConstWithService() {
        // a singleton const that stores a service must use the same container-scoped path and
        // retain the mutable state of that service
        assert SingletonConst.counter.next() == 1;
        assert SingletonConst.counter.next() == 2;
    }

    void testPipOps() {
        class PipTarget {
            Int value;
        }

        PipTarget target = new PipTarget();

        assert ++target.value == 1;
        assert target.value == 1;
        assert target.value++ == 1;
        assert target.value == 2;
        assert --target.value == 1;
        assert target.value == 1;
        assert target.value-- == 1;
        assert target.value == 0;
        ++target.value;
        assert target.value == 1;
        --target.value;
        assert target.value == 0;
        target.value += 5;
        assert target.value == 5;
        target.value -= 2;
        assert target.value == 3;
        target.value *= 10;
        assert target.value == 30;
        target.value /= 3;
        assert target.value == 10;
        target.value %= 6;
        assert target.value == 4;
        target.value <<= 2;
        assert target.value == 16;
        target.value >>= 1;
        assert target.value == 8;
        target.value >>>= 1;
        assert target.value == 4;
        target.value &= 6;
        assert target.value == 4;
        target.value |= 1;
        assert target.value == 5;
        target.value ^= 7;
        assert target.value == 2;
    }

    static service SingletonService
            implements Counter {
        private Int count = 0;

        @Override
        Int next() = ++count;
    }

    static const SingletonConst {
        Counter counter;

        construct() {
            counter = new CounterService();
        }
    }

    class Test() {
        Int          i    = 100;
        Int?         ni1  = Null;
        Int?         ni2  = 200;
        String       s    = "hello";
        String?      ns1  = "world";
        String?      ns2  = Null;
        StringOrInt  si1  = "Foo";
        StringOrInt  si2  = 300;
        StringOrInt? nsi1 = "Bar";
        StringOrInt? nsi2 = 301;
        StringOrInt? nsi3 = Null;
        Int128       x    = 400;
        Int128?      nx1  = 500;
        Int128?      nx2  = Null;
    }

    class ConstructorTest(Int i, Int? ni, Int128 x, Int128? nx, Base base) {
        construct(Int i, Int? ni, Int128 x, Int128? nx, Base base) {
            this.i    = i;
            this.ni   = ni;
            this.x    = x;
            this.nx   = nx;
            this.base = base;

            assert this.i  == i;
            assert this.ni == ni;
            assert this.x  == x;
            assert this.nx == nx;

            assert base.value == "getter";
            base.value = "hello";
            assert base.setterCalled;
        }

        assert() {
            assert i >= 0;
            assert x >= 0;
            if (Int value ?= ni) {
                assert value >= 0;
            }
            if (Int128 value ?= nx) {
                assert value >= 0;
            }
        }
    }

    class Base(String value) {
        Boolean setterCalled;
    }

    class Derived(String value)
            extends Base(value) {
        @Override
        String value {
            @Override
            String get() = "getter";

            @Override
            void set(String value) {
                setterCalled = True;
            }
        }
    }
}
