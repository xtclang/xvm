module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        Parent       p = new Parent();
        Parent.Child c = p.new Child();

        console.println(&c.actualType.OuterType);
        foo(c, p);
        }

    <TargetType> void foo(TargetType target, TargetType.OuterType outer)
        {
        console.println(outer);
        }

    class Parent
        {
        class Child
            {
            }
        }
    }
