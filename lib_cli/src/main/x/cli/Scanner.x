/**
 * Helper methods that scan the TerminalApp for runnable commands.
 */
class Scanner {
    static Map<String, CmdInfo> buildCatalog(TerminalApp app, Object[] extras = []) {
        Map<String, CmdInfo> cmdInfos = new ListMap();

        scanCommands(() -> app, &app.class, cmdInfos);
        if (!extras.empty) {
            extras.forEach(extra ->
                scanCommands(() -> extra, &extra.class, cmdInfos));
        }
        scanClasses(app.classes, cmdInfos);
        return cmdInfos;
    }

    static void scanCommands(function Object() instance, Class clz, Map<String, CmdInfo> catalog) {
        Type type = clz.PublicType;

        for (Method method : type.methods) {
            if (method.is(Command)) {
                String cmd = method.cmd == "" ? method.name : method.cmd;
                if (catalog.contains(cmd)) {
                    throw new IllegalState($|A duplicate command "{cmd}" by the method "{method}"
                                            );
                }
                catalog.put(cmd, new CmdInfo(instance(), method));
            }
        }
    }

    static void scanClasses(Class[] classes, Map<String, CmdInfo> catalog) {
        static class Instance(Class clz)  {
            @Lazy Object get.calc() {
                if (Object single := clz.isSingleton()) {
                    return single;
                }
                Type type = clz.PublicType;
                if (function Object () constructor := type.defaultConstructor()) {
                    return constructor();
                }
                throw new IllegalState($|default constructor is missing for "{clz}"
                                      );
                }
            }

        for (Class clz : classes) {
            if (clz.annotatedBy(Abstract)) {
                continue;
            }

            if (clz.PublicType.isA(TerminalApp)) {
                // avoid recursion
                continue;
            }
            Class[] innerClasses = [];
            if (Object pkg := clz.isSingleton(), pkg.is(Package)) {
                if (pkg.isModuleImport()) {
                    // skip imported modules
                    continue;
                }
                innerClasses = pkg.classes;
            }

            Instance instance = new Instance(clz);
            scanCommands(() -> instance.get, clz, catalog);

            if (!innerClasses.empty) {
                scanClasses(innerClasses, catalog);
            }
        }
    }

    static class CmdInfo(Object target, Command method) {
        @Override
        String toString() = method.toString();
    }
}
