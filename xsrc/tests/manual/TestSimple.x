module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        Iface[] faces = [new Class1(), new Class2()];
        for (Iface face : faces)
            {
            console.println(face.getValue());
            }
        }

    interface Iface
        {
        Int getValue();
        }

    class Class1
            implements Iface
        {
        @Override
        Int getValue() {return 1;}
        }

    class Class2
            implements Iface
        {
        @Override
        Int getValue() {return 2;}
        }
    }
