module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;
    import Ecstasy.TypeSystem;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        console.println(Ecstasy.qualifiedName);
        console.println(TestSimple);
        console.println(TestSimple.ecstasy);
        console.println(TestSimple.simpleName);
        console.println(TestSimple.dependsOn);
        console.println(Ecstasy.collections.maps.simpleName);
        console.println(Ecstasy.collections.maps.qualifiedName);

        Module   thisModule = this:module;
        Module[] allModules = [thisModule] + thisModule.dependsOn;

        console.println(allModules);

        TypeSystem ts = new TypeSystem(allModules);
        console.println(ts);

        if (Module mod := ts.moduleBySimpleName.get("TestSimple"))
            {
            console.println($"ts={mod}");
            }
        }
    }
