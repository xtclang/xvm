/**
 * The XUnit tests.
 */
module xunit_test.xtclang.org {
    package xunit import xunit.xtclang.org;
    package xunit_engine import xunit_engine.xtclang.org;
    package collections import collections.xtclang.org;

    import xunit.ExtensionProvider;
    import xunit.MethodOrFunction;
    
    import xunit.annotations.AfterAll;
    import xunit.annotations.AfterEach;
    import xunit.annotations.BeforeAll;
    import xunit.annotations.BeforeEach;

    /**
     * A marker to indicate to the code below that the annotated
     * thing is not a real test class, method etc and should not
     * be executed as part of the XUnit tests.
     */
    mixin MockTest
        into Class | Method | Function {
    }

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

        if (clz.is(MockTest)) {
            return passed, failed;
        }

        BeforeEach[] beforeMethods = new Array();
        AfterEach[] afterMethods = new Array();
        Test[]      testMethods = new Array();

        for (Function fn : type.functions) {
            switch (fn.is(_)) {
            case BeforeEach:
                beforeMethods.add(fn);
                break;
            case AfterEach:
                afterMethods.add(fn);
                break;
            case Test:
                testMethods.add(fn);
                break;
            }
        }

        for (Method method : type.methods) {
            switch (method.is(_)) {
            case BeforeEach:
                beforeMethods.add(method);
                break;
            case AfterEach:
                afterMethods.add(method);
                break;
            case Test:
                testMethods.add(method);
                break;
            }
        }

        if (!testMethods.empty) {
            (Int p, Int f) = executeTest(clz, testMethods, beforeMethods, afterMethods);
            passed += p;
            failed += f;
        }

        for (Type childType : clz.toType().childTypes.values) {
            if (Class childClass := childType.fromClass()) {
                if (childClass.is(MockTest)) {
                    continue;
                }
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

                if (childClass.path.startsWith("xunit_test")) {
                    (Int p, Int f) = runTests(childClass);
                    passed += p;
                    failed += f;
                }

            }
        }
        return passed, failed;
    }

    (Int, Int) executeTest(Class clz, Test[] testMethods, BeforeEach[] beforeMethods, AfterEach[] afterMethods) {
        Object target;
        Int passed = 0;
        Int failed = 0;

        if (clz.is(MockTest))
            {
            return passed, failed;
            }

        console.print($"Executing tests in {clz}");

        if (Object o := clz.isSingleton()) {
            target = o;
        } else {
            if (Function<<>, <Object>> constructor := findConstructor(clz.toType())) {
                target = constructor();
            } else {
                console.print($"  FAILED: {clz} has no default constructor");
                return passed, 1;
            }
        }

        for (Test test : testMethods) {
            if (test.is(MockTest)) {
                continue;
            }
            Boolean continueTest = True;
            for (BeforeEach before : beforeMethods) {
                try {
                    if (before.is(Method)) {
                        before.bindTarget(target)();
                    } else if (before.is(Function)) {
                        before();
                    }
                } catch (Exception e) {
                    failed++;
                    console.print($"  FAILED: BeforeEach {before} in class {clz} failed: {e}");
                    continueTest = False;
                    break;
                }
            }

            if (continueTest) {
                try {
                    if (test.is(Method)) {
                        test.bindTarget(target)();
                    } else if (test.is(Function)) {
                        test();
                    }
                    passed++;
                    console.print($"  Passed: {test} in class {clz}");
                } catch (Exception e) {
                    failed++;
                    console.print($"  FAILED: {test} in class {clz} failed: {e}");
                }
                for (AfterEach after : afterMethods) {
                    try {
                        if (after.is(Method)) {
                            after.bindTarget(target)();
                        } else if (after.is(Function)) {
                            after();
                        }
                    } catch (Exception e) {
                        failed++;
                        console.print($"  FAILED: AfterEach {after} in class {clz} failed: {e}");
                    }
                }
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

    ExtensionProvider[] getProviders(Class clz, MethodOrFunction method) {
        return getProviders(clz, [method]);
    }

    ExtensionProvider[] getProviders(Class clz, MethodOrFunction[] methods) {
        Collection<String> names = methods.map(m -> m.name);
        return xunit_engine.utils.findExtensions(clz)
            .filter(p -> names.contains(p.name))
            .toArray();
    }

}