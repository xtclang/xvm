module TestGenerics.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Generic tests");

        testArrayType();
        testVirtualChild();
        }

    void testArrayType()
        {
        String[] list = new String[];
        list += "one";

        console.println("type=" + list.ElementType);

        list.ElementType el0 = list[0];
        console.println("el0=" + el0);
        }

    void testVirtualChild()
        {
        Base<Int> bi = new Base();

        Base<Int>.Child c1 = bi.new Child();
        console.println("bi.c1=" + c1);

        Base<Int>.Child2<String> c2 = bi.new Child2<String>();
        console.println("bi.c2=" + c2);

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
            String to<String>()
                {
                return super() + " outer = " + Base.this;
                }
            }

        class Child2<ChildType>
            {
            @Override
            String to<String>()
                {
                return super() + " outer = " + Base.this + " type=" + ChildType;
                }
            }

        void createChild()
            {
            Child c1 = new Child(); // compile time type is B<T>.C
            console.println("c1=" + c1);

            Base<Int> b2 = new Base<Int>();
            Base<Int>.Child c2 = b2.new Child();
            console.println("c2=" + c2);

            Base<> b3 = createBase();
            Base<>.Child c3 = b3.new Child();
            console.println("c3=" + c3);

            Base<String> b4 = new Derived<String>();
            Base<String>.Child c4 = b4.new Child();
            console.println("c4=" + c4);

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
    }