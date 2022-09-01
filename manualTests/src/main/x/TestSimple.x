module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Class c0 = Test;
        Class c1 = Test1;

        assert c0.implements(Runnable);
        assert val anno := c1.annotatedBy(Named);
        console.println($"{anno.arguments[0].name}={anno.arguments[0].value}");
        }

    interface Runnable
        {
        void run();
        }

    mixin Named(String name)
            into Runnable
        {
        String name;
        }

    class Test
        implements Runnable
        {
        @Override
        void run()
            {
            TODO
            }
        }

    @Named("one")
    class Test1
           extends Test
        {
        }
    }