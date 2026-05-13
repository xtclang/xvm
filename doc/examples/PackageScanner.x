/**
 * An example of module/package scanner.
 */
module PackageScanner {

    package jsondb import jsondb.xtclang.org;

    @Inject Console console;

    void run() {
        console.print($"\n*** jsondb");
        scanClasses(jsondb, "jsondb.");
    }

    /**
     * @return the number of "proper" classes in the package
     */
    Int scanClasses(Package pkg, String prefix) {
        Int counter = 0;
        for (Class clz : pkg.classes) {
            if (clz.annotatedBy(Abstract)) {
                console.print($"Skipping abstract {clz.name.quoted()}");
                continue;
            }

            if (Object innerPkg := clz.isSingleton(), innerPkg.is(Package)) {
                String name  = &innerPkg.class.name;
                String qname = prefix + name;
                if (innerPkg.isModuleImport()) {
                    console.print($"Skipping imported module {qname.quoted()}");
                    continue;
                }

                Int innerCounter = scanClasses(innerPkg, prefix + name + '.');

                console.print($"package {qname.quoted()} contains {innerCounter} classes");
            } else {
                counter++;
            }
        }
        return counter;
    }
}

