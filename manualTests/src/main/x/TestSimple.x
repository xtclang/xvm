module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method m = Test.doesNotCompile;
        console.println(m);
        assert m.is(Foo);
        console.println(m.bar);
        }

    const Bar(Int i)
        {
        }

    mixin Foo(Bar? bar = Null)
        into Method;

    static Bar TWO_BAR = new Bar(2);

    class Test
        {
        @Foo(TWO_BAR)   // this used to fail to compile
        void doesNotCompile()
            {
            }
        }
    }