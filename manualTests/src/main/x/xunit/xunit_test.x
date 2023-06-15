/**
 * The XUnit tests.
 */
module xunit_test.xtclang.org {
    package xunit import xunit.xtclang.org;

    @Inject Console console;

    void run() {
        console.print("XUnit tests");
        Class<Package> clz  = &this.actualClass.as(Class<Package>);
        (Int passed, Int failed) = runTests(clz);
        console.print("XUnit tests completed");
        console.print($"Passed: {passed}");
        console.print($"Failed: {failed}");
    }

    (Int, Int) runTests(Class clz) {
        Type type   = clz.toType();
        Int  passed = 0;
        Int  failed = 0;

        if (clz.is(Test)) {
            if (clz.group == Test.Omit) {
                return passed, failed;
            }
        }
        Test[] testMethods = new Array();

        for (Function fn : type.functions) {
            if (fn.is(Test) && fn.group != Test.Omit) {
                testMethods.add(fn);
            }
        }

        for (Method method : type.methods) {
            if (method.is(Test) && method.group != Test.Omit) {
                testMethods.add(method);
            }
        }

        if (!testMethods.empty) {
            (Int p, Int f) = executeTest(clz, testMethods);
            passed += p;
            failed += f;
        }

        for (Type childType : clz.toType().childTypes.values) {
            if (Class childClass := childType.fromClass()) {
                if (childType.isA(Package)) {
                    if (Object o := childClass.isSingleton()) {
                        Package pkg = o.as(Package);
                        if (pkg.isModuleImport()) {
                            // skip module imports
                            continue;
                        }
                        if (&pkg.actualClass.name == ("test_packages")) {
                            // skip test packages
                            continue;
                        }
                    }
                }

                (Int p, Int f) = runTests(childClass);
                passed += p;
                failed += f;
            }
        }
        return passed, failed;
    }

    (Int, Int) executeTest(Class clz, Test[] testMethods) {
        Object target;
        Int passed = 0;
        Int failed = 0;

        if (Object o := clz.isSingleton()) {
            target = o;
        } else {
            if (Function<<>, <Object>> constructor := findConstructor(clz.toType())) {
                target = constructor();
            } else {
                console.print($"FAILED: {clz} has no default constructor");
                return passed, 1;
            }
        }

        for (Test test : testMethods) {
            try {
                if (test.is(Method)) {
                    test.bindTarget(target)();
                } else if (test.is(Function)) {
                    test();
                }
                passed++;
            } catch (Exception e) {
                failed++;
                console.print($"FAILED: {test} in class {clz} failed: {e}");
            }
        }
        console.print($"Executed tests in {clz} Passed={passed} Failed={failed}");
        return passed, failed;
    }

    conditional Function<<>, <Object>> findConstructor(Type type) {
        for (Function<Tuple, <>> c : type.constructors) {
            if (c.requiredParamCount == 0) {
                return True, c;
            }
        }
        return False;
    }

}