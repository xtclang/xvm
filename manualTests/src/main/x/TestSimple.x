module TestSimple
    {
    @Inject Console console;

    void run()
        {
        report(Inner.foo);
        report(Inner.bar);

        class Inner
            {
            @Tagged(weight=1)
            void foo(@Unchecked @Tagged(weight=2) Int i)
                {
                }

            @Tagged(weight=3, tag="m-tag")
            void bar(@Tagged(weight=4, tag="p-tag") @Unchecked Int i)
                {
                }
            }
        }

    mixin Tagged(String tag="none", Int weight=-1)
            into Method | Parameter
        {
        @Override
        Int estimateStringLength()
            {
            return super() + tag.size + "weight=".size + 2;
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            $"@Tagged({tag} {weight}) ".appendTo(buf);
            return super(buf);
            }
        }

    void report(Method m)
        {
        console.println(m);
        console.println();

        if (m.is(Tagged))
            {
            assert m.tag.size > 0;
            assert m.weight >= 0;
            }
        }
    }