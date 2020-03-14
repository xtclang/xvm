module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;
    import Ecstasy.reflect.Access;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        console.println(Base.PublicType.explicitlyImmutable);

        if (Access access := Base.ProtectedType.accessSpecified())
            {
            console.println(access);
            }
        }

   class Base
        {
        }
    }
