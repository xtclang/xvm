module TestSimple
    {
    @Inject Console console;

    void run(   )
        {
        Root root = new Root();
        console.println(root);

        C2 c2 = new C2("h");
        console.println(c2);
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

    const C0(Int i);

    const C1 extends C0
        {
        construct(String s)
            {
            construct C0(42);
            }
        }
    const C2 extends C1
        {
        construct(String s)
            {
            construct C0(41);
            }
        }
    }