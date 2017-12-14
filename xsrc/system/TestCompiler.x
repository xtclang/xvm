class TestCompiler
    {
    interface MyMap<KM, VM>
        {
        VM get(KM key);
        Void put(KM key, VM value);
        Boolean containsValue(VM value);
        }

    interface Consumer<VC>
        {
        Boolean containsValue(VC value);
        }

    class MyClass1<K1, VC>
        implements MyMap<K1, VC> {}

    class MyClass2<K2, V2>
            implements Consumer<V2>
        {
        Boolean containsValue(V2 value) {return false;}
        }

    class MyConsumer<VMC>
            implements Consumer<VMC>
        {
        Boolean containsValue(VMC value) {return true;}
        }

    mixin MyConsumer2<VMC>
            implements Consumer<VMC>
        {
        Boolean containsValue(VMC value) {return true;}
        }

    class MyClass3<K3, V3>
        extends MyConsumer<V3> {}

    class MyClass4<K4>
            implements Consumer<Number>
        {
        Boolean containsValue(Int value) {return false;}
        }

    class MyClass5<K5>
        extends MyConsumer<Int> {}

    class MyClass6<K6, V6>
        extends MyClass3<K6, V6> {}

    class MyClass7
        extends MyClass3<String, String> {}

    class MyClass8<K8, V8>
        incorporates MyConsumer2<V8> {}

    class MyClass9<K9, V9>
        incorporates conditional MyConsumer2<V9 extends Number> {}

    class MyClass10<V10>
        extends MyClass9<String, V10> {}


    static Void test1()
        {
        MyClass1<String, Number> c1;
        Consumer<Int> finder1 = c1; // OK; duck-typing

        MyClass2<String, Number> c2;
        Consumer<Int> finder2 = c2; // OK; "Implements"

        MyClass3<String, Number> c3;
        Consumer<Int> finder3 = c3; // OK; "Extends-Implements"

        MyClass4<String> c4;
        Consumer<Int> finder4 = c4; // OK; "Implements"

        MyClass5<String> c5;
        Consumer<Int> finder5 = c5; // OK; "Extends"

        MyClass6<String, Int> c6;
        Consumer<Int> finder6 = c6; // OK; "Extends-Extends-Implements"
        MyConsumer<Int> finder6a = c6; // OK; "Extends-Extend"

        MyClass7 c7;
        Consumer<String> finder7 = c7; // OK; "Extends-Extends-Implements"

        MyClass8<String, Number> c8;
        Consumer<Int> finder8 = c8; // OK; "Incorporates-Implements"
        Consumer<Int> finder8a = c8; // OK; "Incorporates-Extends"

        MyClass10<Number> c10;
        Consumer<Number> finder10 = c10; // OK; "Extends-Incorporates-Implements"
        }

    static Void test1ExpectedFailure1(MyClass7 c7ExpectedFailure)
        {
        Consumer<Int> finder7a = c7ExpectedFailure;
        }

    static Void test1ExpectedFailure2(MyClass9<String, String> c9)
        {
        MyConsumer<Int> finder9 = c9; // fail; "Incorporates"
        }

    static Void test1ExpectedFailure3(MyClass9<String, String> c9)
        {
        Consumer<Int> finder9a = c9; // fail; "Incorporates-Extends"
        }

    static Void test1ExpectedFailure4(MyClass10<String> c10a)
        {
        MyConsumer<Int> finder10a = c10a; // fail; "Extends-Incorporates"
        }

//    static Void test1ExpectedFailure5(MyClass10<Int> c10)
//        {
//        immutable MyConsumer<Int> finder10b = c10; // fail; "Extends-Incorporates"
//        }

    class P<T>
        {
        T produce()
            {
            T t;
            return t;
            }
        }

    class P2<T2>
        {
        P<T2> produce()
            {
            P<T2> p;
            return p;
            }
        }

    interface C<T>
        {
        Void consume(T value);
        }

    interface C2<T2>
        {
        C<T2> consume();
        }

    class PC<T> extends P<T> implements C<T>
        {
        }

    class FakePCofObject
        {
        Object produce() {return "";}
        Void consume(Object value) {}
        }

    class FakePCofString
        {
        String produce() {return "";}
        Void consume(String value) {}
        }

    static Void testPC()
        {
        C<Object>  y1;
        C<String>  x1 = y1;

        C2<Object> y12;
        C2<String> x12 = y12;

        PC<Object> y2;
        C<String>  x2 = y2;

        P<String>  y5;
        P<Object>  x5 = y5;

        P2<String> y52;
        P2<Object> x52 = y52;

        PC<String> y6;
        PC<Object> x6 = y6; // ok, but the RT needs to "safe-wrap" the consuming methods
        }

    static Void testPCExpectedFailure1(C<String> y3)
        {
        C<Object> x3 = y3;
        }

    static Void testPCExpectedFailure2(PC<String> y4)
        {
        C<Object> x4 = y4;
        }

    static Void testPCExpectedFailure3(P<Object> y5)
        {
        P<String> x5 = y5;
        }

    static Void testPCExpectedFailure4(PC<String> y7)
        {
        FakePCofObject x7 = y7;
        }

    static Void testPCExpectedFailure5(PC<Object> y8)
        {
        PC<String> x8 = y8;
        }

    static Int test2()
        {
        Int i = 0;
        return i;
        }
    }
