import collections.ListMap;
import collections.HashSet;
import reflect.ClassTemplate;


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
                    || qualifiedName == CORE)           // the core Ecstasy module is always shared
                {
                sharedModules.add(_module);
                }
            }

        // make sure that the core Ecstasy module is always present, and always shared
        Module ecstasy = this:module;
        assert ecstasy.qualifiedName == CORE;
        if (moduleByQualifiedName.putIfAbsent(CORE, ecstasy))
            {
            modules = modules + ecstasy;
            sharedModules.add(ecstasy);
            }

        this.modules                = modules;
        this.sharedModules          = sharedModules;
        this.moduleBySimpleName     = moduleBySimpleName;
        this.moduleByQualifiedName  = moduleByQualifiedName;
        }

    /**
     * The qualified name of the core Ecstasy module.
     */
    static String CORE = "Ecstasy.xtclang.org";

    /**
     * The modules that make up the type system.
     */
    Module[] modules;

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
     * Obtain a [ClassTemplate] that is loaded within this `TypeSystem`, based on its qualified
     * name.
     *
     * @return True iff the name identifies a `ClassTemplate`
     * @return (conditional) the specified `ClassTemplate`
     */
    conditional ClassTemplate templateForName(String name)
        {
        TODO
        }

    /**
     * Obtain a [Class] that exists within this `TypeSystem`, based on its class string.
     *
     * @return True iff the name identifies a `Class` that exists in this `TypeSystem`
     * @return (conditional) the specified `Class`
     */
    conditional Class classForName(String name)
        {
        TODO
        }

    /**
     * Obtain a [Type] that exists within this `TypeSystem`, based on its type string.
     *
     * @return True iff the name identifies a `Type` that exists in this `TypeSystem`
     * @return (conditional) the specified `Type`
     */
    conditional Type typeForName(String name)
        {
        TODO
        }

    /**
     * The modules in this type system that are shared with the type system of the parent container.
     */
    HashSet<Module> sharedModules;

    /**
     * True iff the module contains at least one singleton service.
     */
    @Lazy Boolean containsSingletonServices.calc()
        {
        return modules.iterator().untilAny(m -> m.containsSingletonServices);
        }
    }


