module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import ecstasy.reflect.*;

    import json.Schema;

    void run()
        {
        testTemplate(TestSimple);
        testTemplate(Schema);

        testTypeSystem();
        }

    void testTemplate(Class clz)
        {
        ClassTemplate  ct = clz.baseTemplate;
        ModuleTemplate mt = ct.containingModule;

        // this used to show "_native.xtclang.org" and its dependencies
        console.println(mt.parent.moduleNames);
        }

    void testTypeSystem()
        {
        console.println("\n** testTypeSystem");

        TypeSystem ts = this:service.typeSystem;
        console.println($"current TypeSystem={ts}");
        console.println($"modules              : {ts.modules              }");
        console.println($"sharedModules        : {ts.sharedModules        }");
        console.println($"moduleBySimpleName   : {ts.moduleBySimpleName   }");
        console.println($"moduleByQualifiedName: {ts.moduleByQualifiedName}");

        console.println("modules:");
        for (Module _module : ts.modules)
            {
            displayModule(_module);
            }

        String[] names =
                [
                "json.Schema",
                ];

        for (String name : names)
            {
            try
                {
                if (Class clz := ts.classForName(name))
                    {
                    console.println($"class for \"{name}\"={clz}");
                    }
                else
                    {
                    console.println($"no such class: \"{name}\"");
                    }
                }
            catch (Exception e)
                {
                console.println($"exception occurred lookup up class \"{name}\"; exception={e}");
                }
            }
        }

    void displayModule(Module _module)
        {
        console.println($"module \"{_module.simpleName}\" (\"{_module.qualifiedName}\")");
        val deps = _module.modulesByPath;
        if (!deps.empty)
            {
            console.println($" - dependencies:");
            for ((String path, Module dep) : deps)
                {
                console.println($"    - \"{path}\" => \"{dep.qualifiedName}\"");
                }
            }
        }
    }