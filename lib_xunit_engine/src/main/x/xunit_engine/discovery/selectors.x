import xunit.MethodOrFunction;

/**
 * The `selectors` package contains `Selector` implementations for use
 * when discovering test models to execute.
 *
 * The use of selectors and selector resolvers allows a rich set of discovery mechanisms to
 * be used to discover tests. For example a single method can be executed by configuring
 * discovery with a single `MethodSelector`, or all tests in a package or module using a
 * `PackageSelector`, etc. Combinations of selectors can be configured to discover a set
 * of specific tests to execute. For example, an IDE might configure a test run with
 * selectors specifically to re-run a sub-set of just the failed tests from a previous run.
 */
package selectors {

    /**
     * `PackageOrName` is a type that is either a `Package` or a String representing a `Package` name.
     */
    typedef Package | String as PackageOrName;

    /**
     * `ClassOrName` is a type that is either a `Class` or a String representing a `Class` name.
     */
    typedef Class | String as ClassOrName;

    /**
     * `MethodOrFunctionOrName` is a type that is either a `MethodOrFunction` or a String
     * representing a `MethodOrFunction` name.
     */
    typedef MethodOrFunction | String as MethodOrFunctionOrName;

    /**
     * A test discovery `Selector` for selecting a specific `Package`.
     *
     * @param testPackage  the `Package` or fully qualified `Package` name to select.
     * @param allowImport  `True` to allow packages that are module imports
     */
    const PackageSelector(PackageOrName testPackage, Boolean allowImport = False)
            implements Selector {
    }

    /**
     * A test discovery `Selector` for selecting a specific `Class`.
     *
     * @param testClass  the `Class` or fully qualified `Class` name to select.
     */
    const ClassSelector(ClassOrName testClass)
            implements Selector {
    }

    /**
     * A test discovery `Selector` for selecting a specific method
     * or function.
     */
    const MethodSelector
            implements Selector {

        /**
         * Create a `MethodSelector`.
         *
         * @param name  the fully qualified method or function name
         */
        construct (String name) {
            this.testClass  = Null;
            this.testMethod = name;
        }

        /**
         * Create a `MethodSelector`.
         *
         * @param testClass   the `Class` the method or function is bound to
         * @param testMethod  the `Method` to select
         */
        construct (Class testClass, MethodOrFunction testMethod) {
            this.testClass  = testClass;
            this.testMethod = testMethod;
        }

        /**
         * The `Class` the method or function is bound to.
         */
        Class? testClass;

        /**
         * The `Method` or `Function` or fully qualified name.
         */
        MethodOrFunctionOrName testMethod;
    }

    // ----- factory methods -----------------------------------------------------------------------

    /**
     * Return an array of `Selector` instances for the children of the
     * specified `Class`.
     *
     * The `Selector`s returned will be for child classes, and test method
     * and functions in the class.
     *
     * @param clz  the `Class` to obtain the child selectors for
     *
     * @return an array of `Selector` instances for the class
     */
    Selector[] forChildren(Class clz) {
        if (clz.name == TypeSystem.MackPackage) {
            return [];
        }

        Selector[] selectors = new Array();
        Type       type      = clz.toType();
        selectors.addAll(forTypes(type.childTypes.values));
        for (Function fn : type.functions) {
            if (fn.is(Test) && !fn.as(Test).omitted()) {
                selectors.add(forMethod(clz, fn.as(MethodOrFunction)));
            }
        }
        for (Method method : type.methods) {
            if (method.is(Test) && !method.as(Test).omitted()) {
                selectors.add(forMethod(clz, method.as(MethodOrFunction)));
            }
        }
        return selectors;
    }

    /**
     * Return a `Selector` for each `Class` in a collection.
     *
     * @param classes  the array of `Class` instances to obtain `Selector`s for
     *
     * @return a `Selector` for each `Class` in a collection
     */
    Selector[] forClasses(Collection<Class> classes) {
        Selector[] selectors = new Array();
        for (Class clz : classes) {
            if (Selector selector := forClass(clz)) {
                selectors.add(selector);
            }
        }
        return selectors;
    }

    /**
     * Return a `Selector` for each `Type` in a collection.
     *
     * @param classes  the array of `Type` instances to obtain `Selector`s for
     *
     * @return a `Selector` for each `Type` in a collection
     */
    Selector[] forTypes(Collection<Type> types) {
        Selector[] selectors = new Array();
        for (Type type : types) {
            if (Class clz := type.fromClass()) {
                if (Selector selector := forClass(clz)) {
                    selectors.add(selector);
                }
            }
        }
        return selectors;
    }

    Selector[] forMethods(Class clz, MethodOrFunction[] testMethods) {
        Selector[] selectors = new Array();
        for (MethodOrFunction method : testMethods) {
            selectors.add(forMethod(clz, method));
        }
        return selectors;
    }

    Selector forMethod(Class clz, MethodOrFunction testMethod) {
        return new MethodSelector(clz, testMethod);
    }

    conditional Selector forPackage(Type type) {
        if (type.isA(Package)) {
            assert Class clz := type.fromClass();
            if (Object o := clz.isSingleton()) {
                return True, new PackageSelector(o.as(Package));
            }
        }
        return False;
    }

    conditional Selector forModule(Module mod) {
        if (&mod.actualClass.name == TypeSystem.MackPackage) {
            return False;
        }
        return True, new PackageSelector(mod, True);
    }

    conditional Selector forPackage(Package pkg) {
        if (&pkg.actualClass.name == TypeSystem.MackPackage || pkg.isModuleImport()) {
            return False;
        }
        return True, new PackageSelector(pkg);
    }

    conditional Selector forClass(Class clz) {
        Type type = clz.toType();
        if (type.isA(Package)) {
            return forPackage(type);
        }
        return True, new ClassSelector(clz);
    }
}