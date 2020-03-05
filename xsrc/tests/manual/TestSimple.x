module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        new Derived().read<String>();
        }

    class Base
        {
        <Ser> Ser read<Ser>(Ser? defaultValue = Null)
            {
            console.println($"Base {Ser}");
            return "a".as(Ser);
            }
        }

    class Derived
            extends Base
        {
        @Override
        <Serializable> Serializable read<Serializable>(Serializable? defaultValue = Null)
            {
            console.println($"Der {Serializable}");
            return super(defaultValue);
            }
         }
    }
