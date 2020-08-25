module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        Int n = 0;

        Base b = new Base(1);
        console.println(b);

        Base b2 = b.new(b);
        console.println(b2);

        Base d = new Derived(2);
        console.println(d);

        Base d2 = d.new(d);
        console.println(d2);

        Empty e = d2;
        }

    mixin Silly(Int i, String s)
            into Var
        {
        }
// TODO
//    @Link(second) Int first = 1;
//    Int second = 5;
//
//    mixin Link(Property next)
//            into Var
//        {
//        }
    }

