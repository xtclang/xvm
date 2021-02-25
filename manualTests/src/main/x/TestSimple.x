module TestSimple
    {
    @Inject Console console;

    void run(  )
        {
//        report(report.as(Method));
//        report(Inner.foo.as(Method));
//        report(value.as(Property));
//        report(Inner.price.as(Property));
        report(new Inner());

        @Tagged(weight=5)
        class Inner
                implements Tag
            {
            @Tagged(weight=1)
            void foo()
                {
                }

            @Override
            @Tagged(weight=3)
            Int price;
            }
        }

    @Tagged(weight=2)
    @Lazy Int value.calc()
        {
        return 42;
        }

    interface Tag
        {
        Int price;
        }

    mixin Tagged(String tag="none", Int weight=-1)
            into Method | Property | Tag
        {
        }

    @Tagged(weight=0, tag="a")
    void report(Method|Property|Tag m)
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