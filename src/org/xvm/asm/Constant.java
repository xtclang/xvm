package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Comparator;

import java.util.List;
import java.util.function.Consumer;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.ValueConstant;

import org.xvm.compiler.Token;

import org.xvm.type.Decimal128;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;

import org.xvm.util.PackedInteger;


/**
 * A Constant value stored in the ConstantPool of an XVM FileStructure.
 * <p/>
 * Constants are the immutable terminals of the XVM Structure hierarchy. For example, the string
 * "hello world" is a constant, as is the number 42. By representing these as constant values in a
 * "pool" of constant values, it is possible for multiple uses of the same constant value to all
 * refer to one location within the assembled binary FileStructure, saving space. Furthermore, it
 * allows a reference to a particular constant to be made from anywhere within the FileStructure
 * using an integer, which identifies the ordinal position (known as the <i>index</i>) of the
 * constant within the sequence of all constants (whose order does not follow any particular rule.)
 * <p/>
 * In addition to the simple examples of constant values above, constants also serve to identify
 * structures within the FileStructure, and to identify dependencies on other modules located in
 * other FileStructures. Specifically:
 * <p/>
 * <ul>
 * <li>Each identifiable sub-structure within the FileStructure specifies the index of the constant
 *     that is the identity of that sub-structure, such as a Module, Package Class, Property,
 *     Method, or TypeDef;</li>
 * <li>Each reference to a sub-structure that exists within the FileStructure is made by specifying
 *     the index of the constant that identifies that particular sub-structure, such as a Method
 *     that is being invoked or a Property that is being accessed;</li>
 * <li>Similarly, each reference to a sub-structure that exists in some other FileStructure is made
 *     by specifying the index of the constant within this FileStructure that exactly identifies
 *     (fully qualifies) the particular sub-structure in the other FileStructure, such as a Class
 *     being referenced or a Method being invoked in a different Module.</li>
 * </ul>
 * There are several different categories of constants:
 * <ul>
 * <li><b>{@link ValueConstant}</b> - Representing "typed values", such as strings and ints, but
 *     also including composite structures such as arrays, tuples, and maps;</li>
 * <li><b>{@link IdentityConstant}</b> - </li>
 * <li><b>{@link PseudoConstant}</b> - </li>
 * <li><b>{@link TypeConstant}</b> - </li>
 * <li><b>{@link ConditionalConstant}</b> - </li>
 * </ul>
 */
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>, Cloneable, Argument
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Constant.
     *
     * @param pool  the containing constant pool
     */
    protected Constant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- Constant operations -------------------------------------------------------------------

    /**
     * Determine the enumerated constant format classification.
     *
     * @return the format for the Constant
     */
    public abstract Format getFormat();

    /**
     * @return true iff this constant represents a type at runtime, whether or not the exact
     *         type is known at compile time
     */
    public boolean isType()
        {
        return false;
        }

    /**
     * @return true iff this constant represents a value at runtime, whether or not the exact
     *         value is known at compile time
     */
    public boolean isValue()
        {
        return false;
        }

    /**
     * Obtain the TypeConstant that represents the runtime type of the value of this constant.
     *
     * @return a TypeConstant
     */
    @Override
    public TypeConstant getType()
        {
        throw new UnsupportedOperationException("constant-class=" + getClass().getSimpleName());
        }

    /**
     * @return true iff this constant represents a class at runtime, whether or not the exact
     *         identity of the class is known at compile time
     */
    public boolean isClass()
        {
        return false;
        }

    /**
     * @return true iff this constant represents an auto-narrowing identity
     */
    public boolean isAutoNarrowing()
        {
        return false;
        }

    /**
     * @return true iff this constant represents a property at runtime, whether or not the exact
     *         identity of the property is known at compile time
     */
    public boolean isProperty()
        {
        return false;
        }

    /**
     * @return true iff this constant represents a method at runtime, whether or not the exact
     *         identity of the method is known at compile time
     */
    public boolean isMethod()
        {
        return false;
        }

    /**
     * @return false iff the constant is resolved, and all reachable constants within the constant
     *         are resolved
     */
    public boolean containsUnresolved()
        {
        return false;
        }

    /**
     * Determine the type of a binary operator on two constant values.
     *
     * @param op    the token id representing the operation
     * @param that  the Constant on the right side of the operation
     *
     * @return the type of the resulting constant value
     */
    public TypeConstant resultType(Token.Id op, Constant that)
        {
        return getType();
        }

    /**
     * Generate some default constant value for the specified type, which should be the type of a
     * particular constant.
     *
     * @param type  the desired type
     *
     * @return a Constant of the specified type, if possible, otherwise the constant for False
     */
    public static Constant defaultValue(TypeConstant type)
        {
        ConstantPool pool = type.getConstantPool();
        switch (type.getEcstasyClassName())
            {
            default:
                // TODO this needs to be deleted once we verify that things are working
                // System.out.println("** unsupported constant type: " + type);
                // fall through
            case "Boolean":
                return pool.valFalse();

            case "Nullable":
                return pool.valNull();

            case "Ordered":
                return pool.valOrd(0);

            case "Boolean.True":
                return pool.valTrue();

            case "Char":
                return pool.ensureCharConstant('?');

            case "String":
                return pool.ensureStringConstant("");

            case "IntLiteral":
                return pool.ensureLiteralConstant(Format.IntLiteral, "0");

            case "FPLiteral":
                return pool.ensureLiteralConstant(Format.FPLiteral, "0.0");

            case "Bit":
                return pool.ensureBitConstant(0);

            case "Nibble":
                return pool.ensureNibbleConstant(0);

            case "Int8":
                return pool.ensureInt8Constant(0);

            case "UInt8":
                return pool.ensureUInt8Constant(0);

            case "Int16":
            case "Int32":
            case "Int64":
            case "Int128":
            case "VarInt":
            case "UInt16":
            case "UInt32":
            case "UInt64":
            case "UInt128":
            case "VarUInt":
                return pool.ensureIntConstant(PackedInteger.ZERO, Format.valueOf(type.getEcstasyClassName()));

            case "Dec32":
                return pool.ensureDecimalConstant(Decimal32.POS_ZERO);
            case "Dec64":
                return pool.ensureDecimalConstant(Decimal64.POS_ZERO);
            case "Dec128":
                return pool.ensureDecimalConstant(Decimal128.POS_ZERO);
            case "VarDec":
                throw new UnsupportedOperationException();

            case "Float16":
                return pool.ensureFloat16Constant(0.0f);
            case "Float32":
                return pool.ensureFloat32Constant(0.0f);
            case "Float64":
                return pool.ensureFloat64Constant(0.0);
            case "Float128":
                return pool.ensureFloat128Constant(new byte[16]);
            case "VarFloat":
                throw new UnsupportedOperationException();

            // TODO arrays and lists and maps and tuples and so on
            }
        }
    /**
     * Apply the specified operation to this Constant.
     *
     * @param op    the token id representing the operation
     * @param that  the Constant on the right side of the operation
     *
     * @return the result as a Constant
     */
    public Constant apply(Token.Id op, Constant that)
        {
        throw new UnsupportedOperationException("this=" + getClass().getSimpleName()
                + ", op=" + op.TEXT
                + that == null ? "" : ", that=" + that.getClass().getSimpleName());
        }

    /**
     * Convert this constant to a constant of the specified type.
     *
     * @param typeOut  the type that the constant must be assignable to
     *
     * @return the converted constant, or null if the conversion is not implemented
     *
     * @throws ArithmeticException  on overflow
     */
    public Constant convertTo(TypeConstant typeOut)
        {
        return getType().isA(typeOut) ? this : null;
        }

    /**
     * Create a clone of this Constant so that it can be adopted by a different ConstantPool.
     *
     * @param pool  the pool that will hold the clone of this Constant
     *
     * @return the new Constant
     */
    Constant adoptedBy(ConstantPool pool)
        {
        Constant that;
        try
            {
            that = (Constant) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException(e);
            }
        that.setContaining(pool);
        return that;
        }

    /**
     * Visit every underlying constant (if any).
     *
     * @param visitor  a Consumer&lt;Constant&gt; whose {@link Consumer#accept(Object)}that will be
     *                 called with each underlying (i.e. referenced) constant
     */
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        }

    /**
     * Determine the last known position that the Constant was located at in its ConstantPool.
     * Generally. the position only has meaning during the disassembly and assembly processes. The
     * position of all constants may be re-ordered as part of the assembly process.
     *
     * @return the last known index of the Constant, or <tt>-1</tt> if no position has been assigned
     *         to the constant
     */
    public int getPosition()
        {
        return m_iPos;
        }

    /**
     * Assign a position to the Constant.
     *
     * @param iPos  the position to assign to the Constant
     */
    protected void setPosition(int iPos)
        {
        assert iPos >= -1;
        m_iPos = iPos;
        }

    /**
     * Obtain an object that can be used as a key to locate this Constant. By default, the Constant
     * does not have a locator. Even within a particular category of Constants, it is acceptable
     * that only some of the Constants will have a locator, but given a particular Constant value,
     * the choice must be consistent. The purpose of the locator is to avoid having to create a new
     * Constant instance just to see if there is already an old Constant index; for example, if the
     * constant for the character string "Hello World!" is requested by calling the {@link
     * ConstantPool#ensureStringConstant(String)} method, it would use the string "Hello World!"
     * as the locator object to see if that constant already exists; if it could not do so, it would
     * have to create a new CharStringConstant and register it just to find out if such a constant
     * already exists! Some constants would spend as much time and memory creating a locator as it
     * would take to just create a new constant, so in those cases, no locator is used. Lastly, the
     * locator is a completely hidden concept shared between the Constant and the ConstantPool;
     * users of the Constant and ConstantPool APIs are not even aware that the concept exists.
     *
     * @return an object that uniquely identifies this Constant within its category of constants,
     *         and implements the Object methods {@link #equals}, {@link #hashCode}, and {@link
     *         #toString}; or null
     */
    protected Object getLocator()
        {
        return null;
        }

    /**
     * This method allows each particular type of constant to compare its detailed information with
     * another instance of the same type of constant in order to provide a stable and predictable
     * ordering of constants.
     *
     * @param that  another Constant of the same Type
     *
     * @return a negative integer, zero, or a positive integer as this Constant is less than, equal
     *         to, or greater than the specified Constant
     */
    protected abstract int compareDetails(Constant that);

    /**
     * Before the registration of constants begins, each Constant's tracking of its references is
     * reset. There are two purposes: (1) to be able to tally how many references there are to the
     * constant, so that it can be placed near the front of the ConstantPool if it is used
     * frequently (which will reduce the size of the index used to specify the constant), and
     * (2) to be able to determine which Constants can be discarded altogether (i.e. the ones that
     * have zero references to them.)
     */
    void resetRefs()
        {
        m_cRefs = 0;
        }

    /**
     * This marks that the Constant is referred to by another XvmStructure.
     */
    void addRef()
        {
        ++m_cRefs;
        }

    /**
     * Based on the Constant registration process that tallies references to the Constants,
     * determine if this constant has any references to it.
     *
     * @return true iff there are any known references to this Constant
     */
    boolean hasRefs()
        {
        return m_cRefs > 0;
        }


    // ----- XvmStructure operations ---------------------------------------------------------------

    @Override
    public ConstantPool getConstantPool()
        {
        return (ConstantPool) getContaining();
        }

    @Override
    public boolean isModified()
        {
        // it's a constant; it can't be modified
        return false;
        }

    @Override
    protected void markModified()
        {
        // it's a constant; it can't be modified
        throw new IllegalStateException();
        }

    @Override
    protected void resetModified()
        {
        // it's a constant; it wasn't modified
        }

    @Override
    public boolean isConditional()
        {
        // a constant does not support conditional inclusion of itself
        return false;
        }

    @Override
    public void purgeCondition(ConditionalConstant condition)
        {
        }

    @Override
    public boolean isPresent(LinkerContext ctx)
        {
        // a constant does not support conditional inclusion of itself; it will simply be
        // automatically discarded if it is not referenced at the time of assembly
        return true;
        }

    @Override
    public boolean isResolved()
        {
        return true;
        }

    /**
     * {@inheritDoc}
     * <p/>
     * Since the reading of the Constant information is done as part of construction, this method is
     * used to provide the Constant a chance to resolve other Constants that it knows only by index.
     * <p/>
     * This method must be overridden by constant types which reference other constants.
     */
    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        }

    /**
     * {@inheritDoc}
     * <p/>
     * This method must be overridden by constant types which reference other constants.
     */
    @Override
    protected void registerConstants(ConstantPool pool)
        {
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        }


    // ----- debugging support ---------------------------------------------------------------------

    /**
     * @return the value of the Constant as it might appear in source code
     */
    public abstract String getValueString();

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        // this must be over-ridden by any Constant implementation that has a multi-line toString()
        out.print(sIndent);
        out.println(toString());
        }


    // ----- Object operations ---------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        // inefficient and begs for optimization by sub-classes
        return this.toString().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (!(obj instanceof Constant))
            {
            return false;
            }

        Constant constThis = this instanceof ResolvableConstant
                ? ((ResolvableConstant) this).unwrap()
                : this;
        Constant constThat = obj instanceof ResolvableConstant
                ? ((ResolvableConstant) obj).unwrap()
                : (Constant) obj;
        return constThis == constThat || (constThis.getFormat() == constThat.getFormat()
                && constThis.compareDetails(constThat) == 0);
        }

    @Override
    public String toString()
        {
        return getFormat().name() + '{' + getDescription() + '}';
        }


    // ----- Comparable interface ------------------------------------------------------------------

    @Override
    public int compareTo(Constant that)
        {
        if (this == that)
            {
            return 0;
            }

        // primary sort of constants is by the "format" i.e. the "binary
        // type" of the constant
        int cDif = this.getFormat().ordinal() - that.getFormat().ordinal();
        if (cDif != 0)
            {
            return cDif;
            }

        // two constants of the same format can be compared by their
        // contents
        return this.compareDetails(that);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Determine the index of the specified Constant.
     *
     * @param constant  the Constant to look up the index for, or null
     *
     * @return the Constant's index in the ConstantPool, or -1 if passed constant is null
     */
    protected static int indexOf(Constant constant)
        {
        return constant == null ? -1 : constant.getPosition();
        }

    /**
     * Translate a comparison operator and and ordered result into a constant value.
     *
     * @param nOrder  a value of -1, 0, or 1 (or more loosely: negative, zero, or positive)
     * @param op      a comparison operator
     *
     * @return one of {@code Boolean.True}, {@code Boolean.False}, {@code Ordered.Lesser},
     *         {@code Ordered.Equal}, {@code Ordered.Greater}
     */
    protected Constant translateOrder(int nOrder, Token.Id op)
        {
        ConstantPool pool = getConstantPool();
        switch (op)
            {
            case COMP_EQ:
                return pool.valOf(nOrder == 0);
            case COMP_NEQ:
                return pool.valOf(nOrder != 0);
            case COMP_LT:
                return pool.valOf(nOrder < 0);
            case COMP_LTEQ:
                return pool.valOf(nOrder <= 0);
            case COMP_GT:
                return pool.valOf(nOrder > 0);
            case COMP_GTEQ:
                return pool.valOf(nOrder >= 0);
            case COMP_ORD:
                return pool.valOrd(nOrder);
            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Add a TypeConstant that needs its TypeInfo to be built or rebuilt.
     *
     * @param type  the TypeConstant to defer the building of a TypeInfo for
     */
    protected void addDeferredTypeInfo(TypeConstant type)
        {
        getConstantPool().addDeferredTypeInfo(type);
        }

    /**
     * @return true iff there are any TypeConstants that have deferred the building of a TypeInfo
     */
    protected boolean hasDeferredTypeInfo()
        {
        return getConstantPool().hasDeferredTypeInfo();
        }

    /**
     * @return the List of TypeConstants to build (or rebuild) TypeInfo objects for
     */
    protected List<TypeConstant> takeDeferredTypeInfo()
        {
        return getConstantPool().takeDeferredTypeInfo();
        }


    // ----- constant pool format identifiers ------------------------------------------------------

    /**
     * The Format enum is used to specify the "binary format on disk" used to encode a constant's
     * information. This is necessary because all of the Constants in the ConstantPool are written
     * out in a seemingly-arbitrary sequence, mixed in with various other Constants that have
     * various other formats.
     */
    public enum Format
        {
        /*
         * Values.
         */
        IntLiteral,
        Bit,
        Nibble,
        Int8,
        Int16,
        Int32,
        Int64,
        Int128,
        VarInt,
        UInt8,
        UInt16,
        UInt32,
        UInt64,
        UInt128,
        VarUInt,
        FPLiteral,
        Float16,
        Float32,
        Float64,
        Float128,
        VarFloat,
        Dec32,
        Dec64,
        Dec128,
        VarDec,
        Char,
        String,
        Date,           // ISO8601 YYYY-MM-DD date format
        Time,           // ISO8601 HH:MM[:SS[.sssssssss]]['Z' | ('+'|'-')hh[:mm]] format
        DateTime,       // ISO8601 date ['T' time] format
        Duration,       // ISO8601 P[n]Y[n]M[n]DT[n]H[n]M[n]S | P[n]W format
        TimeInterval,   // ISO8601 datetime '/' datetime format
        Version,
        SingletonConst,   // identity constant for a Module, Package, an enum value or a static const
        SingletonService, // identity constant of a Service class
        ConstantValue,    // PropertyConstant that represents the value of a const or a service
        Tuple,
        Array,
        UInt8Array,    // byte[]
        Set,
        MapEntry,
        Map,
        Interval,

        /*
         * Structural identifiers.
         */
        Module,
        Package,
        Class,
        Typedef,
        Property,
        MultiMethod,
        Method,

        /*
         * Pseudo identifiers.
         */
        UnresolvedName,
        ThisClass,
        ParentClass,
        ChildClass,
        Register,
        Signature,
        NativeClass,

        /*
         * Types.
         */
        TerminalType,
        ImmutableType,
        AccessType,
        AnnotatedType,
        ParameterizedType,
        UnionType,
        IntersectionType,
        DifferenceType,

        /*
         * Conditions.
         */
        ConditionNot,
        ConditionAll,
        ConditionAny,
        ConditionNamed,
        ConditionPresent,
        ConditionVersionMatches,
        ConditionVersioned,
        ;

        public Format next()
            {
            return Format.valueOf(this.ordinal() + 1);
            }

        /**
         * Look up a Format enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Format enum for the specified ordinal
         */
        public static Format valueOf(int i)
            {
            return FORMATS[i];
            }

        /**
         * All of the Format enums.
         */
        private static final Format[] FORMATS = Format.values();
        }


    // ----- Constant Comparators for ordering ConstantPool ----------------------------------------

    /**
     * A Comparator of Constant values that orders the "most frequently used" constants to the front
     * of the ConstantPool.
     */
    public static final Comparator<Constant> MFU_ORDER = new Comparator<Constant>()
        {
        @Override
        public int compare(Constant o1, Constant o2)
            {
            if (o1 == o2)
                {
                return 0;
                }

            assert o1.getConstantPool() == o2.getConstantPool();

            // how much more is the first constant used than the second constant?
            int cDif = o1.m_cRefs - o2.m_cRefs;

            // most used comes first (i.e. _reverse_ sort on most used)
            return -cDif;
            }
        };


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An empty array of constants.
     */
    public final static Constant[] NO_CONSTS = new Constant[0];

    /**
     * A cached index of the location of the Constant in the pool.
     */
    private transient int m_iPos = -1;

    /**
     * A calculated number of references to this constant; useful for priority based ordering of the
     * constant pool.
     */
    private transient int m_cRefs;
    }
