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
 */
module ecstasy.xtclang.org
    {
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
        }

    interface Interface
        {
        }
    
    interface Class
        {
        }

    interface Mixin
        {
        }
        
    interface Trait
        {
        }
        
    interface Value
            extends Comparable, Hashable
        {
        String to<String>();
        }
    
    interface Enum
        {
        }
    
    interface Service
        {
        }
    
    interface Method
        {
        String Name;
        }
    
    interface Property<T>
            extends Ref<T>
        {
        @ro String Name;

        @ro boolean Assigned;

        // TODO evaluate:
        // peek / poke?
        // CAS?
        }
        
    interface Type
        {
        }
    
    interface Ref
        {
        @ro Type<T> RefType
            {
            Type<T> get()
                {
                return T;
                }
            };

        @ro T get();

        void set(T value);
        }

    interface Element<T>
            impelments Ref<T>
        {
        }

    interface Variable<T>
            implements Ref<T>
        {
        @ro String Name;
        }
        
    class Object
        {
        Type to<Type>();
        }
    
    interface Number
        {
        @ro Sign Sign;

        @ro int BitCount;
        bit[] to<bit[]>();

        Integer to<Integer>();
        Int8 to<Int8>();
        Int16 to<Int16>();
        Int32 to<Int32>();
        Int64 to<Int64>();
        Int128 to<Int128>();
        
        /* maybe later
        UInt8 to<UInt8>();
        UInt16 to<UInt16>();
        UInt32 to<UInt32>();
        UInt64 to<UInt64>();
        UInt128 to<UInt128>();
        */
        
        /* maybe later?
        Float16 to<Float16>();
        */
        Float32 to<Float32>();
        Float64 to<Float64>();
        /* maybe later?
        Float128 to<Float128>();
        */
        
        Dec32 to<Dec32>();
        Dec64 to<Dec64>();
        Dec128 to<Dec128>();

        // operations
        Number opAdd(Number n);
        Number opSub(Number n);

        }
        
    interface Integer
            extends Number, OpAdd
        {
        Integer opAdd(Integer n);
        
        }
        
    value Int64 // TODO 8, 16, 24?, 32, 48?, 64, 128, 256?, ...?
            implements Integer
        {
        }
    
    value Byte // NOTE: is NOT an Integer
        {
        Integer
        }
        
    value Binary
        {
        // TODO
        }

    value Char
            extends String
        {
        Integer to<Integer>();
        Int8 to<Int8>();
        Int16 to<Int16>();
        Int32 to<Int32>();
        Int64 to<Int64>();
        }
    
    value String
        {
        Char[] to<Char[]>()
        }

    enum Nullable {Null};
    
    enum Boolean {False, True};

    enum Ordered (Lesser, Equal, Greater);

    enum Signum {Negative, Zero, Positive};
    
    // TODO Comparable, Hashable
    
    // TODO String
    // TODO Array

    /**
     * A Tuple is a container for an arbitrary number of elements, each of an arbitrary
     * type.
     */
    interface Tuple
            implements Value
        {
        @ro int ElementCount;
        Element[] as<Element[]>();
        }
    }
