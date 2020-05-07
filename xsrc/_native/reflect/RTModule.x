import ecstasy.collections.ListMap;

/**
 * This is the native runtime implementation of Module.
 */
const RTModule
        extends RTPackage
        implements Module
    {
    @Override String simpleName                    .get() { TODO("native"); }
    @Override String qualifiedName                 .get() { TODO("native"); }
    @Override Version? version                     .get() { TODO("native"); }

    @Override conditional Class classForName(String name) { TODO("native"); }

    // internal
    (String[], Module[]) getModuleDependencies()          { TODO("native"); }

    @Override @Lazy immutable Map<String, Module> modulesByName.calc()
        {
        (String[] names, Module[] modules) = getModuleDependencies();
        return new ListMap<String, Module>(names, modules).ensureImmutable(true);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return qualifiedName.size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        qualifiedName.appendTo(appender);
        }
    }
