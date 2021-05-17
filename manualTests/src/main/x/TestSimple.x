module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Impl t = new Impl();
        t.foo();
        }

    interface Iface1
        {
        Iface1 foo()
            {
            TODO
            }
        }

    interface Iface2
        {
        Iface2 foo();
        }

    class Impl
            implements Iface1
            implements Iface2
        {
        @Override
        Impl foo()
            {
            return this;
            }
        }
    }