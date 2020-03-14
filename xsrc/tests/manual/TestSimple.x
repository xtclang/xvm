module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;
    import Ecstasy.reflect.Access;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        new Base();
        }

   class Base
        {
        construct()
            {
            console.println(&this.actualType);
            }
        }
    }
