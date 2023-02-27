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
        testConditionalMixins();
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

    void testConditionalMixins()
        {
        import testConditional.*;

        Derived1 d1 = new Derived1();
        assert d1.mi == 1;

        Derived2 d2 = new Derived2();
        assert d2.mi == 1 && d2.bi == 2;

        Derived2a d2a = new Derived2a(3);
        assert d2a.mi == 4 && d2a.bi == 5;

        Derived3<Number> d3n = new Derived3(Int:3);
        assert d3n.bi == 1;

        Derived3<Int> d3i = new Derived3(3);
        assert d3i.bi == 1 && d3i.mi == 4;
        }

    package testConditional
        {
        const Base {}

        const Base2(Int bi)
                extends Base {}

        mixin Mix<Element>(Int mi)
                into Base {}

        const Derived1 extends Base
                incorporates Mix(1) {}

        const Derived2
                incorporates Mix(1)
                extends Base2(2) {}

        const Derived2a(Int d2i)
                incorporates Mix(d2i+1)
                extends Base2(d2i+2) {}

        const Derived3<Value>(Value d3i)
                extends Base2(1)
                incorporates conditional Mix<Value extends Int>(d3i+1) {}
        }
    }