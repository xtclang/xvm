module TestSimple
    {
    @Inject Console console;

    void run()
        {
        val c1 = new Derived().createChild1();
        console.print(&c1.actualType);

        val c2 = new Derived().createChild2();
        console.print(&c2.actualType);
        }

    service Base
        {
        class Child1{}

        Child1 createChild1()
            {
            return new Child1();
            }

        class Child2<Value>{}

        Child2 createChild2()
            {
            return new Child2<String>(); // used to be compiled incorrectly
            }
        }

    service Derived
            extends Base {}
    }