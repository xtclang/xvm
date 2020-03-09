module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        new @Mix Base1().read<String>();
        }

    interface Iface
        {
        <Ser> Ser read<Ser>();
        }

    class Base1
            implements Iface
        {
        @Override
        <Ser> Ser read<Ser>()
            {
            console.println($"B1 {Ser}");
            return "a".as(Ser);
            }
        }

    class Base2
            implements Iface
        {
        @Override
        <Ser> Ser read<Ser>(Ser? defaultValue = Null)
            {
            console.println($"B2 {Ser}");
            return "a".as(Ser);
            }
         }

    mixin Mix
        into (Base1 | Base2)
        {
        @Override
        <Ser> Ser read<Ser>()
            {
            console.println($"Mix {Ser}");
            return super();
            }
        }
    }
