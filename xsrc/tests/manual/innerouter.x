module TestInnerOuter
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        testSimple();
        testStaticIface();
        testAnonInner();
        }

    interface IfaceOuter
        {
        void fnOuter();

        static interface IfaceInner
            {
            void fnInner();
            }
        }

    class Base(String text)
            implements IfaceOuter
        {
        class Child_V()
            {
            String textByName.get()
                {
                return this.Base.text;
                }

            String textByOuter.get()
                {
                return outer.text;
                }
            }

        @Override
        void fnOuter()
            {
            }

        static const Child_NV(String name)
                implements IfaceOuter.IfaceInner
            {
            @Override
            void fnInner()
                {
                console.println(" -> fnInner");
                }
            }


        @Override
        String toString()
            {
            return text;
            }
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        Base b1 = new Base("Hello");
        Base b2 = new Base("World");
        Base... bases = [b1, b2];

        for (Base base : bases)
            {
            Base.Child_V child = base.new Child_V();
            console.println("Obtaining text from \"" + base + "\" child:");
            console.println(" -> by-outer=" + child.textByOuter);
            console.println(" -> by-name=" + child.textByName);
            }
        }

    void testStaticIface()
        {
        console.println("\n** testStaticIface()");

        IfaceOuter.IfaceInner childNV = new Base.Child_NV("NonVirtual");
        console.println($"childNV={childNV}");
        childNV.fnInner();
        }

    void testAnonInner()
        {
        console.println("\n** testAnonInner()");

        class Inner
            {
            construct(String s) {}
            }

        Int i = 4;

        // var o = new Object()
        var o = new Inner("hello")
            {
            void run()
                {
                console.println("in run (i=" + i + ")");
                ++i;
                // foo();
                }
            };

        o.run();

        console.println("done (i=" + i + ")");
        }
    }
