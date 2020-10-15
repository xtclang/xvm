module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    void run()
        {
        reportModule(this:module);

        reportPackage(ecstasy);
        reportPackage(json);
        reportPackage(ecstasy.reflect);
        reportPackage(json.mapping);
        }

    void reportModule(Module m)
        {
        console.println($"module {m} version={m.version}");
        }

    void reportPackage(Package p)
        {
        if (Module m := p.isModuleImport())
            {
            console.println($"import {m} version={m.version}");
            }
        else
            {
            console.println($"regular package {p}");
            }

        }
    }
