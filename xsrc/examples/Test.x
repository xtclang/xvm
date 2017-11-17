module Test
    {
    // this causes IllegalStateException:
    //  t i = 0;
    // Exception in thread "main" java.lang.IllegalStateException: unresolved constant: TerminalType{type=t}
    // 	at org.xvm.asm.ConstantPool.register(ConstantPool.java:109)
    // 	at org.xvm.asm.PropertyStructure.registerConstants(PropertyStructure.java:178)
    // 	at org.xvm.asm.Component.registerChildConstants(Component.java:1401)
    // 	at org.xvm.asm.Component.registerChildrenConstants(Component.java:1381)
    // 	at org.xvm.asm.Component.registerChildConstants(Component.java:1405)
    // 	at org.xvm.asm.Component.registerChildrenConstants(Component.java:1381)
    // 	at org.xvm.asm.FileStructure.registerConstants(FileStructure.java:736)

//    class Fubar
//        {
//        Int number;
//        Int[] numbers;
//        Object... params;
//
//// problem #1 - is the solution a SubstitutableTypeConstant that takes the place of each instance of "T"?
//        <T> conditional T foo(T t)          // compiled as "(Boolean*, T) foo(Type<Object> T*, T t)"
//            {
//            return t;
//            }
//
//// problem #2 ".Type" resolution ... kind of like problem #2 ... hmmm ...
//        Fubar! fn(String s)
//            {
//            return this;
//            }
//        }
//
//    Void foo(Int i) {}
//
//// problem #3 - functions as types
//    (function (Int, Int) (String, String)) fn;
//    function (Int, Int) (String, String) fn2;
//    function (Int, Int) fn3(String, String).get()
//        {
//        return f;
//        }
//
//    static (Int, Int) f(String a, String b)
//        {
//        return (0,0);
//        }
//
//    interface List<ElementType>
//        {
//        ElementType first;
//
//        Void add(ElementType value);
//
//        Iterator<ElementType> iterator();
//        }
//
//    class MyList<ElementType extends Int>
//            implements List<ElementType>
//        {
//        Void add(ElementType value)
//            {
//            TODO
//            }
//        }
//
//    class MyClass<MapType1 extends Map, MapType2 extends Map>
//        {
//        Void process(MapType1.KeyType k1, MapType2.KeyType k2)  // TODO resolve both "KeyType" correctly
//            {
//            // ...
//            }
//
//        <MT3 extends MapType1, KT3 extends MapType1.KeyType> Void process(MT3.KeyType k, KT3 k3)  // TODO resolve both "KeyType" correctly
//            {
//            // ...
//            }
//        }
//
//    mixin MyMixin<T>
//        {
//        // ...
//        }
//
//    class MyClass2<T>
//            incorporates conditional MyMixin<T extends Int>
//        {
//        // TODO
//        }
//
//    typedef function Void Alarm();
//
//    class MyTest3
//        {
//        Alarm alarm;
//
//        Alarm foo(Alarm alarm);
//        }

// problem: Void compiling to Tuple instead of being eliminated
//    Void fnVoid();
//    Tuple fnTupleNone();
//    Tuple<> fnTupleEmpty();
//    Tuple<Int> fnTupleInt();
//    Tuple<Tuple> fnTupleTuple();

// problem: InjectedRef.RefType resolves in compilation to Ref.RefType (wrong!)
//    @Inject String option;        // TODO figure out where the @Inject ended up
//
//    @Inject String option2.get()  // TODO sig is wrong (shows Void, should be String)
//        {
//        return super.get();
//        }

// problem: constructors are named after the class instead of "construct"
//    class ConstructorTest
//        {
//        construct ConstructorTest(Int i) {}
//        construct ConstructorTest(String s) {} finally {}
//        }

//    Void foo1()
//        {
//        }

//    String foo1String()
//        {
//        return "hello" * 5;
//        }
//
//    String foo1String2()
//        {
//        return 'x' * 5;
//        }

//    String foo1MissingReturn()
//        {
//        }

//    Void foo2()
//        {
//        return;
//        }
//
//    Int foo2b(Int i)
//        {
//        return i;
//        }
//
//    Int foo2c()
//        {
//        Int i = 0;
//        return i;
//        }
//
//    Int foo2d()
//        {
//        Int i = 0;
//        i = i + 1;
//        return i;
//        }
//
//    Int foo2e()
//        {
//        Int i = 0;
//        i += 1;
//        return i;
//        }
//
//    String foo3()
//        {
//        return "hello";
//        }
//
//    Int foo4()
//        {
//        return 0;
//        }
//
//    (String, Int) foo5()
//        {
//        return "hello", 0;
//        }

// needs fix for isA: Tuple<String, Int>.isA(Tuple) == false
//    (String, Int) foo5b()
//        {
//        return ("hello", 0);
//        }

    conditional String foo6()
        {
        return false;
        }

    conditional String foo7()
        {
        return true, "hello";
        }

//    Int foo8(Int a, Int b)
//        {
//        return a + b;
//        }

//    Int foo9(Iterator<Int> iter)
//        {
//        Int sum = 0;
//        while (Int i : iter.next())
//            {
//            sum += i;
//            }
//
//        // just for comparison
//        while (sum < 10)
//            {
//            ++sum;
//            }
//
//        return sum;
//        }

//    Test bar()
//        {
//        return this;
//        }
    }