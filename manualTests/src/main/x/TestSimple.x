module TestSimple
    {
    @Inject Console console;

    void run()
        {
//        report(report.as(Method));
//        report(Inner.foo.as(Method));
//        report(value.as(Property));
//        report(Inner.price.as(Property));
        report(Inner.as(Class));

        @Tagged(weight=5)
        class Inner
            {
            @Tagged(weight=1)
            void foo()
                {
                }

            @Tagged(weight=3)
            Int price;
            }
        }

    @Tagged(weight=2)
    @Lazy Int value.calc()
        {
        return 42;
        }

    mixin Tagged(String tag="none", Int weight=-1)
            into Method | Property | Class
        {
        }

    @Tagged(weight=0, tag="a")
    void report(Method|Property|Class m)
        {
        console.println(m);

        if (m.is(Tagged))
            {
            assert m.tag.size > 0;
            assert m.weight >= 0;
            console.println($"tag={m.tag} weight={m.weight}\n");
            }
        }
    }