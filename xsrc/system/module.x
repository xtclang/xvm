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

    typedef Nullable.Null null;
    typedef Boolean.False false;
    typedef Boolean.True  true;
    typedef UInt8         Byte;
    typedef Int64         Int;
    typedef UInt64        UInt;
    typedef Decimal128    Dec;

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

        static Boolean equals(Int64 value1, Int64 value2)
            {
            return value1.to<Byte[]> == value2.to<Byte[]>;
            }
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

    interface BinaryNumber
            extends FloatingPointNumber
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

    interface DecimalNumber
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

    interface Property<T>
            extends Ref<T>
        {
        @ro String Name;

        @ro boolean Assigned;

        // TODO evaluate:
        // peek / poke?
        // CAS?
        }

    mixin Lazy<RefType> into Ref<RefType>
        {
        conditional RefType peek()
            {
            if (filled)
                {
                return true, get();
                }

            return false;
            }

        RefType get()
            {
            if (!filled)
                {
                set(evaluate());
                assert:always filled;
                }

            return super();
            }

        Void set(RefType value)
            {
            assert:always !filled;
            super(value);
            filled = true;
            }

        protected RefType evaluate();

        private Boolean filled = false;
        }

    mixin FnLazy<RefType>(function RefType () produce)
            extends Lazy<RefType>
        {
        RefType evaluate()
            {
            return produce();
            }
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

    value Binary
        {
        // TODO
        }

    // TODO Comparable, Hashable

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
        ElementType get(IndexType index);

        /**
         * Modify the value in the specified element.
         */
        Void set(IndexType index, ElementType newValue)
            {
            throw new TODO
            }

        /**
         * Obtain a Ref for the specified element.
         */
        Ref<ElementType> elementAt(IndexType index)
            {
            return new TODO
            }
        }

    interface Iteratable<ElementType>
        {
        Iterator<ElementType> iterator();

        Iterator<ElementType> iterator(function Boolean fn(ElementType))
            {
            return new Iterator<ElementType>()
                {
                Iterator iter = iterator();

                conditional ElementType next()
                    {
                    while (ElementType value : iter.next())
                        {
                        if (fn(value))
                            {
                            return (true, value);
                            }
                        }

                    return false;
                    }
                }
            }
        }

    interface Sequence<ElementType>
            extends UniformIndexed<Int, ElementType>
            extends Iterable<ElementType>
        {
        /**
         * The length of the Sequence, which is the number of elements in the Sequence.
         */
        @ro Int length;

        /**
         * Returns a SubSequence of this Sequence. The SubSequence is backed by this
         * Sequence, which means that changes made to the SubSequence will be visible
         * through this Sequence.
         *
         * @param start  first index to include in the SubSequence, inclusive
         * @param end    last index to include in the SubSequence, exclusive
         */
        Sequence<ElementType> subSequence(Int start, Int end);

        /**
         * Obtain a Sequence of the same length and that contains the same values
         * as this SubSequence. Changes to the returned Sequence are not visible
         * through this SubSequence, and subsequent changes to this SubSequence
         * are not visible through the returned Sequence.
         */
        Sequence<ElementType> reify();
        }

    interface Iterator<ElementType>
        {
        conditional ElementType next();

        Void forEach(function Void fn(ElementType)) // ElementType -> ()
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

            Element element = (Element) head;
            while (index-- > 0)
                {
                element = (Element) element.next;
                }

            return element;
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
        @ro String name;
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
