/**
 * This is the archetype Ecstasy module, the seed from which all Ecstasy code must derive,
 * and the foundation upon which all Ecstasy code builds. This module contains the Ecstasy
 * type system, defining each of the intrinsically supported data types, and the various
 * structures and capabilities of the Ecstasy type system and runtime environment.
 * Additionally, a number of useful data structures and algorithms are included to promote
 * the productivity of developers writing Ecstasy code.
 * <p>
 * All Ecstasy modules import this module automatically, as if they had the following line
 * of code:
 * <code>import ecstasy.xtclang.org as x;</code>
 * <p>
 * This module is fully and completely self-referential, containing no references to other
 * modules, and no link-time or runtime dependencies.
 *
 * @Copyright 2016-2017 xqiz.it
 */
module ecstasy.xtclang.org
    {
    enum Nullable {Null};
    enum Ordered(String symbol) {Lesser("<"), Equal("="), Greater(">")}

    typedef Nullable.Null           null;
    typedef Boolean.False           false;
    typedef Boolean.True            true;
    typedef UInt8                   Byte;
    typedef Int64                   Int;
    typedef UInt64                  UInt;
    typedef Decimal64               Dec;
    typedef ().Type                 Void;
    typedef annotations.AtomicRef   atomic;
    typedef annotations.Automagic   auto;
    typedef annotations.FutureRef   future;
    typedef annotations.LazyRef     lazy;
    typedef annotations.ReadOnly    ro;
    typedef annotations.SoftRef     soft;
    typedef annotations.WatchRef    watch;
    typedef annotations.WeakRef     weak;

    /**
     * Represents an Ecstasy Module, which is the outer-most level organizational unit for
     * source code, and the aggregate unit for compiled code distribution and deployment.
     * <p>
     * Because of its name, the Module type must be defined inside (textually included in)
     * the Ecstasy "module.x" file, for two reasons: (1) the Module.x file would conflict
     * with the Ecstasy "module.x" file that is in the same directory, and (2) the file
     * name "module.x" is reserved for defining the module itself, while in this case we
     * are defining the "Module" type.
     */
    interface Module
            extends Package
        {
        // TODO
        }

    /**
     * Represents an Ecstasy Package.
     * <p>
     * Because of its name, the Package type must be defined inside (textually included
     * in) the Ecstasy "module.x" file, because the file name "package.x" is reserved for
     * defining the package itself, while in this case we are defining the "Package" type.
     */
    interface Package
            extends Class
        {
        // TODO
        }

// TODO -------------- split out everything below this point -----------------

    interface AutoConst
        {
        @const AutoConst ensureConst();
        }
    interface AutoService
        {
        @service AutoService ensureService();
        }

    interface AutoMutable
        {
        AutoMutable ensureMutable();
        }

    interface AutoPersistent
        {
        AutoPersistent ensurePersistent();
        }


//

    interface Class
        {
        }

    interface Mixin
        {
        }

    interface Trait
        {
        }

    interface Enum
        {
        }

    value Binary
        {
        // TODO
        }

    interface Map<KeyType, ValueType>
            extends Indexed<KeyType>
    }
