/**
 * Represents an Ecstasy Package.
 */
interface Package
        extends immutable Const
    {
    /**
     * Test to see if this package represents a module import and if so, return it.
     *
     * @return True iff this package imports a module
     * @return (conditional) the [Module] that this package imports
     */
    conditional Module isModuleImport();

    /**
     * The classes contained immediately within this package.
     */
    @RO immutable Class[] classes.get()
        {
        return classByName.values.toArray(Constant).as(immutable Class[]);
        }

    /**
     * A mapping from simple name to class within this package.
     */
    @RO immutable Map<String, Class> classByName;


    // ----- Stringable methods ----------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return &this.actualClass.name.size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return &this.actualClass.name.appendTo(buf);
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends Package> Int64 hashCode(CompileType value)
        {
        return &value.actualClass.name.hashCode();
        }

    @Override
    static <CompileType extends Package> Boolean equals(CompileType value1, CompileType value2)
        {
        // two packages are equal only if they are the same Package object (the same instance)
        return &value1 == &value2;
        }
    }