import X.Number;

class TestCompiler<TestType1 extends Number,
                   TestType2 extends TestType1,
                   TestType3 extends TestType2>
    {
    interface MyMap<KM, VM>
        {
        VM get(KM key);
        void put(KM key, VM value);
        Boolean containsValue(VM value);
        }

    interface Consumer<VC>
        {
        Boolean containsValue(VC value);
        }

    static class MyClass1<K1, VC>
        implements MyMap<K1, VC> {}

    static class MyClass2<K2, V2>
            implements Consumer<V2>
        {
        Boolean containsValue(V2 value) {return false;}
        }

    static class MyConsumer<VMC>
            implements Consumer<VMC>
        {
        @Override
        Boolean containsValue(VMC value) {return true;}
        }

    static mixin MyConsumer2<VMC>
            implements Consumer<VMC>
        {
        @Override
        Boolean containsValue(VMC value) {return true;}
        }

    static class MyClass3<K3, V3>
        extends MyConsumer<V3> {}

    static class MyClass4<K4>
            implements Consumer<Number>
        {
        @Override
        Boolean containsValue(Int value) {return false;}
        }

    static class MyClass5<K5>
        extends MyConsumer<Int> {}

    static class MyClass6<K6, V6>
        extends MyClass3<K6, V6> {}

    static class MyClass7
        extends MyClass3<String, String> {}

    static class MyClass8<K8, V8>
        incorporates MyConsumer2<V8> {}

    static class MyClass9<K9, V9>
        incorporates conditional MyConsumer2<V9 extends Number> {}

    static class MyClass10<V10>
        extends MyClass9<String, V10> {}

    static void test1(MyClass1<String, Number> c1,
                      MyClass2<String, Number> c2,
                      MyClass3<String, Number> c3,
                      MyClass4<String> c4,
                      MyClass5<String> c5,
                      MyClass6<String, Int> c6,
                      MyClass7 c7,
                      MyClass8<String, Number> c8,
                      MyClass10<Number> c10)
        {
        Consumer<Int> finder1 = c1; // OK; duck-typing

        Consumer<Int> finder2 = c2; // OK; "Implements"

        Consumer<Int> finder3 = c3; // OK; "Extends-Implements"

        Consumer<Int> finder4 = c4; // OK; "Implements"

        Consumer<Int> finder5 = c5; // OK; "Extends"

        Consumer<Int> finder6 = c6; // OK; "Extends-Extends-Implements"
        MyConsumer<Int> finder6a = c6; // OK; "Extends-Extend"

        Consumer<String> finder7 = c7; // OK; "Extends-Extends-Implements"

        Consumer<Int> finder8 = c8; // OK; "Incorporates-Implements"
        Consumer<Int> finder8a = c8; // OK; "Incorporates-Extends"

        Consumer<Number> finder10 = c10; // OK; "Extends-Incorporates-Implements"

        // partial formal types
        MyClass6 mc6 = c6;
        MyClass6<String> mc6s = c6;
        }

    static void test1ExpectedFailure1(MyClass7 cF1)
        {
        Consumer<Int> finder7a = cF1;
        }

    static void test1ExpectedFailure2(MyClass9<String, String> cF2)
        {
        MyConsumer<Int> finder9 = cF2; // fail; "Incorporates"
        }

    static void test1ExpectedFailure3(MyClass9<String, String> cF3)
        {
        Consumer<Int> finder9a = cF3; // fail; "Incorporates-Extends"
        }

    static void test1ExpectedFailure4(MyClass10<String> cF4)
        {
        MyConsumer<Int> finder10a = cF4; // fail; "Extends-Incorporates"
        }

    static void test1ExpectedFailure5(MyClass10<Int> cF5)
        {
        immutable MyConsumer<Int> finder10b = cF5; // fail; "Extends-Incorporates"
        }

    static class P<T>
        {
        private T content;

        T produce()
            {
            T t = content;
            return t;
            }

        // self-referencing methods don't contribute to consumption/production
        P<T> self()
            {
            return this;
            }
        P!<T> self2()
            {
            return this;
            }
        }

    static class P2<T2>
        {
        P<T2> produce()
            {
            P<T2> p = new P<T2>();
            return p;
            }
        }

    interface C<T>
        {
        void consume(T value);

        // self-referencing methods don't contribute to consumption/production
        C<T> self();
        C!<T> self2();
        }

    interface C2<T2>
        {
        C<T2> consume();
        }

    static class CC<T> implements C<T>
        {
        }

    static class PC<T> extends P<T> implements C<T>
        {
        }

    static class FakePCofObject
        {
        Object produce() {return "";}
        void consume(Object value) {}
        }

    static class FakePCofString
        {
        String produce() {return "";}
        void consume(String value) {}
        }

    static void testPC(C          c,
                       C<Object>  co,
                       C2<Object> c2o,
                       PC<Object> pco,
                       PC         pc,
                       P<String>  ps,
                       P2<String> p2s,
                       PC<String> pcs)
        {
        C<String> cs = co;

        C2<String> c2s = c2o;

        C<String> cs1 = pco;
        C c1 = co;
        C c2 = cs;
        C c3 = pc;
        C c4 = pco;
        C c5 = pcs;

        P<Object> po = ps;
        P p = ps;
        P p1 = pc;

        P2<Object> p20 = p2s;

        PC<Object> pco = pcs; // ok, but the RT needs to "safe-wrap" the consuming methods
        }

    static void testPCExpectedFailure1(C<String> csF1)
        {
        C<Object> co1 = csF1;
        }

    static void testPCExpectedFailure2(PC<String> pcsF2)
        {
        C<Object> co2 = pcsF2;
        }

    static void testPCExpectedFailure3(P<Object> poF3)
        {
        P<String> ps3 = poF3;
        }

    static void testPCExpectedFailure4(PC<String> pcsF4)
        {
        FakePCofObject fpco4 = pcsF4;
        }

    static void testPCExpectedFailure5(PC<Object> pcoF5)
        {
        PC<String> pcs5 = pcoF5;
        }

    static void testPCExpectedFailure6(CC ccF6)
        {
        CC<Object> cco6 = ccF6;
        }

    static Int test2()
        {
        Int i = 0;
        return i;
        }

//    TestType1 extends Number,
//    TestType2 extends TestType1,
//    TestType3 extends TestType2
    void test3(TestType1 t1,
               TestType2 t2,
               TestType3 t3,
               C<TestType1> ct1,
               C<TestType2> ct2,
               PC<TestType3> pct3,
               C<Number> cn)
        {
        Number n1 = t1;
        Number n3 = t3;

        TestType1 t11 = t2;
        TestType1 t13 = t3;

        C<TestType3> ct3 = ct1;
        P<TestType1> pt1 = pct3;

        C<TestType3> ct31 = cn;
        P<Number> pn = pct3;
        PC<TestType1> pct1 = pct3;
        }

    void test3ExpectedFailure1(TestType3 tF3)
        {
        Int n = tF3;
        }

    static <Type1 extends Number, Type2 extends Type1>
        void test4(Type1 t1,
                   Type2 t2,
                   C<Type1> ct1,
                   PC<Type2> pct2)
        {
        Number n1 = t1;
        Number n2 = t2;

        Type1 t11 = t2;

        C<Type2> ct2 = ct1;
        P<Number> pn = pct2;
        PC<Number> pcn = pct2;
        }

    static <Type1 extends Number, Type2 extends Type1>
        void test5(Type1 | Type2 t12,
                   C<Type1> | C<Type2> c12,
                   C<Number> cn,
                   PC<Type1> + PC<Type2> pct12)
        {
        Number n = t12;
        c12 = cn;
        PC<Number> pcn = pct12;
        }

    static <Type1 extends Number, Type2 extends Type1>
            void test5ExpectedFailure1(C<Type1> | C<Type2> ctF5)
        {
        C<Number> cn = ctF5;
        }

    static void testTuple(Tuple t, Tuple<Int, String> tns)
        {
        Tuple t1 = t;
        Tuple t2 = tns;
        Tuple<Object, Object> to = tns; // wrapped
        Tuple<Number> tn = tns;
        }

    static void testTupleExpectedFailure1(Tuple<Number, String> tnsF1)
        {
        Tuple<Int, String> tn = tnsF1;
        }

    static void testTupleExpectedFailure2(Tuple tF2)
        {
        Tuple<Int> ti = tF2;
        }

    static class PCOfInt
            extends PC<Int>
        {
        }

    static void testClassConst()
        {
        // PC must refer to a class whose public type is of type PC
        Class<PC> clzPC = PC;
        Class<PC<Int>> clcAPC = PCOfInt; // PC<Int> by itself can only be a type

        Type<PC> typePC = PC;
        // Type<PC<Int>> typePCI = PC<Int>;
        }

    static void testEnumeration(Enumeration<False> enf, Enumeration en)
        {
        Class<False> cf = enf;
        Class c = enf;
        Class c2 = en;
        }

    static void testEnumerationExpectedFailure1(Enumeration<False> enF1)
        {
        Class<True> c = enF1;
        }

    static void testEnumerationExpectedFailure2(Enumeration enF2)
        {
        Class<True> c = enF2;
        }

    mixin MCI
            into MyConsumer<Int> | Int
        {
        }

    static void testIntersection(MCI mci)
        {
        Consumer<Int> | Number cnn = mci;
        }

    // auto-narrowing tests

    interface ANIface1
        {
        ANIface1 f1();
        }

    interface ANIface2
        {
        ANIface2! f1();
        }

    interface ANIface1D extends ANIface1
        {
        void f2();
        }

    class ANClass1
        {
        ANClass1 f1();
        }

    class ANClass2
        {
        ANClass2! f1();
        }

    static void testAutoNarrow(ANClass1 clz1, ANClass2 clz2)
        {
        ANIface1 iface11 = clz1;
        ANIface2 iface21 = clz1;
        ANIface1 iface12 = clz2;
        ANIface2 iface22 = clz2;
        }

    static void testList()
        {
        List<String> ls = List:{"a", "b", "c"};
        List<String> ls2 = ["a", "b", "c"];
        List<Int> li = List:{1, 2, 3};
        List<Int> li2 = [1, 2, 3];
        }

    static class Person
        {
        Person parent;
        List<Person> dependents;

        void add(Person! p) {dependents.add(p);}
        void addParent(Person! p) {parent = p;}

        void testExpectedFailure()
            {
            List<Employee> emps1 = dependents;
            }
        }

    static class Employee
            extends Person
        {
        void testAutoNarrow2()
            {
            List<Employee> emps2 = dependents;
            }
        }
    }
