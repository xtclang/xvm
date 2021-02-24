module TestSimple
    {
    @Inject Console console;

    void run()
        {
        report(report.as(Method));
        report(Inner.foo.as(Method));
        report(value.as(Property));
        report(Inner.size.as(Property));

        class Inner
            {
            @Tagged(weight=1)
            void foo()
                {
                }

            @Tagged(weight=3)
            Int size;
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