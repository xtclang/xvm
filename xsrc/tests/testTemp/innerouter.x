module TestInnerOuter.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        testSimple();
        testAnonInner();
        }

    class Base(String text)
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

        static class Child_NV
            {
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
        Base[] bases = [b1, b2];        // TODO allow "Base..."

        for (Base base : bases)
            {
            Base.Child_V child = base.new Child_V();
            console.println("Obtaining text from \"" + base + "\" child:");
            console.println(" -> by-outer=" + child.textByOuter);
            console.println(" -> by-name=" + child.textByName);
            }
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
