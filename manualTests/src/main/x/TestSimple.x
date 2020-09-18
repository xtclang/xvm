module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Iface d = new Delegator(new Base());
        console.println(d.value);
        }

    interface Iface
        {
        @RO Int value.get()
            {
            console.println("default");
            return 1;
            }
        }

    class Base
            implements Iface
        {
        @Override
        @RO Int value.get()
            {
            console.println("base");
            return 2;
            }
        }

    class Delegator(Base base)
            delegates Iface(base)
        {
        }
    }
