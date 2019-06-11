module TestInnerOuter.xqiz.it
    {
    void run()
        {
        @Inject X.io.Console console;
        console.println("hello world! (inner-class / outer-reference tests)");

        testSimple();
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
        @Inject X.io.Console console;
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
    }
