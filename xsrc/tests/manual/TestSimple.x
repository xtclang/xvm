module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Class c = String;

        console.println(c.implicitName);
        }


    class Root
            extends ElementInputStream<Nullable>
        {
        construct()
            {
            construct ElementInputStream(Null);
            }
        }

    class ElementInputStream<ParentInput extends ElementInputStream?>
        {
        construct(ParentInput parent)
            {
            console.println(parent);
            }
        }
    }