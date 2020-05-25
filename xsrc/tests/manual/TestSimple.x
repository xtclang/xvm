module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Root root = new Root();
        console.println(root);

        ElementInputStream s = new ElementInputStream(Null);
        console.println(s);
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