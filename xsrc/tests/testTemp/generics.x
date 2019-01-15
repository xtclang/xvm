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
        bi.createChild();
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

        void createChild()
            {
            Child c = new Child(); // compile time type is B<T>.C

            Base<Int> b2 = new Base<Int>();
            Base.Child<Int> c2 = b2.new Child();
            console.println("c2=" + c2);

            Base<> b3 = createBase();
            Base.Child<> c3 = b3.new Child();
            console.println("c3=" + c3);

            Base<String> b4 = new Derived<String>();
            Base.Child<String> c4 = b4.new Child();
            console.println("c4=" + c4);
            }

        void createChildTestExpectedFailure()
            {
            Base<Int> bi = new Base<Int>();
            Child ci = bi.new Child(); // compile time error; type is B<Int>.C; not assignable to B<T>.C

            Base<> b = createBase();
            Child c = b.new Child(); // compile time error; type is B<Object>.C; not assignable to B<T>.C
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
    }