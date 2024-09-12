/**
 * This is a dummy XUnit Module that does not actually do anything.
 *
 * Once we have the real XUnit then this will be responsible for discovering
 * and executing all the tests in a module.
 */
module xunit.xtclang.org {

    /**
     * Execute tests in the specified `Module`.
     *
     * If the specified module, or one of the modules it directly imports, implements `TestExecutor`
     * that module will be responsible for discovering and executing tests, otherwise the default
     * test executor will be used.
     *
     * @param mod   the module containing tests to be executed
     */
    void test(Module mod) {
        TestExecutor ex = new SimpleTestExecutor();
        if (mod.is(TestExecutor)) {
            // The module being tested implements TestExecutor use it as the executor
            ex = mod;
        } else {
            // Search the module imports for any that implement TestExecutor
            Class<Package> clz = &mod.actualClass.as(Class<Package>);
            for (Type childType : clz.toType().childTypes.values) {
                if (Class childClass := childType.fromClass()) {
                    if (childType.isA(Package)) {
                        if (Object o := childClass.isSingleton()) {
                            Package pkg = o.as(Package);
                            if (pkg.isModuleImport()) {
                                if (pkg.is(TestExecutor)) {
                                    ex = pkg;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        ex.runTests(mod);
    }}
