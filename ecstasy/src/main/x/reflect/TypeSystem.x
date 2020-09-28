import reflect.ClassTemplate;
import reflect.InvalidType;
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
            assert modules.is(Freezable);
            modules = modules.freeze();
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
        this.modulePaths           = modulePaths          .freeze(True);
        this.sharedModules         = sharedModules        .freeze(True);
        this.moduleBySimpleName    = moduleBySimpleName   .freeze(True);
        this.moduleByQualifiedName = moduleByQualifiedName.freeze(True);
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
    static String MackKernel = "ecstasy.xtclang.org";

    /**
     * The reserved package import name for the core Ecstasy module. This package name automatically
     * exists in the root of every module.
     */
    static String MackPackage = "ecstasy";

    /**
     * The implicitly-imported types from the Ecstasy core library.
     */
    static Map<String, Type> implicitTypes =
        {
        import lang.src.Lexer.Token;
        import lang.src.Parser;
        import lang.src.ast.ImportStatement;

        String                source     = $/implicit.x;
        Parser                parser     = new Parser(source);
        // TODO GG - this should not have compiled (accessing non-static field from static context)
        // assert Module         mackModule := moduleByQualifiedName.get(MackKernel);
        Module                mackModule = this:module;
        ListMap<String, Type> implicits  = new ListMap();
        NextImport: while (!parser.eof)
            {
            ImportStatement stmt = parser.parseImportStatement();
            Module?         mod  = mackModule;
            Type            type = &mod.actualType;
            for (Token name : stmt.names)
                {
                assert type := type.childTypes.get(name.valueText);
                }
            assert Class clz := type.fromClass();
            implicits.put(stmt.aliasName, clz);
            }

        return implicits.makeImmutable();
        };

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
     * `"ecstasy.xtclang.org:collections.HashMap"`.
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
     * @param name            the class name to obtain, which may include annotations, type
     *                        parameters, and other valid class identifier syntax
     * @param hideExceptions  pass True to catch type exceptions and return them as `False` instead
     *
     * @return True iff the name identifies a `Class` that exists in this `TypeSystem`
     * @return (conditional) the specified `Class`
     *
     * @throws InvalidType  if a type exception occurs and `hideExceptions` is not specified
     */
    conditional Class classForName(String name, Boolean hideExceptions = False)
        {
        // delegate to the typeForName() implementation
        if (Type type := typeForName(name, hideExceptions))
            {
            return type.fromClass();
            }

        return False;
        }

    /**
     * Obtain a [Type] that exists within this `TypeSystem`, based on its type string.
     *
     * @param name            the type name to obtain, which may include annotations, type
     *                        parameters, relational operators, and other valid type syntax
     * @param hideExceptions  pass True to catch type exceptions and return them as `False` instead
     *
     * @return True iff the name identifies a `Type` that exists in this `TypeSystem`
     * @return (conditional) the specified `Type`
     *
     * @throws InvalidType  if a type exception occurs and `hideExceptions` is not specified
     */
    conditional Type typeForName(String name, Boolean hideExceptions = False)
        {
        if (name == "")
            {
            return True, &primaryModule.actualType;
            }

        if (CacheEntry entry ?= lookupCache[name])
            {
            if (Type type ?= entry.type)
                {
                return True, type;
                }

            if (!hideExceptions, String message ?= entry.failure)
                {
                throw new InvalidType(message);
                }

            return False;
            }

        import lang.src.Parser;
        import lang.src.ast.TypeExpression;

        Parser         parser    = new Parser(name, allowModuleNames=True);
        Exception?     exception = Null;
        String?        failure   = Null;
        TypeExpression typeExpr;
        try
            {
            typeExpr = parser.parseTypeExpression();

            // if the parser left something unparsed, then the type name is not valid
            if (!parser.eof)
                {
                failure = $"Type name contains unparsable element(s): {name.quoted()}";
                }
            else
                {
                if (Type result := typeExpr.resolveType(this))
                    {
                    lookupCache[name] = new CacheEntry(result);
                    return True, result;
                    }
                }
            }
        catch (Exception e)
            {
            exception = e;
            }

        if (failure == Null && exception != Null)
            {
            failure = &exception.actualClass.name;
            if (exception.text != Null)
                {
                failure += ": " + exception.text;
                }
            }

        lookupCache[name] = new CacheEntry(failure = failure);

        if (!hideExceptions && failure != Null)
            {
            throw exception.is(InvalidType)
                    ? exception
                    : new InvalidType(failure, cause = exception);
            }

        return False;
        }

    /**
     * A lazily instantiated cache for looking up type information by name.
     */
    private @Lazy LookupCache lookupCache.calc()
        {
        return new LookupCache();
        }

    /**
     * A lightweight caching service implementation for looking up type information by name.
     */
    private service LookupCache
        {
        private HashMap<String, CacheEntry> cache = new HashMap();

        @Op("[]") CacheEntry? getElement(String typeName)
            {
            return cache.getOrNull(typeName);
            }

        @Op("[]=") void setElement(String typeName, CacheEntry entry)
            {
            cache.putIfAbsent(typeName, entry);
            }
        }

    /**
     * A representation of a result from looking up a type by name.
     */
    private static const CacheEntry(Type? type = Null, String? failure = Null);

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
    Appender<Char> appendTo(Appender<Char> buf)
        {
        buf.addAll("TypeSystem{");

        Modules: for (Module _module : modules)
            {
            if (!Modules.first)
                {
                buf.addAll(", ");
                }

            buf.addAll(_module.qualifiedName);

            if (String path := modulePaths.get(_module), path.size > 0)
                {
                buf.addAll(" at \"")
                   .addAll(path)
                   .add('\"');
                }

            if (Modules.first)
                {
                buf.addAll(" (primary)");
                }

            if (sharedModules.contains(_module))
                {
                buf.addAll(" (shared)");
                }
            }

        return buf.add('}');
        }
    }
