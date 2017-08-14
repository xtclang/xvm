package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Comparator;

import java.util.function.Consumer;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.ValueConstant;


/**
 * A Constant value stored in the ConstantPool of an XVM FileStructure.
 * <p/>
 * Constants are the immutable terminals of the XVM Structure hierarchy. For example, the string
 * "hello world" is a constant, as is the number 42. By representing these as constant values in a
 * "pool" of constant values, it is possible for multiple uses of the same constant value to all
 * refer to one location within the assembled binary FileStructure, saving space. Furthermore, it
 * allows a reference to a particular constant to be made from anywhere within the FileStructure
 * using an integer, which identifies the ordinal position (known as the <i>index</i>) of the
 * constant within the sequence of all constants (whose order does not follow any partiscular rule.)
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
 *
 *
 * @author cp  2015.12.08
 */
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>, Cloneable
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
     * ConstantPool#ensureCharStringConstant(String)} method, it would use the string "Hello World!"
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

    protected abstract void assemble(DataOutput out)
            throws IOException;


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
        Enum,
        Tuple,
        Array,
        UInt8Array,     // byte[]
        Set,
        MapEntry,
        Map,

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
        ThisClass,      // TODO
        ParentClass,
        ChildClass,
        TypeProperty,   // TODO
        TypeRegister,   // TODO
        Signature,      // TODO
        Unresolved,
        UnresolvedClass,// TODO
        UnresolvedType, // TODO

        /*
         * Types.
         */
        ClassType,
        ThisType,
        ParentType,
        ChildType,
        RegisterType,
        ParameterType,
        ImmutableType,
        UnionType,
        IntersectionType,
        AnnotatedType,

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

        /**
         * Determine if structures of the type are length-encoded when assembled.
         *
         * @return true if the persistent form of the corresponding XVM structure gets
         *         length-encoded
         */
        public boolean isLengthEncoded()
            {
            switch (this)
                {
                case Package:
                case Class:
                case Method:
                    return true;

                default:
                    return false;
                }
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
     * A cached index of the location of the Constant in the pool.
     */
    private transient int m_iPos = -1;

    /**
     * A calculated number of references to this constant; useful for priority based ordering of the
     * constant pool.
     */
    private transient int m_cRefs;
    }
