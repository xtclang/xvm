
package propertyInitTests {

    static TestConsole propertyConsole = new TestConsole();

    void run() {
        testSimple();
        testPropertySuper();
        testBoundPropertySuper();
        testComplexProperty();
        testWatchedComplexProperty();
        testWatchedUnassignedProperty();
        testLazyProperty();
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

    void testPropertySuper() {
        class Test {
            Int value {
                // super accesses this property's field without re-entering the custom accessor
                Int get() = super() + 10;
                void set(Int value) {
                    super(value * 2);
                }
            }
        }

        Test test = new Test();
        assert test.value == 10;
        test.value = 7;
        assert test.value == 24;
    }

    void testBoundPropertySuper() {
        // binding super must retain the backing-field body in both accessor call chains
        class Test {
            Int value {
                Int get() {
                    function Int() read = super;
                    return read() + 10;
                }

                void set(Int value) {
                    function void(Int) write = super;
                    write(value * 2);
                }
            }
        }

        Test test = new Test();
        assert test.value == 10;
        test.value = 7;
        assert test.value == 24;
    }

    void testComplexProperty() {
        class Complex {
            String value {
                private Char hold = '!';

                String get() = appendHold(super());
                void set(String value) = super(removeHead(value));

                private String removeHead(String value) {
                    hold = value[0];
                    return value.substring(1);
                }
                private String appendHold(String value) = value + hold;
            } = "hello";

            Int number {
                Int get() = increment(super());
                void set(Int v) = super(double(v));

                Int increment(Int v) = v + 1;
                Int double(Int v) = v * 2;
            } = 10;
        }

        Complex test = new Complex();
        assert test.value == "hello!";
        test.value = "abc";
        assert test.value == "bca";
        assert test.value == "bca";

        assert test.number == 11;
        test.number = 7;
        assert test.number == 15;
        assert test.number == 15;
    }

    void testWatchedComplexProperty() {
        propertyConsole.reset();
        WatchedComplex test = new WatchedComplex();
        assert propertyConsole.output() == "construct;validate;";
        assert test.value == "ello";

        propertyConsole.reset();
        test.value = "abc";
        assert test.value == "bc";
        assert propertyConsole.output() == "set:abc;watch:abc;";

        Var<String> ref = asVar(test.&value);
        assert ref.assigned;
        assert ref.is(Watch);
        assert ref.get() == "bc";
        assert String value := ref.peek();
        assert value == "bc";
        assert propertyConsole.output() == "set:abc;watch:abc;";

        propertyConsole.reset();
        ref.set("xyz");
        assert test.value == "yz";
        assert ref.get() == "yz";
        assert propertyConsole.output() == "set:xyz;watch:xyz;";

        propertyConsole.reset();
        assert asLifecycle(ref).status() == "ready";
        assert propertyConsole.output() == "status;";

        class OtherValue {
            @Unassigned String value;
        }
        OtherValue other = new OtherValue();
        assert !asLifecycle(ref).sameAssignment(other.&value);
        other.value = "ready";
        assert asLifecycle(ref).sameAssignment(other.&value);

        Var<String> asVar(Object value) = value.as(Var<String>);
        PropertyLifecycle asLifecycle(Object value) = value.as(PropertyLifecycle);
    }

    class WatchedComplex() {
        @PropertyLifecycle(v -> propertyConsole.print($"{v};", True))
        @Watch(v -> propertyConsole.print($"watch:{v};", True))
        String value {
            @Override
            String get() = removeHead(super());
            @Override
            void set(String value) {
                propertyConsole.print($"set:{value};", True);
                super(value);
            }
            private String removeHead(String value) = value.substring(1);
        } = "hello";
    }

    void testWatchedUnassignedProperty() {
        class WatchedUnassigned() {
            @Unassigned
            @Watch(v -> propertyConsole.print($"value:{v};", True))
            String value {
                @Override
                String get() {
                    propertyConsole.print("get;", True);
                    return super();
                }
            }

            @Unassigned
            @Watch(v -> propertyConsole.print($"wide:{v};", True))
            Int128 wide;

            @Unassigned
            @Watch(v -> propertyConsole.print($"nullable:{v};", True))
            Int? nullable;
        }

        propertyConsole.reset();
        WatchedUnassigned test = new WatchedUnassigned();
        Var<String> valueRef = asVar(test.&value);
        assert !valueRef.assigned;
        assert !valueRef.peek();
        assert propertyConsole.output() == "";

        valueRef.set("ready");
        assert valueRef.assigned;
        assert propertyConsole.output() == "value:ready;";
        assert String value := valueRef.peek();
        assert value == "ready";
        assert propertyConsole.output() == "value:ready;get;";

        Ref<Int128> wideRef = test.&wide;
        assert !wideRef.assigned;
        assert !wideRef.peek();
        test.wide = 0;
        assert wideRef.assigned;
        assert Int128 wide := wideRef.peek();
        assert wide == 0;

        Var<Int?> nullableRef = asNullableVar(test.&nullable);
        assert !nullableRef.assigned;
        assert !nullableRef.peek();
        nullableRef.set(Null);
        assert nullableRef.assigned;
        assert Int? nullable := nullableRef.peek();
        assert nullable == Null;
        assert propertyConsole.output() == "value:ready;get;wide:0;nullable:Null;";

        Var<String> asVar(Object value) = value.as(Var<String>);
        Var<Int?> asNullableVar(Object value) = value.as(Var<Int?>);
    }

    @Lazy Int lazyInstance.calc() = 42;
    // TODO: static annotated properties need shared annotation storage and initialization support
//    static @Lazy Int lazyStatic.calc() = 42;

    void testLazyProperty() {
        assert !&lazyInstance.assigned;
        assert lazyInstance == 42;
        assert &lazyInstance.assigned;
//        assert !&lazyStatic.assigned;
//        assert lazyStatic == 42;
//        assert &lazyStatic.assigned;

        const Point(Int x, Int y) {
            @Lazy Int hypo.calc() = x*x + y*y;
            @Lazy(() -> x*x + y*y) Int hypo2 = 2;
            @Lazy(() -> x*x + y*y) Int hypo3;
        }

        Point point = new Point(3, 4);
        assert !point.&hypo.assigned;
        assert point.hypo == 25;
        assert point.&hypo.assigned;
        assert point.hypo2 == 2;
        assert !point.&hypo3.assigned;
        assert point.hypo3 == 25;
        assert point.&hypo3.assigned;

        class LazyProperties() {
            @Lazy String text.calc() {
                propertyConsole.print("text;", True);
                return "ready";
            }

            @Lazy Int number.calc() {
                propertyConsole.print("number;", True);
                return 42;
            }

            @Lazy(() -> {
                propertyConsole.print("lambda;", True);
                return "lambda";
            })
            String supplied;

            @Lazy(() -> {
                propertyConsole.print("nullable;", True);
                return Null;
            })
            Int? nullable;

            @Watch(v -> propertyConsole.print($"watch:{v};", True))
            @Lazy
            Int observed.calc() {
                propertyConsole.print("observed;", True);
                return 7;
            }
        }

        propertyConsole.reset();
        LazyProperties test = new LazyProperties();
        Var<String> textRef = asVar(test.&text);
        assert !textRef.assigned;
        assert !textRef.peek();
        assert propertyConsole.output() == "";
        assert test.text == "ready";
        assert textRef.assigned;
        assert textRef.get() == "ready";
        assert propertyConsole.output() == "text;";

        Ref<Int> numberRef = test.&number;
        assert !numberRef.assigned;
        assert !numberRef.peek();
        assert numberRef.get() == 42;
        assert numberRef.assigned;
        assert test.number == 42;
        assert propertyConsole.output() == "text;number;";

        Var<String> suppliedRef = asVar(test.&supplied);
        assert !suppliedRef.assigned;
        assert suppliedRef.get() == "lambda";
        assert suppliedRef.assigned;
        assert test.supplied == "lambda";
        assert propertyConsole.output() == "text;number;lambda;";

        propertyConsole.reset();
        Ref<Int?> nullableRef = test.&nullable;
        assert !nullableRef.assigned;
        assert !nullableRef.peek();
        assert propertyConsole.output() == "";
        assert nullableRef.get() == Null;
        assert nullableRef.assigned;
        assert test.nullable == Null;
        assert propertyConsole.output() == "nullable;";

        propertyConsole.reset();
        Ref<Int> observedRef = test.&observed;
        assert !observedRef.assigned;
        assert observedRef.get() == 7;
        assert observedRef.assigned;
        assert test.observed == 7;
        assert propertyConsole.output() == "observed;watch:7;";

        Var<String> asVar(Object value) = value.as(Var<String>);
        Var<Int> asIntVar(Object value) = value.as(Var<Int>);
    }

    annotation PropertyLifecycle(function void(String) record) into Var<String> {
        private function void(String) record;
        private Char state = '!';

        construct(function void(String) record) {
            assert state == '!';
            this.record = record;
            record("construct");
        }

        assert() {
            assert state == '!';
            record("validate");
        }

        String status() {
            record("status");
            return "ready";
        }

        Boolean sameAssignment(Ref<String> other) = this.assigned == other.assigned;
    }

    void testConstructor() {
        ConstructorTest withNulls =
                new ConstructorTest(600, Null, 700, Null, new Derived("hello"));
        ConstructorTest withValues =
                new ConstructorTest(601, 602, 703, 704, new Derived("hello"));

        Boolean rejected = False;
        try {
            new ConstructorTest(-1, Null, 700, Null, new Derived("hello"));
        } catch (IllegalState e) {
            rejected = True;
        }
        assert rejected as "validation had to fail";

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
            Byte?     b    = 0xFD;
            Int8?     i8   = -5;
            UInt16?   u16  = 60000;
            Bit?      bit  = 1;
            Int64?    i64  = 1234567890123;
            Int128?   i128 = -12345678901234567890;
            UInt128?  u128 = 12345678901234567890;
            Dec64?    dec  = 12.5;
            Duration? dur  = Duration:PT1H2M3S;
            Byte?     none = Null;
        }

        Test t = new Test();

        String sb    = $"{t.b}";
        String si8   = $"{t.i8}";
        String su16  = $"{t.u16}";
        String sbit  = $"{t.bit}";
        String si64  = $"{t.i64}";
        String si128 = $"{t.i128}";
        String su128 = $"{t.u128}";
        String sdec  = $"{t.dec}";
        String sdur  = $"{t.dur}";
        String snil  = $"{t.none}";

        assert sb    == "253";
        assert si8   == "-5";
        assert su16  == "60000";
        assert sbit  == "1";
        assert si64  == "1234567890123";
        assert si128 == "-12345678901234567890";
        assert su128 == "12345678901234567890";
        assert sdec  == "12.5";
        assert sdur  == "1:02:03"; // Duration's default format is not ISO-8601
        assert snil  == "Null";

        // assert captures each property value in a temporary register for its failure message;
        // the string template then moves that saved value into a Stringable register; unlike the
        // assignments above, this exercises nullable boxing
        assert $"{t.b}"    == "253";
        assert $"{t.i8}"   == "-5";
        assert $"{t.u16}"  == "60000";
        assert $"{t.bit}"  == "1";
        assert $"{t.i64}"  == "1234567890123";
        assert $"{t.i128}" == "-12345678901234567890";
        assert $"{t.u128}" == "12345678901234567890";
        assert $"{t.dec}"  == "12.5";
        assert $"{t.dur}"  == "1:02:03";
        assert $"{t.none}" == "Null";

        // Null primitives must discard all components, not box their placeholder values
        t.b    = Null;
        t.i8   = Null;
        t.u16  = Null;
        t.bit  = Null;
        t.i64  = Null;
        t.i128 = Null;
        t.u128 = Null;
        t.dec  = Null;
        t.dur  = Null;
        assert $"{t.b}"    == "Null";
        assert $"{t.i8}"   == "Null";
        assert $"{t.u16}"  == "Null";
        assert $"{t.bit}"  == "Null";
        assert $"{t.i64}"  == "Null";
        assert $"{t.i128}" == "Null";
        assert $"{t.u128}" == "Null";
        assert $"{t.dec}"  == "Null";
        assert $"{t.dur}"  == "Null";
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
        typedef String|Int as StringOrInt;

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
