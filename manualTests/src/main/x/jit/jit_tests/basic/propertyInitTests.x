
package propertyInitTests {

    typedef String|Int as StringOrInt;

    @Inject static Console console;

    void run() {
        testSimple();
        testConstructor();
        testMethodProperty();
        testMethodResultProperty();
        testDefaultProperty();
        testNullablePropertyTarget();
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
