module Test
        {
//    class C
//        {
//        construct()
//            {
//            construct C(4);
//            }
//
//        construct(Int n)
//            {
//            this.n = n;
//            }
//
//        Int n;
//        }
    // class C
    //   {
    //   construct C()
    //      {
    //      construct Object();
    //      }
    //   }

    void run()
        {
        // Tuple<Int, Int> t = (0, 1);
//        Int i = foo();
//        @Inject X.io.Console console;
//        console.println("Hello world!");
//         Int[] ai = new Int[4];
//        C c = new C();
        }

    Int[] foo2()
        {
        return [foo(), 0, foo()];
        }

    Int foo()
        {
        return foo2()[0];
        }

/*
    const Person(String name) {}

    const Point(Int x, Int y)
        default(Origin);

    const Point3d(Int x, Int y, Int z = 0)
        extends Point(x, y);

    static const Origin
        extends Point(0, 0);

//    const Person
//        {
//        construct(String name)
//            {
//            this.name = name;
//            }
//
//        String name;
//        }

    const Employee(String name)
            extends Person(name)
        {
        }
*/

//    class Outer
//        {
//        Int x = 4;
//
//        class C
//            {
//            @RO Int y
//                {
//                Int z = 5;
//
//                @Override
//                Int get()
//                    {
//                    Outer outer = Outer.this;
//                    C c = C.this;
//                    Ref<Int> v = y.this;
//                    return Outer.this.x; // TODO  + y.this.z;
//                    }
//
////                void set(Int n)
////                    {
////                    Ref<Int> r1 = y.this;
////                    Var<Int> r2 = y.this;
////                    Ref<Int> r3 = &y;
////                    Var<Int> r4 = &y;
////                    Ref<Int> r5 = &z;
////                    Var<Int> r6 = &z;
////                    Ref<Int> r7 = y.this.&z;
////                    Var<Int> r8 = y.this.&z;
////
////                    Outer.this.x = n;
////                    y.this.z     = n;
////                    }
//                }
//            }
//        }

//    class Fubar
//        {
//        // problem #1 - is the solution a SubstitutableTypeConstant that takes the place of each instance of "T"?
//        <T> conditional T foo(T t)          // compiled as "(Boolean*, T) foo(Type<Object> T*, T t)"
//            {
//            return True, t;
//            }
//
//        // problem #2 ".Type" resolution ... kind of like problem #2 ... hmmm ...
//        Fubar! fn(String s)
//            {
//            return this;
//            }
//        }
//
//    // problem #3 - functions as types
//    (function (Int, Int) (String, String)) fn;
//    function (Int, Int) (String, String) fn2;
//    function (Int, Int) fn3(String, String).get()
//        {
//        return fn2;
//        }
//
//    // problem #4 - tuples weren't reporting themselves as having more than 1 value
//    static (Int, Int) f(String a, String b)
//        {
//        return (0,0);
//        }
//
//    // problem #5 - not sure what this problem was, but it compiles now (the T0D0 was an issue)
//        interface List<Element>
//                {
//                Element first;
//
//                void add(Element value);
//
//                Iterator<Element> iterator();
//                }
//
//    class MyList<Element extends Int>
//            implements List<Element>
//        {
//        void add(Element value)
//            {
//            TODO
//            }
//        }
//
//    // problem #6
//    class MyClass<MapType1 extends Map, MapType2 extends Map>
//        {
//        void process(MapType1.Key k1, MapType2.Key k2)  // TODO resolve both "Key" correctly
//            {
//            // ...
//            }
//
//        <MT3 extends MapType1, KT3 extends MapType1.Key> void process(MT3.Key k, KT3 k3)  // TODO resolve both "Key" correctly
//            {
//            // ...
//            }
//        }
//
//    // problem #7 - conditional mixin
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
//    // problem #8 - typedefs
//    typedef function void Alarm();
//
//    class MyTest3
//        {
//        Alarm alarm;
//
//        Alarm foo(Alarm alarm);
//        }
//
//    // problem #9 - void compiling to Tuple instead of being eliminated
//    void fnVoid();
//    Tuple fnTupleNone();
//    Tuple<> fnTupleEmpty();
//    Tuple<Int> fnTupleInt();
//    Tuple<Tuple> fnTupleTuple();
//
//    // problem #10 - InjectedRef.Referent resolves in compilation to Ref.Referent (wrong!)
//    @Inject String option;
//
//    // problem #11 - sig is wrong (shows Void, should be String)
//    @Inject String option2.get()
//        {
//        return super.get();
//        }
//
//    // problem #12 - constructors are named after the class instead of "construct"
//    class ConstructorTest
//        {
//        construct ConstructorTest(Int i) {}
//        construct ConstructorTest(String s) {} finally {}
//        }
//
//    // problem #13 - various return type tests, @Op tests, and conversion tests
//    void foo1()
//        {
//        }
//
//    String foo1MissingReturn() // note: this is supposed to generate an error
//        {
//        }
//
//    String foo1String()
//        {
//        return "hello" * 5;
//        }
//
//    String foo1String2()
//        {
//        return 'x' * 5;
//        }
//
//    void foo2()
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
//
//    // problem #14 - TODO this still fails (AssignmentStatement#emit does not yet implement "+=")
//    Int foo2e()
//        {
//        Int i = 0;
//        i += 1;
//        return i;
//        }
//
//    // problem #15 - needs fix for isA: Tuple<String, Int>.isA(Tuple) == false
//    (String, Int) foo5b()
//        {
//        return ("hello", 0);
//        }
//
//    // problem #16 - conditional tests
//    conditional String foo6()
//        {
//        return false;
//        }
//
//    conditional String foo7()
//        {
//        return true, "hello";
//        }
//
//    // problem #17 - operator +
//    Int foo8(Int a, Int b)
//        {
//        return a + b;
//        }
//
//    // problem #18 - operator + and auto-conversion of IntLiteral to Int
//    Int foo8()
//        {
//        return 40 + 2;
//        }
//
//    // problem #19 - while loops
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
//
//    // problem #20 - "this", auto-narrowing types
//    class C20
//        {
//        C20 bar()
//            {
//            return this;
//            }
//        }
//
//    // problem #21 - calling a method
//    void foo()
//        {
//        }
//
//    void bar()
//        {
//        foo();
//        }
//
//    // problem #22 - calling a function
//    static void foo()
//        {
//        }
//
//    static void bar()
//        {
//        foo();
//        }

//    // TypeInfo testing
//
//    interface I
//        {
//        @RO Int x.get()
//            {
//            return 0;
//            }
//        }
//
//    class DumpC1
//        implements I
//        {
//        @Lazy Int y.calc()
//            {
//            return 4;
//            }
//        }
//
//    class DumpC12
//        extends DumpC1
//        {
//        Int z;
//        }
//
//    class DumpC2
//        implements I
//        {
//        @Override
//        public/private Int x;
//        }
//
//    class DumpC3
//        implements I
//        {
//        @Override
//        public Int x.get()
//            {
//            return 0;
//            }
//        }

//    // TypeInfo testing
//
//    class B
//        {
//        Object foo() {return "hello";}
//        }
//
//    // this will fail (compiler error), because there is no exact sig match for the super (and no
//    // @Override)
////    class D1 extends B
////        {
////        String foo() {return super();}
////        }
//
//    // this will succeed, because even though there is no exact sig match for the super, the
//    // @Override annotation indicates that it may use (i.e. must find) a compatible signature.
//    // because it overrides the "Object foo()" method, it "caps" that method chain, such that it would
//    // redirect to this chain); calls to "Object foo()" will redirect to "String foo()" now
//    class D2 extends B
//        {
//        @Override
//        String foo()
//            {
//            // could just say "return super()" and the compiler will know to insert a cast because
//            // of the presence of the @Override
////            Object o = super();
//            return "test"; // TODOo.toString();
//            }
//        }
//
//    // this will fail, because even though @Override was used, there is now no unambiguous method
//    // to call to support "Object foo()"
////    class D3 extends B
////        {
////        @Override
////        String foo() {return "hello";}
////
////        @Override
////        Int foo() {return 4;}
////        }
//
//    // this will succeed, because it provides an unambiguous (exact signature match) method to call
//    // to support "Object foo()"
//    class DumpD4 extends B
//        {
//        @Override
//        Object foo();   // no {} required here .. this just indicates that the call chain isn't "capped"
//
//        @Override
//        String foo() {return "hello";}
//
//        @Override
//        Int foo() {return 4;}
//        }


//    // type info for nested children
//    class C
//        {
//        Int x
//            {
//            Int get()
//                {
//                static Int z = 0;
//                return z++;
//                }
//            }
//
//        Int y
//            {
//            void set(Int value)
//                {
//                super(value);
//                }
//            }
//        }
//
//    class D extends C
//        {
//        Int x
//            {
//            Int get()
//                {
//                return super();
//                }
//            }
//
//        Int y
//            {
//            void set(Int value)
//                {
//                super(value);
//                }
//            }
//        }


//    class C
//        {
//        Int x = 0;
//        void foo(Int i) {}
//        void testVariations()
//            {
//            Int i = x;
//            x = i;
//
//            // TODO requires multi-name resolution
//            // this.x = i;
//
//            // TODO requires post-bang
//            // Property p = x!;
//
//            // TODO requires multi-name resolution
//            // Property p = this.x!;
//            // Property p = C.x;
//
//            // TODO requires pre-ampersand
//            // Ref<Int> = &x;
//
//            // TODO
//            // Function f1 = foo;
//            // Function<<Int>, Void> f1b = foo;
//
//            // TODO requires post-bang
//            // TODO Method m1 = foo!;
//
//            // TODO requires multi-name resolution
//            // Method m2 = C.testVariations;
//            // Function f2 = this.testVariations;
//
//            // TODO
//            // Function f3 = foo(?);
//            // Function<<Int>, Void> f3b = foo(?);
//            // Function f4 = foo(4)!;
//            // Function<Void, Void> f4b = foo(4);
//            }
//        }

//    Int test(Int c)
//        {
//        Int i = 0;
//        while (i < c)
//            {
//            i = i + 1;
//            }
//        return i;
//        }

//    class Bob
//        {
//        @Auto Sam to<Sam>();
//        }
//    class Sam
//        {
//        }
//
//    Sam foo(Bob bob)
//        {
//        // assignment test
//        Sam sam = bob;
//
//        // conversion on return test
//        return bob;
//        }
//

//    // auto-narrowing tests:
//    class C
//        {
//        C x;
//        C! y;
//
//        C foo(C c)
//            {
//            TODO
//            }
//
//        C! bar(C! c)
//            {
//            TODO
//            }
//
//        class K
//            {
//            C foo(K kid)
//                {
//                TODO
//                }
//            C! bar(K! kid)
//                {
//                TODO
//                }
//            }
//        }
//
//    class D extends C
//        {
//        D x;
//        D! y;
//
//        D foo(D c)
//            {
//            TODO
//            }
//
//        D! bar(D! c)
//            {
//            TODO
//            }
//
//        class K extends C.K
//            {
//            D foo(K kid)
//                {
//                TODO
//                }
//            D! bar(K! kid)
//                {
//                TODO
//                }
//            }
//        }

//    class MyMap<Key, Value> implements Map<Key, Value>
//        {
//        void foo();
//        @Auto Int size();
//        }
//
//    mixin M into MyMap {}
//
//    class MyMap2<Key, Value> extends MyMap<Key, Value>
//        {
//        public/private @Unchecked Int x
//            {
//            @Unchecked Int get() {return 0;}
//            void set(@Unchecked Int n) {}
//            }
//
//        void bar();
//        @Auto Int size();
//
//        static Int y = 0;
//        static Int z = () -> y;
//        }
//
//    mixin M2 into MyMap2 extends M {}
//
//    class B incorporates M {}
//    class D extends B incorporates M2 {}
//
//    Int foo()
//        {
//        MyMap2<Object, Object> map;
//        return map;
//        }

//    // this fails!!!
//    function Object() foo(Object o)
//        {
//        return o;
//        }
        }