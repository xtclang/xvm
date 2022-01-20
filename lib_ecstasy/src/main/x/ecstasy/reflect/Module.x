/**
 * Represents an Ecstasy Module, which is the outer-most level organizational unit for
 * source code, and the aggregate unit for compiled code distribution and deployment.
 */
interface Module
        extends Package
    {
    /**
     * The simple qualified name of the module, such as "ecstasy".
     */
    @RO String simpleName.get()
        {
        return qualifiedName.split('.')[0];
        }

    /**
     * The fully qualified name of the module, such as "ecstasy.xtclang.org".
     */
    @RO String qualifiedName.get()
        {
        return &this.actualClass.name;
        }

    /**
     * The version of the module.
     */
    @RO Version version;

    /**
     * The modules that this module depends on by linkage, both directly and indirectly. For
     * each such module that this module is linked to, and that is also visible within this
     * module's namespace, the shortest dot-delimited path and the depended-upon module will
     * be present in the map. In the case of an unlinked, optional module (one that is specified
     * as a "desired" or "optional" import, but was not loaded and linked with this module for
     * whatever reason), no entry will be present in the map.
     */
    @RO immutable Map<String, Module!> modulesByPath;

    /**
     * Given the qualified name of a class nested within this module, obtain the [Class].
     *
     * @return True iff the name identified a class
     * @return (conditional) the specified [Class]
     */
    conditional Class classForName(String name);

    /**
     * Given the qualified name of a type nested within this module, obtain the [Type].
     *
     * @return True iff the name identifies a type
     * @return (conditional) the specified [Type]
     */
    conditional Type typeForName(String name);


    // ----- Stringable methods ----------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return qualifiedName.size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return qualifiedName.appendTo(buf);
        }
    }
