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
    interface Ref<RefType>
        {
        /**
         * De-reference the reference to obtain the referent.
         */
        RefType get();

        /**
         * Specify the referent for this reference.
         */
        void set(RefType value);

        /**
         * Obtain the actual runtime type of the reference that this Ref currently
         * holds. The ActualType represents the full set of methods that can be
         * invoked against the referent, and is always a super-set of the RefType.
         * (The RefType denotes the constraint of the reference, i.e. the reference
         * must "be of" the RefType, but is not limited to only having the methods of
         * the RefType; the RefType is often the <i>compile-time type</i> of the
         * reference.)
         */
        @ro Type ActualType;

        /**
         * Narrow the reference so that it contains only the methods in the specified
         * type. This strips the runtime reference of any methods that are not present
         * in the specified type.
         */
        SomeType as<Type SomeType>();

        // TODO equality of the "reference" (referring to the same thing?)

        // TODO type of the referent
        }

    interface Type
        {
        // TODO methods
        // TODO properties
        }

    /**
     * This class represents the capabilities that are common to every Ecstasy object;
     * it is the single inheritance root for the Ecstasy type system.
     */
    class Object
        {
        /**
         * Convert this object to an object of another specified type.
         */
        SomeType to<SomeType>();

        String to<String>()
            {
            // TODO implement a rudimentary to<String>?
            }

        @ro protected Meta meta;
        }

    class Meta
        {
        boolean immutable;

        /**
        * The containing module.
        */
        Module module;

        Package package;

        TypeComposition class;
        }

    // -----

    /**
     * The Number interface represents the properties and operations available on every
     * numeric type included in Ecstasy.
     */
    interface Number
        {
        // ----- properties

        /**
         * The number of bits that the number uses.
         */
        @ro Int64 bitLength;

        /**
         * The number of bytes that the number uses.
         */
        @ro Int64 byteLength;

        /**
         * The Sign of the number.
         */
        @ro Sign Sign;

        // ----- operations

        /**
         * Addition: Add another number to this number, and return the result.
         */
        Number add(Number n);
        /**
         * Subtraction: Subtract another number from this number, and return the result.
         */
        Number sub(Number n);
        /**
         * Multiplication: Multiply this number by another number, and return the result.
         */
        Number mul(Number n);
        /**
         * Division: Divide this number by another number, and return the result.
         */
        Number div(Number n);

        /**
         * The absolute value of this number.
         */
        Number abs();
        /**
         * The negative of this number.
         */
        Number neg();
        /**
         * This number raised to the specified power.
         */
        Number pow(Number n);
        /**
         * The smaller of this number and the passed number.
         */
        Number min(Number n);
        /**
         * The larger of this number and the passed number.
         */
        Number max(Number n);

        // ----- conversions

        /**
         * Obtain the number as an array of bits.
         */
        Boolean[] to<Boolean[]>();

        /**
         * Obtain the number as an array of bytes.
         */
        UInt8[] to<UInt8[]>();

        /**
         * Convert the number to a variable-length signed integer.
         */
        VarInt to<VarInt>();

        /**
         * Convert the number to a signed 8-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        Int8 to<Int8>()
            {
            return to<VarInt>().to<Int8>();
            }

        /**
         * Convert the number to a signed 16-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        Int16 to<Int16>();
            {
            return to<VarInt>().to<Int16>();
            }

        /**
         * Convert the number to a signed 32-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        Int32 to<Int32>();
            {
            return to<VarInt>().to<Int32>();
            }

        /**
         * Convert the number to a signed 64-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        Int64 to<Int64>();
            {
            return to<VarInt>().to<Int64>();
            }

        /**
         * Convert the number to a signed 128-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        Int128 to<Int128>();
            {
            return to<VarInt>().to<Int128>();
            }

        /**
         * Convert the number to a variable-length unsigned integer.
         */
        VarUInt to<VarUInt>();

        /**
         * Convert the number to a unsigned 8-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        UInt8 to<UInt8>()
            {
            return to<VarUInt>().to<UInt8>();
            }

        /**
         * Convert the number to a unsigned 16-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        UInt16 to<UInt16>();
            {
            return to<VarUInt>().to<UInt16>();
            }

        /**
         * Convert the number to a unsigned 32-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        UInt32 to<UInt32>();
            {
            return to<VarUInt>().to<UInt32>();
            }

        /**
         * Convert the number to a unsigned 64-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        UInt64 to<UInt64>();
            {
            return to<VarUInt>().to<UInt64>();
            }

        /**
         * Convert the number to a unsigned 128-bit integer.
         * Any additional magnitude is discarded; any fractional value is discarded.
         */
        UInt128 to<UInt128>();
            {
            return to<VarInt>().to<UInt128>();
            }

        /**
         * Convert the number to a variable-length binary radix floating point number.
         */
        VarFloat to<VarFloat>();

        /**
         * Convert the number to a 16-bit radix-2 (binary) floating point number.
         */
        Float16 to<Float16>()
            {
            return to<VarFloat>().to<Float16>();
            }

        /**
         * Convert the number to a 32-bit radix-2 (binary) floating point number.
         */
        Float32 to<Float32>()
            {
            return to<VarFloat>().to<Float32>();
            }

        /**
         * Convert the number to a 64-bit radix-2 (binary) floating point number.
         */
        Float64 to<Float64>()
            {
            return to<VarFloat>().to<Float64>();
            }

        /**
         * Convert the number to a 128-bit radix-2 (binary) floating point number.
         */
        Float128 to<Float128>()
            {
            return to<VarFloat>().to<Float128>();
            }

        /**
         * Convert the number to a variable-length decimal radix floating point number.
         */
        VarDec to<VarDec>();

        /**
         * Convert the number to a 32-bit radix-10 (decimal) floating point number.
         */
        Dec32 to<Dec32>()
            {
            return to<VarDec>().to<Dec32>();
            }

        /**
         * Convert the number to a 64-bit radix-10 (decimal) floating point number.
         */
        Dec64 to<Dec64>()
            {
            return to<VarDec>().to<Dec64>();
            }

        /**
         * Convert the number to a 128-bit radix-10 (decimal) floating point number.
         */
        Dec128 to<Dec128>()
            {
            return to<VarDec>().to<Dec128>();
            }
        }

    interface IntNumber
            extends Number
        {
        // operations
        (IntNumber, IntNumber) divmod(IntNumber);
        IntNumber mod(IntNumber);
        IntNumber rem(IntNumber);

        IntNumber inc();
        IntNumber dec();

        IntNumber shl(Int n);
        IntNumber shr(Int n);
        IntNumber ushr(Int n);
        IntNumber rol(Int n);
        IntNumber ror(Int n);
        }

    const IntLiteral(String literal)
            implements IntNumber
        {
        // TODO the idea is that it holds on to the "source code" representation of an integer
        //      number and provides automatic conversion of that number to any runtime Int or
        //      FP number

        String to<String>()
            {
            return literal;
            }

        Int64 to<Int64>()
            {
            // TODO
            }
        }

    const Int64
            implements IntNumber
        {
        /**
         * The minimum value for an Int64.
         */
        static IntLiteral minvalue = -0x8000000000000000;
        /**
         * The maximum value for an Int64.
         */
        static IntLiteral maxvalue =  0x7FFFFFFFFFFFFFFF;

        @ro UInt64 magnitude
            {
            return to<Int128>().abs().to<UInt64>();
            }

        /**
         * In addition to the implicit "add(Int64 n)" method, this method allows any
         * integer to be added to this value.
         */
        Int64 add(IntNumber n);
        // TODO sub / mul / div?
        }

    interface UIntNumber
            extends IntNumber
        {
        UnsignedIntNumber abs()
            {
            return this;
            }

        UnsignedIntNumber neg()
            {
            throw new UnsupportedOperationException();
            }
        }

    const UInt8
            implements UIntNumber
        {
        static IntLiteral minvalue = 0x00;
        static IntLiteral maxvalue = 0xFF;
        }

    /**
     * A signed integer with a power-of-2 number of bits.
     */
    const VarInt
        {
        // TODO
        }

    /**
     * A signed integer with a power-of-2 number of bits.
     */
    const VarUInt
        {
        }

    interface FPNumber
            extends Number
        {
        /**
         * If the floating point number is a finite value, indicating that it is neither
         * infinite nor Not-a-Number.
         */
        @ro Boolean finite;
        /**
         * If the floating point number is an infinite value.
         */
        @ro Boolean infinite;
        /**
         * If the floating point number is Not-a-Number.
         */
        @ro Boolean NaN;
        /**
         * The radix. (The only values defined by IEEE 754-2008 are 2 and 10.)
         */
        @ro Int radix;
        /**
         * The precision, in "digits" of the radix of the floating point number, as specified by IEEE 754-2008.
         */
        @ro Int precision;
        /**
         * The maximum exponent, as specified by IEEE 754-2008.
         */
        @ro Int emax;
        /**
         * The minimum exponent, as specified by IEEE 754-2008.
         */
        @ro Int emin
            {
            return 1 - emax;
            }

        FPNumber round();
        FPNumber floor();
        FPNumber ceil();

        FPNumber exp();
        FPNumber log();
        FPNumber log10();
        FPNumber sqrt();
        FPNumber cbrt();

        FPNumber sin();
        FPNumber cos();
        FPNumber tan();
        FPNumber asin();
        FPNumber acos();
        FPNumber atan();
        FPNumber deg2rad();
        FPNumber rad2deg();
        }

    interface BFPNumber
            extends FPNumber
        {
        @ro Int radix
            {
            return 2;
            }

        @ro Int precision
            {
            // TODO k – round(4×log2(k)) + 13
            }

        @ro Int emax
            {
            // TODO 2^(k–p–1) –1
            }
        }

    interface DecNumber
            extends FPNumber
        {
        @ro Int radix
            {
            return 10;
            }

        @ro Int precision
            {
            // TODO 9×k/32 – 2
            }

        @ro Int emax
            {
            // TODO 3 × 2 ^ (k /16 + 3)
            }

        }

    // -----

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

    interface Const
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
        String name;
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

//    interface Element<T>
//            implements Ref<T>
//        {
//        }

    interface Variable<T>
            implements Ref<T>
        {
        @ro String Name;
        }

    value Bit(Boolean flag)
            implements Integer
        {
        /**
         * Construct a Bit from an Integer, where the Bit value is 0 iff the Integer value is 0.
         */
        construct Bit(Bit:Struct struct, Integer value)
            {
            struct.flag = value != 0;
            }

        Boolean to<Boolean>()
            {
            return flag;
            }

        Integer to<Integer>()
            {
            return flag ? 1 : 0;
            }
        }

    const Char
        {
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
     * UniformIndexed is an interface that allows the square bracket operators
     * to be used with a container that contains elements of a specified type,
     * indexed by a specified type.
     */
    interface UniformIndexed<IndexType, ElementType>
        {
        /**
         * Obtain the value of the specified element.
         */
        ElementType get(IndexType index)
            {
            return elementAt(index).get();
            }

        /**
         * Modify the value in the specified element.
         */
        Void set(IndexType index, ElementType newValue)
            {
            elementAt(index).set(newValue);
            }

        /**
         * Obtain a Ref for the specified element.
         */
        Ref<ElementType> elementAt(IndexType index);
        }

    interface Sequence<ElementType>
            extends UniformIndexed<Int, ElementType>
        {
        @ro Int length;

        /**
        TODO
         * Returns a sub-sequence backed by this sequence.
         */
        SubSequence<ElementType> subSequence(Int start, Int end);

        Iterator<ElementType> iterator();
        }

    interface SubSequence
            extends Sequence<ElementType>
        {
        /**
        TODO
         * Returns a new sequence that changes to it do not affect the original sequence that this
         * sub-sequence was based on, nor do changes to the original sequence show up in the new
         * one ...
         * ... could be copy-on-write
         */
        Sequence<ElementType> reify();
        }

    interface Iteratable<ElementType>
        {
        Iterator<ElementType> iterator();
        }

    interface Iterator<ElementType>
        {
        conditional ElementType next();

        void forEach(function void fn(ElementType))
            {
            while (ElementType value : next())
                {
                fn(value);
                }
            }
        }

    /**
     * An array is a container of elements of a particular type.
     */
    class Array<ElementType>
            implements Sequence<ElementType>
        {
        construct Array(Int capacity)
            {
            if (capacity < 0)
                {
                throw new IllegalArgument("capacity", capacity, "must be >= 0");
                }
            this.capacity = capacity;
            }

        construct (Int capacity, function ElementType fn())
            {
            construct Array(capacity);

            Element<ElementType>? head = null;
            if (capacity > 0)
                {
                head = new Element<ElementType>(fn());

                Element<ElementType> tail = head;
                for (Int i : 1..capacity)
                    {
                    Element<ElementType> node = new Element<ElementType>(fn());
                    tail.next = node;
                    tail      = node;
                    }
                }

            this.head     = head;
            this.capacity = capacity;
            this.length   = capacity;
            }

        public/private Int capacity = 0;
        public/private Int length   = 0;

        Ref<ElementType> elementAt(Int index)
            {
            if (index < 0 || index >= length)
                {
                throw new IllegalArrayIndex(index, 0, length);
                }

            Element element = head;
            // TODO start with first, walk list
            }

        private Element<ElementType>? head;

        class Element<RefType>(ElementType value)
                extends Ref<RefType>
            {
            ElementType get()
                {
                return value;
                }

            void set(ElementType value)
                {
                this.value = value;
                }

            Element<RefType>? next;
            }
        }

    // REVIEW this is roughly what I'm thinking as the basis for Tuple
    // NOTE that Tuple == Struct in many ways
    // - Tuple has (optionally) named elements; Struct has named elements
    // - Tuple has int-indexed elements; Struct _could_ be accessed in the same way
    //   -> probably have Struct.to<Tuple<ElementType>>(); instead

    interface Field<RefType>
            extends Ref<RefType> // REVIEW does it (could it) derive from property?
        {
        String name;
        }

    /**
     * NonUniformIndexed is an interface that allows the square bracket operators
     * to be used with a container that contains a specific number of elements, each
     * of an arbitrary type.
     */
    interface NonUniformIndexed<ElementType...>
        {
        /**
         * The number of Elements in the Tuple.
         */
        @ro Int length;

        /**
         * Obtain the value of the specified element.
         */
        ElementType[index] get(Int index);

        /**
         * Modify the value in the specified element.
         */
        void set(Int index, ElementType[index] newValue)
            {
            elementAt(index).set(newValue);
            }

        /**
         * Obtain the Ref for the specified element.
         */
        Ref<ElementType[index]> elementAt(Int index);
        }

    /**
     * A Tuple is a container for an arbitrary number of elements, each of an arbitrary
     * type.
     * <p>
     * The Tuple interface is what the "NonUniformIndexed" interface would look like.
     */
    class Tuple<ElementType...>
            implements NonUniformIndexed<ElementType...>
        {
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
            extends Indexed<KeyType>
    }
