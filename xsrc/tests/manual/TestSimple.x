module TestSimple.xqiz.it
    {
    import ecstasy.collections.HashMap;
    import ecstasy.TypeSystem;

    @Inject ecstasy.io.Console console;

    void run()
        {
//        console.println(ecstasy.qualifiedName);
        console.println(TestSimple);
        console.println(TestSimple.ecstasy);
        console.println(TestSimple.simpleName);
        console.println(TestSimple.dependsOn);
//        console.println(ecstasy.collections.maps.simpleName);
//        console.println(ecstasy.collections.maps.qualifiedName);

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
