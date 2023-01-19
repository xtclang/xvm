module TestGenerics
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        console.print("Generic tests");

        testArrayType();
        testVirtualChild();
        testTypeParams();
        testTurtleType();
        }

    void testArrayType()
        {
        String[] list = new String[];
        list += "one";

        console.print("type=" + list.Element);

        list.Element el0 = list[0];
        console.print("el0=" + el0);
        }

    void testVirtualChild()
        {
        Base<Int> bi = new Base();

        Base<Int>.Child c1 = bi.new Child();
        console.print("bi.c1=" + c1);

        Base<Int>.Child2<String> c2 = bi.new Child2<String>();
        console.print("bi.c2=" + c2);

        bi.createChild();
        }

    interface Runnable
        {
        void run();
        }

    class Base<BaseType>
        {
        Base<BaseType>? nextBase;
        Child? nextChild;

        class Child
            {
            @Override
            String toString()
                {
                return super() + " outer = " + this.Base;
                }
            }

        class Child2<ChildType>
            {
            @Override
            String toString()
                {
                return super() + " outer = " + this.Base + " type=" + ChildType;
                }
            }

        void createChild()
            {
            Child c1 = new Child(); // compile time type is B<T>.C
            console.print("c1=" + c1);

            Base<Int> b2 = new Base<Int>();
            Base<Int>.Child c2 = b2.new Child();
            console.print("c2=" + c2);

            Base<> b3 = createBase();
            Base<>.Child c3 = b3.new Child();
            console.print("c3=" + c3);

            Base<String> b4 = new Derived<String>();
            Base<String>.Child c4 = b4.new Child();
            console.print("c4=" + c4);

            Base<Int>.Child2<String> c5 = b2.new Child2<String>();
            }

//        void createChildTestExpectedFailure1()
//            {
//            Base<Int> bi = new Base<Int>();
//            Child ci = bi.new Child(); // compile time error; type is B<Int>.C; not assignable to B<T>.C
//            }
//
//        void createChildTestExpectedFailure2()
//            {
//            Base<> b = createBase();              mj
//            Child c = b.new Child(); // compile time error; type is B<Object>.C; not assignable to B<T>.C
//            }
//
        Base!<> createBase()
            {
            return new Base<String>();
            }
        }

    class Derived<DerivedType>
            extends Base<DerivedType>
        {
        }

    class Derived2<Derived2Type>
            extends Derived<Derived2Type>
        {
        }

    void testTypeParams()
        {
        Derived<String>  d1 = new Derived();
        Derived2<String> d2 = new Derived2();

        foo(d1, d2, d2);
        }

    <CompileType1 extends Base, CompileType2 extends CompileType1, CompileType3 extends CompileType2>
            void foo(CompileType1 c1, CompileType2 c2, CompileType3 c3)
        {
        assert c2.as(CompileType1) != c1;
        }

    void testTurtleType()
        {
        TestTurtle<<Int, String>> turtle = new TestTurtle<<Int, String>>();
        console.print($"turtle.getType(0)={turtle.getType(0)}");

        class TestTurtle<TurtleTypes extends Tuple<TurtleTypes>>
            {
            Type getType(Int index)
                {
                // TODO GG: the following line generates "suspicious assignment"
                // List<Type> types = TurtleTypes;
                return TurtleTypes[index];
                }
            }
        }
    }