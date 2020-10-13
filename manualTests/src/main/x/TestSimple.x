module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Derived d = new Derived<Int>();
        console.println(d.testType(0));
        console.println(d.testType(1));
        console.println(d.testType(2));
        }

    class Derived<Element>
            extends Base<Element?>
        {
        construct()
            {
            console.println(Referent);

            assert Referent == Element?;
            assert Referent == Nullable|Element;
            assert Type<Referent> == Type<Element?>;
            assert Type<Referent> == Type<Nullable|Element>;
            }

        void testProp(Property prop)
            {
            assert Type<prop.Referent> == Type<Element?>;
            }

        Type testType(Int i)
            {
            switch (i)
                {
                case 0:
                    return Element?;

                case 1:
                    return Nullable|Element;

                case 2:
                    return Referent+Element;

                default:
                    TODO
                }
            }
        }

    class Base<Referent>
        {
        }
    }
