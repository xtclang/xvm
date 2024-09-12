/**
 * A simple test executor.
 *
 * This test runner will find all tests in a module, and in classes
 * within that module and run them. A test is any method or function
 * that has the `@Test` annotation where the `@Test` annotation's `group`
 * is not `Omit`.
 *
 * The `--test` command line flag can be used to specify patterns to use as filters
 * to include or exclude specific tests. The value of the `--test` flag is an
 * expression or pair or expressions to match a class name and optionally a method
 * or function name. The two expressions are delimited by a hash sign ('#').
 */
const SimpleTestExecutor
    implements TestExecutor {

    import ecstasy.text.RegEx;

    /**
     * A type representing a method or a function.
     */
    typedef Method<Object, Tuple<>, Tuple<>> | Function<Tuple, Tuple> | Function<<>, <Object>> as MethodOrFunction;

    /**
     * A regular expression to match a dot.
     */
    private RegEx dot = new RegEx("[.]");

    /**
     * A regular expression to match an astrix.
     */
    private RegEx star = new RegEx("[*]");

    /**
     * A filter to match a test class and method name.
     *
     * @param classPattern   an optional `RegEx` to match a class name
     * @param methodPattern  an optional `RegEx` to match a method ro function name
     * @param include        `True` if this is an inclusion filter, or `False` if this
     *                       is an exclusion filter.
     */
    const TestFilter(RegEx? classPattern, RegEx? methodPattern, Boolean include) {
        /**
         * Test this filter against a `Class`.
         *
         * @param clz  the `Class` to test against this filter
         *
         * Return `True` if the `classPattern` is not `Null` and matches the class fully
         * qualified path.
         * If the `classPattern` is `Null`, return `True` if this is an inclusion filter,
         * otherwise return `False`.
         */
        Boolean matches(Class clz) = classPattern != Null ? classPattern.match(clz.path) : include;

        /**
         * Test this filter against a `Method` or `Function`.
         *
         * @param m  the `Method` or `Function` to test against this filter
         *
         * Return `True` if the `methodPattern` is not `Null` and matches the method or function name.
         * If the `methodPattern` is `Null`, return `True` if this is an inclusion filter, otherwise
         * return `False`.
         */
        Boolean matches(MethodOrFunction m) = methodPattern != Null ? methodPattern.match(m.name) : include;
    }

    /**
     * The `Console` to write output to.
     */
    @Inject Console console;

    /**
     * Discover and run `@Test` annotated methods in a `Module`.
     *
     * @param m  the `Module` to run tests in
     */
    @Override
    void runTests(Module m) {
        // An array of values passed in with the --test command line flag
        @Inject("xunit.test_patterns")
        String[] testPatterns;

        console.print($"Starting test execution for module {m.qualifiedName}");

        TestFilter[] includes = new Array();
        TestFilter[] excludes = new Array();

        for (String pattern : testPatterns) {
            Boolean exclude = pattern[0] == '!';
            if (exclude) {
                pattern = pattern[1 ..< pattern.size];
            }
            String[]   parts       = pattern.split('#', False, True);
            RegEx?     clzRegEx    = toRegEx(parts[0], m);
            RegEx?     methodRegEx = parts.size > 1 ? toRegEx(parts[1]) : Null;
            TestFilter filter      = new TestFilter(clzRegEx, methodRegEx, !exclude);

            if (exclude) {
                excludes.add(filter);
            } else {
                includes.add(filter);
            }
        }

        Class<Package> clz  = &m.actualClass.as(Class<Package>);
        (Int passed, Int failed) = runTests(clz, includes, excludes);
        console.print($"Completed test execution for module {m.qualifiedName}");
        console.print($"Passed: {passed}");
        console.print($"Failed: {failed}");
    }

    /**
     * Produce a test filter `RegEx` from a pattern string.
     *
     * @param pattern  the pattern to turn into a filter `RegEx`
     * @param m        an optional module containing the tests
     */
    RegEx? toRegEx(String pattern, Module? m = Null) {
        if (pattern.empty) {
            return Null;
        }

        String prefix;
        if (m.is(Module) && m.qualifiedName == pattern) {
            pattern = pattern + ":";
            prefix  = "";
        } else if (pattern.indexOf(':')) {
            prefix  = "";
        } else {
            prefix = "(.*):";
        }

        String regex = dot.replaceAll(pattern, "\\\\.");
        regex = prefix + star.replaceAll(regex, "(.*)");
        return new RegEx(regex);
    }

    /**
     * Find an execute all the `@Test` annotated methods in a class
     * and any sub-classes.
     *
     * @param clz  the class to discover and run tests in
     */
    (Int, Int) runTests(Class clz, TestFilter[] includes, TestFilter[] excludes) {
        Type type   = clz.toType();
        Int  passed = 0;
        Int  failed = 0;

        if (clz.is(Test) && clz.group == Test.Omit) {
            return passed, failed;
        }

        if (matches(clz, includes, excludes)) {
            MethodOrFunction[] testMethods = new Array();

            for (Function fn : type.functions) {
                if (fn.is(Test) && fn.group != Test.Omit && matches(fn, includes, excludes)) {
                    testMethods.add(fn);
                }
            }

            for (Method method : type.methods) {
                if (method.is(Test) && method.group != Test.Omit  && matches(method, includes, excludes)) {
                    testMethods.add(method);
                }
            }

            if (!testMethods.empty) {
                (Int p, Int f) = executeTest(clz, testMethods);
                passed += p;
                failed += f;
            }
        }

        for (Type childType : clz.toType().childTypes.values) {
            if (Class childClass := childType.fromClass()) {
                if (childClass.name == TypeSystem.MackKernel || childClass.name.startsWith("ecstasy.")) {
                    // do not go into Ecstasy classes
                    continue;
                }

                if (childType.isA(Enum)) {
                    // do not go into enums
                    continue;
                }

                if (childType.isA(Package)) {
                    if (Object o := childClass.isSingleton()) {
                        Package pkg = o.as(Package);
                        if (pkg.isModuleImport()) {
                            // skip module imports
                            continue;
                        }
                    }
                }

                (Int p, Int f) = runTests(childClass, includes, excludes);
                passed += p;
                failed += f;
            }
        }
        return passed, failed;
    }

    /**
     * Determine whether the specified `Class` should be included in the tests.
     *
     * If a `Class` fully qualified path matches a filter in the includes array, or the includes
     * filter array is empty, the `Class` is included, unless the `Class` also matches a pattern
     * in the excludes array.
     *
     * @param clz       the `Class` to test
     * @param includes  the test filters to match tests to be included
     * @param excludes  the test filters to match tests to be excluded
     */
    Boolean matches(Class clz, TestFilter[] includes, TestFilter[] excludes) {
        if (includes.empty && excludes.empty) {
            return True;
        }
        Boolean match = includes.empty;
        if (!match) {
            for (TestFilter filter : includes) {
                if (filter.matches(clz)) {
                    match = True;
                    break;
                }
            }
        }
        if (match && !excludes.empty) {
            for (TestFilter filter : excludes) {
                if (filter.matches(clz)) {
                    match = False;
                    break;
                }
            }
        }
        return match;
    }

    /**
     * Determine whether the specified `Method` or `Function` should be included in the tests.
     *
     * If a test matches a filter in the includes array, or the includes filter array is empty, the test is
     * included, unless the test name also matches a pattern in the excludes array.
     *
     * @param fn        the `Method` or `Function` to test
     * @param includes  the test filters to match tests to be included
     * @param excludes  the test filters to match tests to be excluded
     */
    Boolean matches(MethodOrFunction fn, TestFilter[] includes, TestFilter[] excludes) {
        if (includes.empty && excludes.empty) {
            return True;
        }
        Boolean match = includes.empty;
        if (!match) {
            for (TestFilter filter : includes) {
                if (filter.matches(fn)) {
                    match = True;
                    break;
                }
            }
        }
        if (match && !excludes.empty) {
            for (TestFilter filter : excludes) {
                if (filter.matches(fn)) {
                    match = False;
                    break;
                }
            }
        }
        return match;
    }

    /**
     * Execute the specified `@Test` methods in a class.
     *
     * @param clz          the class to run tests in
     * @param testMethods  the discovered tests to execute
     */
    (Int, Int) executeTest(Class clz, MethodOrFunction[] testMethods) {
        Object target;
        Int passed = 0;
        Int failed = 0;

        console.print($"  Executing tests in {clz}");

        if (Object o := clz.isSingleton()) {
            target = o;
        } else {
            if (Function<<>, <Object>> constructor := findConstructor(clz.toType())) {
                target = constructor();
            } else {
                console.print($"    FAILED: {clz} has no default constructor");
                return passed, 1;
            }
        }

        Tuple noArgs = Tuple:();
        for (MethodOrFunction test : testMethods) {
            try {
                if (test.is(Method<Object, Tuple<>, Tuple<>>)) {
                    test.bindTarget(target)();
                } else {
                    test();
                }
                passed++;
                console.print($"    Passed: {test} in {clz}");
            } catch (Exception e) {
                failed++;
                console.print($"    FAILED: {test} in {clz} failed: {e}");
            }
        }
        console.print($"  Executed tests in {clz} Passed={passed} Failed={failed}");
        return passed, failed;
    }

    /**
     * Find the default no-args constructor for a type.
     *
     * @param type  the `Type` to find the constructor for
     */
    conditional Function<<>, <Object>> findConstructor(Type type) {
        for (Function<Tuple, <>> c : type.constructors) {
            if (c.requiredParamCount == 0) {
                return True, c;
            }
        }
        return False;
    }
}