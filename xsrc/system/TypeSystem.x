import collections.ListMap;
import collections.HashSet;

import reflect.ClassTemplate;
import reflect.TypeTemplate;


/**
 * A TypeSystem is the result of linking a set of one or more modules together.
 *
 * Each Ecstasy container is created based on a TypeSystem.
 *
 * Modules in a type system are either shared from the parent container, or not shared. (Modules
 * that are shared will share the same singleton instances across the containers. If those modules
 * contain singleton services, then the mutable information represented by those services will be
 * shared across the containers.)
 */
const TypeSystem
    {
    /**
     * Construct a TypeSystem from an array of modules.
     *
     * @param modules  an array of modules
     * @param shared   (optional) an array of indicators of which modules are shared
     */
    construct(Module[] modules, Boolean[] shared=[])
        {
        assert modules.size > 0;
        if (!modules.is(immutable Object))
            {
            modules = modules.ensureImmutable();
            }

        HashSet<Module>         sharedModules         = new HashSet();
        ListMap<String, Module> moduleBySimpleName    = new ListMap();
        ListMap<String, Module> moduleByQualifiedName = new ListMap();
        Loop: for (Module _module : modules)
            {
            String  simpleName    = _module.simpleName;
            String  qualifiedName = _module.qualifiedName;

            assert moduleByQualifiedName.putIfAbsent(qualifiedName, _module);
            moduleBySimpleName.putIfAbsent(simpleName, _module);

            if (shared.size > Loop.count && shared[Loop.count]
                    || qualifiedName == MackKernel)     // the core Ecstasy module is always shared
                {
                sharedModules.add(_module);
                }
            }

        // make sure that the core Ecstasy module is always present, and always shared
        Module ecstasy = this:module;
        assert ecstasy.qualifiedName == MackKernel;
        if (moduleByQualifiedName.putIfAbsent(MackKernel, ecstasy))
            {
            modules = modules + ecstasy;
            sharedModules.add(ecstasy);
            }

        ListMap<Module, String> modulePaths = new ListMap();
        modulePaths.put(modules[0], "");
        for ((String path, Module _module) : modules[0].modulesByPath)
            {
            modulePaths.put(_module, path);
            }

        this.modules               = modules;
        this.modulePaths           = modulePaths;
        this.sharedModules         = sharedModules;
        this.moduleBySimpleName    = moduleBySimpleName;
        this.moduleByQualifiedName = moduleByQualifiedName;
        }

    /**
     * The qualified name of the core Ecstasy module. The core Ecstasy module is notable for two
     * unique reasons:
     *
     * * It has no dependencies on other modules;
     * * It is automatically present in every module.
     *
     * As the foundation module for the _Turtles Type System_, this module is known as the Mack
     * kernel, whose namesake is the bottom turtle in the technical documentation by Dr. Seuss.
     * (Apologies to Richard Rashid.)
     */
    static String MackKernel = "Ecstasy.xtclang.org";

    /**
     * The reserved package import name for the core Ecstasy module. This package name automatically
     * exists in the root of every module.
     */
    static String MackPackage = "ecstasy";

    /**
     * The modules that make up the type system.
     */
    Module[] modules;

    /**
     * The path of each module relative to the primary module of this type system; the path is
     * the shortest sequence of dot-delimited package names that leads to the module, with the
     * primary module having the empty path `""`.
     *
     * Modules that are not reachable by a path from the primary module are not present in this map.
     * This could occur if a module that is not depended upon is explicitly loaded as part of the
     * type system, for whatever reason. In such a case, the classes within the module would only be
     * identifiable using the explicit module qualification format, such as
     * `"Ecstasy.xtclang.org:collections.HashMap"`.
     */
    ListMap<Module, String> modulePaths;

    /**
     * The primary module is the module that is assumed to have defined the set of modules in the
     * type system, based on its module dependencies.
     *
     * Generally, all modules within the TypeSystem are imported as packages within the primary
     * module (or imported within modules that in turn are imported into the primary module), such
     * that they are within the namespace of the primary module. (The recursion of module importing
     * can be arbitrarily deep, and there is no particular limit on the complexity of the module
     * graph, or even on the number of times that any given module is imported into any other given
     * module.)
     */
    Module primaryModule.get()
        {
        return modules[0];
        }

    /**
     * A look-up of modules in the type system, by their qualified names, in the same order that
     * they appear in the [modules] array.
     */
    ListMap<String, Module> moduleByQualifiedName;

    /**
     * A look-up of modules in the type system, by their simple names, in the same order that
     * they appear in the [modules] array.
     *
     * This data structure may omit some of the modules, iff multiple modules have the same simple
     * name. If that occurs, then the order that the modules appear in the [modules] array dictates
     * the precedence of which module is represented in this data structure.
     */
    ListMap<String, Module> moduleBySimpleName;

    /**
     * Obtain a [Class] that exists within this `TypeSystem`, based on its class string.
     *
     * @return True iff the name identifies a `Class` that exists in this `TypeSystem`
     * @return (conditional) the specified `Class`
     */
    conditional Class classForName(String name)
        {
        // TODO conflict expected with combination of annotations and module spec (':')
        if (Int moduleSep := name.indexOf(':'))
            {
            String moduleName = name[0..moduleSep);
            if (Module _module := moduleByQualifiedName.get(moduleName))
                {
                return _module.classForName(name[moduleSep+1 .. name.size));
                }

            if (Module _module := moduleBySimpleName.get(moduleName))
                {
                return _module.classForName(name[moduleSep+1 .. name.size));
                }

            return false;
            }
        else
            {
            return primaryModule.classForName(name);
            }
        }

    /**
     * Obtain a [Type] that exists within this `TypeSystem`, based on its type string.
     *
     * @return True iff the name identifies a `Type` that exists in this `TypeSystem`
     * @return (conditional) the specified `Type`
     */
    conditional Type typeForName(String name)
        {
        // TODO this needs to handle '(..)', '?', '+', '-', '|', and the rest of the type syntax
        return primaryModule.typeForName(name);
        }

    /**
     * The modules in this type system that are shared with the type system of the parent container.
     */
    HashSet<Module> sharedModules;


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int size = "TypeSystem{ (primary)}".size + 2 * (moduleByQualifiedName.size - 1);
        Modules: for (Module _module : modules)
            {
            size += _module.qualifiedName.size;

            if (String path := modulePaths.get(_module), path.size > 0)
                {
                size += 6 + path.size;
                }

            if (sharedModules.contains(_module))
                {
                size += " (shared)".size;
                }
            }
        return size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add("TypeSystem{");

        Modules: for (Module _module : modules)
            {
            if (!Modules.first)
                {
                appender.add(", ");
                }

            appender.add(_module.qualifiedName);

            if (String path := modulePaths.get(_module), path.size > 0)
                {
                appender.add(" at \"")
                        .add(path)
                        .add('\"');
                }

            if (Modules.first)
                {
                appender.add(" (primary)");
                }

            if (sharedModules.contains(_module))
                {
                appender.add(" (shared)");
                }
            }

        appender.add('}');
        }
    }
