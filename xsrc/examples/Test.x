module Test
    {
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

    mixin MyMixin<T extends Int>
        {
        // ...
        }

    class MyClass2<T>
            incorporates conditional MyMixin<T extends Int>
        {
        // TODO
        }

    typedef Void Alarm();

    class MyTest3
        {
        Alarm alarm;

        Alarm foo(Alarm alarm);
        }
    }