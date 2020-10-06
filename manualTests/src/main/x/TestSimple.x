module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Parent p = new Parent();
        Parent.Child c = p.child();

        if (c.is(Inner))
            {
            Object outer = c.outer;
            console.println(outer.is(Service));
            }
        }

    service Parent()
        {
        class Child
            {
            Int foo()
                {
                return 1;
                }
            }

        Child child()
            {
            return new Child();
            }
        }
    }
