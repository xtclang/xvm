module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;
    import Ecstasy.reflect.Access;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        console.println(Ecstasy.qualifiedName);
        console.println(TestSimple);
        val x = TestSimple.ecstasy;
//        console.println(x);
        console.println(TestSimple.simpleName);
        console.println(TestSimple.dependsOn);
        console.println(Ecstasy.collections.maps.simpleName);
        console.println(Ecstasy.collections.maps.qualifiedName);
        new P.C();
        }

    package P
        {
        class C
            {
            construct()
                {
                console.println(this:module.qualifiedName);
                }
            }
        }
    }
