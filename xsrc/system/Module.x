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

    // REVIEW: consider "const" instead of "value"
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

    /**
     *
     */
    interface UniformIndexed<IndexType, ElementType>
        {
        ElementType get(IndexType index)
            {
            return elementAt(index).get();
            }
        Void set(IndexType index, ElementType newValue)
            {
            elementAt(index).set(newValue);
            }
        Ref<ElementType> elementAt(IndexType index);
        }

    /**
     * An array is a container of elements of a particular type.
     */
    interface Array<ElementType>
            implements UniformIndexed<Int, ElementType>
        {
        @ro int ElementCount; // REVIEW or just "Size"? "Length"? "Count"?

        }

    // REVIEW this is roughly what I'm thinking as the basis for Tuple
    // NOTE that Tuple == Struct in many ways
    // - Tuple has (optionally) named elements; Struct has named elements
    // - Tuple has int-indexed elements; Struct _could_ be accessed in the same way
    //   -> probably have Struct.to<Tuple<ElementType>>(); instead

    interface Field<RefType>
            implements Ref<RefType> // REVIEW does it (could it) derive from property?
        {
        String Name;
        }

    /**
     * A Tuple is a container for an arbitrary number of elements, each of an arbitrary
     * type.
     * <p>
     * The Tuple interface is what the "NonUniformIndexed" interface would look like.
     */
    interface Tuple<FieldType...>
        {
        /**
         * The number of Fields in the Tuple.
         */
        @ro Int FieldCount;

        /**
         * Obtain the value in the specified field of the Tuple.
         */
        FieldType[index] get(Int index);

        /**
         * Modify the value in the specified field of the Tuple.
         */
        void set(Int index, FieldType[index] newValue);

        /**
         * Obtain the Fields of this Tuple as an Array. Note that the data types of the
         * fields can vary, so the FieldType of each Field may differ.
         */
        Field[] to<Field[]>();

        // REVIEW - basically this says we can convert any tuple to a "struct" at runtime
        /**
         * Obtain a Struct that represents the contents of this Tuple.
         * <p>
         * <li>The Struct is immutable iff this Tuple is immutable.</li>
         * <li>The Struct is read-only iff this Tuple is read-only.</li>
         */
        Struct to<Struct>();

        // REVIEW does this make sense? can you use this as a "return type" of "Void"?
        singleton value Void
                implements Tuple<>
            {
            Int FieldCount
                {
                Int get()
                    {
                    return 0;
                    }
                }

            // ...
            }
        }

    /**
     * A Struct is a simple container of Field objects. Each Field is represented by a
     * property of the Struct.
     * TODO: Explain what that means
     */
    interface Struct
        {
        /**
         * Obtain a Tuple that represents the contents of this Struct.
         * <p>
         * <li>The Tuple is immutable iff this Struct is immutable.</li>
         * <li>The Tuple is read-only iff this Struct is read-only.</li>
         */
        Tuple to<Tuple>();

        /**
         * Obtain an Array that represents the contents of this Struct.
         * TODO define mutability guarantees
         */
        Field[] to<Field[]>();
        }

    interface Map<KeyType, ValueType>
            implements Indexed<KeyType>
    }
