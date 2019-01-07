module TestGenerics.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Generic tests");

        testArrayType();
        }

    void testArrayType()
        {
        String[] list = new String[];
        list += "one";

        console.println("type=" + list.ElementType);

        list.ElementType el0 = list[0];
        console.println("el0=" + el0);
        }
    }