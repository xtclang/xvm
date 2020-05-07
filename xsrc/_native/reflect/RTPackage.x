import ecstasy.collections.ListMap;

/**
 * This is the native runtime implementation of Package.
 */
const RTPackage
        implements Package
    {
    @Override @Lazy immutable Class[] classes.calc()
        {
        (String[] names, Class[] classes) = getChildNamesAndClasses();
        return classes.ensureImmutable(true);
        }

    @Override @Lazy immutable Map<String, Class> classByName.calc()
        {
        (String[] names, Class[] classes) = getChildNamesAndClasses();
        return new ListMap<String, Class>(names, classes).ensureImmutable(true);
        }

    @Override conditional Module isModuleImport() { TODO("native"); }
    (String[], Class[]) getChildNamesAndClasses() { TODO("native"); }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return &this.actualClass.name.size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        &this.actualClass.name.appendTo(appender);
        }
    }
