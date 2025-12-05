import ecstasy.reflect.Annotation;

import xunit.MethodOrFunction;

import xunit.templates.TestTemplate;
import xunit.templates.TestTemplateFactory;

/**
 * The `selectors` package contains `Selector` implementations for use when discovering test models
 * to execute.
 *
 * The use of selectors and selector resolvers allows a rich set of discovery mechanisms to be used
 * to discover tests. For example a single method can be executed by configuring discovery with a
 * single `MethodSelector`, or all tests in a package or module using a `PackageSelector`, etc.
 * Combinations of selectors can be configured to discover a set of specific tests to execute. For
 * example, an IDE might configure a test run with selectors specifically to re-run a sub-set of
 * just the failed tests from a previous run.
 */
package selectors {

    /**
     * `PackageOrName` is a type that is either a `Package` or a String representing a `Package`
     *  name.
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
     * A test discovery `Selector` for selecting a specific method or function.
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

    /**
     * A test discovery `Selector` for selecting a specific test template.
     *
     * @param name       the name of the test template
     * @param iteration  an optional test template iteration to select
     */
    const TemplateSelector(Class             testClass,
                           MethodOrFunction? testMethod,
                           Annotation        templateAnnotation,
                           Annotation[]      parents   = [],
                           Int?              iteration = Null)
            implements Selector {
    }

    // ----- factory methods -----------------------------------------------------------------------

    /**
     * Return an immutable array of `Selector` instances for the children of the specified `Class`.
     *
     * The `Selector`s returned will be for child classes, and test method and functions in the
     * class.
     *
     * @param clz  the `Class` to obtain the child selectors for
     *
     * @return an immutable array of `Selector` instances for the class
     */
    Selector[] forChildren(Class clz) {
        if (clz.name == TypeSystem.MackPackage) {
            return [];
        }

        Selector[] selectors = new Array();
        Type       type      = clz.toType();
        selectors.addAll(forClasses(childClasses(clz)));

        for (Function fn : type.functions) {
            if (fn.is(Test), fn.group != Test.Omit) {
                selectors.addAll(forMethod(clz, fn.as(MethodOrFunction)));
            }
        }
        for (Method method : type.methods) {
            if (method.is(Test), method.group != Test.Omit) {
                selectors.addAll(forMethod(clz, method.as(MethodOrFunction)));
            }
        }
        return selectors.freeze(True);
    }

    /**
     * Return an immutable array of Class instances for all the child classes of a specified class,
     * where the child class belongs to the same module as the specified class.
     *
     * @param clz  the Class to find the child classes from
     *
     * @return  an array of Class instances representing the child classes of the specified class
     */
    private Class[] childClasses(Class clz) {
        String path = clz.path;
        assert Int colon := path.indexOf(':');
        String moduleName = path[0 ..< colon];
        Class[] children = new Array();

        for (Class child : clz.childClasses) {
            Type t = child.toType();
            if (child.path.startsWith(moduleName)) {
                children.add(child);
            }
        }
        return children.freeze(True);
    }

    /**
     * Return an array of `Selector` instances for each `Class` in a collection.
     *
     * @param classes  the array of `Class` instances to obtain `Selector`s for
     *
     * @return an `Selector` instances, one for each `Class` in a collection
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
     * @param types  the array of `Type` instances to obtain `Selector`s for
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

    /**
     * Create a `Selector` for a method or function within a class.
     *
     * @param clz     the class to use to create the selector
     * @param method  the method or function to use to create the selector
     *
     * @return a selector for the specified class and method
     */
    Selector[] forMethod(Class clz, MethodOrFunction method) {
        Selector[] selectors = new Array();
        selectors.add(new MethodSelector(clz, method));
        selectors.addAll(templatesFor(clz, method));
        return selectors;
    }

    Selector[] templatesFor(Class testClass, MethodOrFunction? method = Null) {
        Class clz;
        if (method.is(MethodOrFunction)) {
            clz = &method.class;
        } else {
            clz = testClass;
        }

        if (clz.PublicType.isA(TestTemplate)) {
            Selector[]   selectors     = new Array();
            (_, Annotation[] annotations) = clz.deannotate();
            Annotation[] parents = new Array();
            for (Annotation an : annotations.filter(a -> a.annoClass.extends(TestTemplate))) {
                selectors.add(new TemplateSelector(testClass, method, an, parents=parents));
                parents.add(an);
            }

            return selectors;
        }
        return [];
    }

    /**
     * Create an array of `Selector` instances for all methods or functions within a class that
     * match the specified name.
     *
     * @param clz   the class to use to create the selector
     * @param name  the method or function name to use to create the selectors
     *
     * @return the array of selectors for the matching methods or functions
     */
     Selector[] forMethod(Class clz, String name) {
        Type       type      = clz.toType();
        Selector[] selectors = new Array();

        for (Function fn : type.functions) {
            if (fn.is(Test), fn.group != Test.Omit, fn.name == name) {
                selectors.addAll(forMethod(clz, fn.as(MethodOrFunction)));
            }
        }
        for (Method method : type.methods) {
            if (method.is(Test), method.group != Test.Omit, method.name == name) {
                selectors.addAll(forMethod(clz, method.as(MethodOrFunction)));
            }
        }
        return selectors;
    }

    /**
     * Create a selector for a module.
     *
     * @param clz  the `Module` to use to create the selector
     *
     * @return True iff the specified class is a package that is not the core Ecstasy module
     * @return a selector for the specified module
     */
    conditional Selector forModule(Module mod) {
        if (&mod.class.name == TypeSystem.MackPackage) {
            return False;
        }
        return True, new PackageSelector(mod, True);
    }

    /**
     * Create a selector for a package.
     *
     * @param clz  the `Class` to use to create the selector
     *
     * @return True iff the specified class is a package
     * @return a selector for the specified package
     */
    conditional Selector forPackage(Class clz) {
        return forPackage(clz.toType());
    }

    /**
     * Create a selector for a package.
     *
     * @param clz  the `Type` to use to create the selector
     *
     * @return True iff the specified type is a package
     * @return a selector for the specified package
     */
    conditional Selector forPackage(Type type) {
        if (type.isA(Package)) {
            assert Class clz := type.fromClass();
            if (Object o := clz.isSingleton()) {
                return True, new PackageSelector(o.as(Package));
            }
        }
        return False;
    }

    /**
     * Create a selector for a package.
     *
     * @param clz  the `Package` to use to create the selector
     *
     * @return True iff the specified class is a package and not the core Ecstasy package
     * @return a selector for the specified package
     */
    conditional Selector forPackage(Package pkg) {
        if (&pkg.class.name == TypeSystem.MackPackage || pkg.isModuleImport()) {
            return False;
        }
        return True, new PackageSelector(pkg);
    }

    /**
     * Create a selector for a class.
     *
     * @param clz  the `Class` to use to create the selector
     *
     * @return True iff the specified class is a package and not the core Ecstasy package
     * @return a selector for the specified class
     */
    conditional Selector forClass(Class clz) {
        Type type = clz.toType();
        if (type.isA(Package)) {
            return forPackage(type);
        }
        return True, new ClassSelector(clz);
    }
}