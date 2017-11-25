
class TestCompiler
    {
    interface MyMap<KM, VM>
        {
        VM get(KM key);
        Void put(KM key, VM value);
        Boolean containsValue(VM value);
        }

    interface Finder<VF>
        {
        Boolean containsValue(VF value);
        }

    class MyClass1<K1, V1>
        implements MyMap<K1, V1> {}

    class MyClass2<K2, V2>
            implements Finder<V2>
        {
        Boolean containsValue(V2 value) {return false;}
        }

    class MyFinder<VMF>
            implements Finder<VMF>
        {
        Boolean containsValue(VMF value) {return true;}
        }

    class MyClass3<K3, V3>
        extends MyFinder<V3> {}

    class MyClass4<K4>
            implements Finder<Int>
        {
        Boolean containsValue(Int value) {return false;}
        }

    class MyClass5<K5>
        extends MyFinder<Int> {}

    class MyClass6<K6, V6>
        extends MyClass3<K6, V6> {}

    class MyClass7
        extends MyClass3<String, String> {}

    class MyClass8<K8, V8>
            incorporates MyFinder<V8> {}

    class MyClass9<K9, V9>
            incorporates conditional MyFinder<V9 extends Number> {}

    class MyClass10<V10>
            extends MyClass9<String, V10> {}


    static Void test()
        {
//        MyClass1<String, Int> c1;
//        Finder<Number> finder1 = c1; // OK; duck-typing

        MyClass2<String, Int> c2;
        Finder<Number> finder2 = c2; // OK; "Implements"

        MyClass3<String, Int> c3;
        Finder<Number> finder3 = c3; // OK; "Extends-Implements"

        MyClass4<String> c4;
        Finder<Number> finder4 = c4; // OK; "Implements"

        MyClass5<String> c5;
        Finder<Number> finder5 = c5; // OK; "Extends"

        MyClass6<String, Int> c6;
        Finder<Number> finder6 = c6; // OK; "Extends-Extends-Implements"
        MyFinder<Number> finder6a = c6; // OK; "Extends-Extend"

        MyClass7 c7;
        Finder<String> finder7 = c7; // OK; "Extends-Extends-Implements"
//        Finder<Int> finder7a = c7; // fail; "Extends-Extends-Implements"

        MyClass8<String, Int> c8;
        MyFinder<Number> finder8 = c8; // OK; "Incorporates"
        Finder<Number> finder8a = c8; // OK; "Incorporates-Extends"

//        MyClass9<String, String> c9;
//        MyFinder<Number> finder9 = c9; // fail; "Incorporates"
//        Finder<Number> finder9a = c9; // fail; "Incorporates-Extends"

        MyClass10<Int> c10;
        MyFinder<Number> finder10 = c10; // OK; "Extends-Incorporates"

//        MyClass10<String> c10a;
//        MyFinder<Number> finder10a = c10a; // fail; "Extends-Incorporates"
        }
    }
