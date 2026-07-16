module TestSimple {

    @Inject Console console;

    package net import net.xtclang.org;

    import net.Network;
    import net.NameService.Record;

    void run() {
        scanClasses(xenia, "xenia.", new String[]);
    }

    void scanClasses(Package pkg, String prefix, String[] visitedModules) {
        Int counter = 0;
        for (Class clz : pkg.classes) {
            if (clz.annotatedBy(Abstract)) {
                console.print($"Skipping abstract {clz.name.quoted()}");
                continue;
            }

            if (Object innerPkg := clz.isSingleton(), innerPkg.is(Package)) {
                String name  = &innerPkg.class.name;
                String qname = prefix + name;
                if (Module imported := innerPkg.isModuleImport()) {
                    String moduleName = imported.qualifiedName;
                    if (visitedModules.contains(moduleName)) {
                        continue;
                    } else {
                        visitedModules += moduleName;
                        console.print($"*** looking into imported module {moduleName.quoted()}");
                        qname = imported.simpleName;
                    }
                }

                // this used to throw an exception when looking at "@Narrowable GenericMapping"
                // inside of the json module
                scanClasses(innerPkg, qname + '.', visitedModules);
            } else {
                counter++;
            }
        }
        console.print($"package {&pkg.class.name.quoted()} contains {counter} classes");
    }
}
