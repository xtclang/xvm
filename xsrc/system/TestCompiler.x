
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

    static Void test()
        {
//        MyClass1<String, Int> c1;
//        Finder<Number> finder1 = c1; // OK; duck-typing

        MyClass2<String, Int> c2;
        Finder<Number> finder2 = c2; // OK; contribution by "Implements"

//        MyClass3<String, Int> c3;
//        Finder<Number> finder3 = c3; // OK; contribution by "Extends"
        }
    }
