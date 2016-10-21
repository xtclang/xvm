package org.xvm.asm;


import java.io.DataInput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Comparator;
import org.xvm.asm.ConstantPool.ConditionalConstant;


/**
 * A Constant value stored in the ConstantPool of an XVM FileStructure.
 * <p>
 * Constants are the immutable terminals of the XVM Structure hierarchy. For
 * example, the string "hello world" is a constant, as is the number 42. By
 * representing these as constant values in a "pool" of constant values, it
 * is possible for multiple uses of the same constant value to all refer to
 * one location within the assembled binary, saving space. Furthermore, it
 * allows a reference to a binary to be made from anywhere with a single
 * number, which is the ordinal position of the constant within the sequence
 * of all constants (whose order can be considered arbitrary).
 * <p>
 * A Constant can be compared to another Constant of the same type for
 * purposes of ordering.
 *
 * @author cp  2012.12.08
 */
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct a Constant.
     *
     * @param pool  the containing constant pool
     */
    protected Constant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- XvmStructure operations -------------------------------------------


    @Override
    protected ConstantPool getConstantPool()
        {
        return (ConstantPool) getContaining();
        }

    @Override
    protected boolean isModifiable()
        {
        // it's a constant; it can't be modified
        return false;
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
        // a constant does not support conditional inclusion of itself; it will
        // simply be automatically discarded if it is not referenced at the
        // time of assembly
        return true;
        }

    @Override
    public boolean isResolved()
        {
        return true;
        }

    /**
     * {@inheritDoc}
     * <p>
     * Since the reading of the Constant information is done as part of
     * construction, this method is used to provide the Constant a chance to
     * resolve other Constants that it knows only by index.
     * <p>
     * This method must be overridden by constant types which reference other
     * constants.
     */
    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        }

    /**
     * {@inheritDoc}
     * <p>
     * This method must be overridden by constant types which reference other
     * constants.
     */
    @Override
    protected void registerConstants(ConstantPool pool)
        {
        }


    // ----- debugging support -------------------------------------------------

    public abstract String getValueString();

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        // to be over-ridden by various specific constants if they have multi-line toString() implementations
        out.print(sIndent);
        out.println(toString());
        }


    // ----- Object operations -------------------------------------------------

    @Override
    public int hashCode()
        {
        // inefficient and begs for optimization by sub-classes
        return this.toString().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof Constant)
            {
            Constant that = (Constant) obj;
            return this == that || (this.getType() == that.getType() && this.compareDetails(that) == 0);
            }

        return false;
        }

    @Override
    public String toString()
        {
        return getType().name() + '{' + getDescription() + '}';
        }


    // ----- Comparable interface

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
            // order by types
            return cDif;
            }

        // two constants of the same format can be compared by their
        // contents
        return this.compareDetails(that);
        }


    // ----- Constant operations ------------------------------------------------

    /**
     * Determine the enumerated constant type classification.
     *
     * @return the type for the Constant
     */
    public abstract Type getType();

    /**
     * Determine the enumerated constant format classification.
     *
     * @return the format for the Constant
     */
    public abstract Format getFormat();

    /**
     * Determine the last known position that the Constant was located at in
     * its ConstantPool. Generally. the position only has meaning during the
     * disassembly and assembly processes. The position of all constants may
     * be re-ordered as part of the assembly process.
     *
     * @return the last known index of the Constant, or <tt>-1</tt> if no
     *         position has been assigned to the constant
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
     * Obtain an object that can be used as a key to locate this Constant. By
     * default, the Constant does not have a locator. Even within a particular
     * category of Constants, it is acceptable that only some of the Constants
     * will have a locator, but given a particular Constant value, the choice
     * must be consistent.
     *
     * @return an object that uniquely identifies this Constant within its
     *         category of constants, and implements the Object methods
     *         {@link #equals}, {@link #hashCode}, and {@link #toString}; or
     *         null
     */
    protected Object getLocator()
        {
        return null;
        }

    /**
     * This method allows each particular type of constant to compare its
     * detailed information with another instance of the same type of constant
     * in order to provide a stable and predictable ordering of constants.
     *
     * @param that  another Constant of the same Type
     *
     * @return a negative integer, zero, or a positive integer as this Constant
     *         is less than, equal to, or greater than the specified Constant
     */
    protected abstract int compareDetails(Constant that);

    /**
     * Before the registration of constants begins, each Constant's tracking of
     * its references is reset. There are two purposes: (1) to be able to tally
     * how many references there are to the constant, so that it can be placed
     * near the front of the ConstantPool, and (2) to be able to determine which
     * Constants can be discarded altogether.
     */
    protected void resetRefs()
        {
        m_cRefs = 0;
        }

    /**
     * Note that the Constant is referred to by another XvmStructure.
     */
    protected void addRef()
        {
        ++m_cRefs;
        }

    /**
     * Note that the Constant is referred to by another Contant.
     * <p>
     * This method must be overridden by constant types which track references
     * from other constants.
     *
     * @param that  the Constant that references this Constant
     */
    protected void addRef(Constant that)
        {
        addRef();
        }

    /**
     * Based on the Constant registration process that tallies references to the
     * Constants, determine if this constant has no references to it.
     *
     * @return true iff there are no known references to this Constant
     */
    protected boolean hasRefs()
        {
        return m_cRefs > 0;
        }

    /**
     * If this constant acts as an identity for an XVM structure, then
     * instantiate that XVM Structure using this constant as its identity.
     *
     * @param xsParent  the parent of the XVM structure to instantiate
     *
     * @return the new XVM structure
     */
    protected XvmStructure instantiate(XvmStructure xsParent)
        {
        throw new UnsupportedOperationException();
        }


    // ----- constant pool type identifiers ------------------------------------

    /**
     * Enum: Types of Constant values in the ConstantPool.
     */
    enum Type
        {
        Byte,
        ByteString,
        Char,
        CharString,
        Int,
        Version,
        Condition,
        Module,
        Package,
        Class,
        Method,
        Property,
        Parameter;  // REVIEW not in constant pool=

        /**
         * Look up a Type enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Type enum for the specified ordinal
         */
        public static Type valueOf(int i)
            {
            return TYPES[i];
            }

        /**
         * Determine if structures of the type are length-encoded when
         * assembled.
         *
         * @return true if the persistent form of the corresponding XVM
         *         structure gets length-encoded
         */
        public boolean isLengthEncoded()
            {
            switch (this)
                {
                case Package:
                case Class:
                case Method:
                case Property:
                    return true;

                default:
                    return false;
                }
            }

        /**
         * All of the Type enums.
         */
        private static final Type[] TYPES = Type.values();
        }


    // ----- constant pool format identifiers ----------------------------------

    /**
     * Enum: Binary formats of Constant values in the ConstantPool.
     */
    enum Format // TODO re-do / maybe discard this altogether?
        {
        Byte,
        ByteString,
        Char,
        CharString,
        Int,
        Version,
        ConditionNot,
        ConditionAll,
        ConditionAny,
        ConditionOnly1,
        ConditionNamed,
        ConditionPresent,
        ConditionVersion,
        Module,
        Package,

        /**
         * Constant-Type: Class (or Type).
         * <p>
         * Implementation-Class: {@link ConstantPool.ClassConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 8
         * [1..] = index of the Module, Package, Class, Property, or Method constant
         *         that contains this class, in variable-length encoded format
         * [...] = index of the Character-String constant that specifies the
         *         unqualified class name, in variable-length encoded format
         * [...] = number of class specification parameters, in variable-length
         *         encoded format; then, for each parameter argument:
         *         -> the index of the Character-String constant for the parameter
         *            name, in variable-length encoded format
         *         -> the index of the Class constant specifying the parameter type,
         *            in variable-length encoded format
         *         -> the index of the constant specifying the default argument
         *            value, in variable-length encoded format, or -1 if the
         *            argument is required; to reference the value of an in-scope
         *            specification parameter, specify the index of a String
         *            constant whose value is the name of the specification
         *            parameter
         * </pre></code>
         */
        Class,

        /**
         * Constant-Type: Parameterized Class.
         * <p>
         * Implementation-Class: {@link ConstantPool.ClassConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 9
         * [1..] = index of the Class Constant that specifies the unparameterized
         *         class, in variable-length encoded format
         * [...] = number of parameter arguments, in variable-length encoded format;
         *         then, for each parameter argument:
         *         -> the index of the Integer constant (parameter index) or
         *            Character-String constant (parameter name), in variable-length
         *            encoded format
         *         -> the index of the constant specifying the argument value, in
         *            variable-length encoded format; to reference the value of an
         *            in-scope specification parameter, specify the index of a
         *            String constant whose value is the name of the specification
         *            parameter
         * </pre></code>
         */
        ClassParmed,

        /**
         * Constant-Type: Method (or Function).
         * <p>
         * Implementation-Class: {@link ConstantPool.MethodConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 10
         * [1..] = index of the Module, Package, Class, Property, or Method constant
         *         that contains this method, in variable-length encoded format
         * [...] = index of the Character-String constant that specifies the
         *         method name, in variable-length encoded format
         * [...] = number of method specification parameters, in variable-length
         *         encoded format; then, for each parameter:
         *         -> the index of the Character-String constant for the parameter
         *            name, in variable-length encoded format
         *         -> the index of the Class constant specifying the parameter type,
         *            in variable-length encoded format
         *         -> the index of the constant specifying the default argument
         *            value, in variable-length encoded format, or -1 if the
         *            argument is required
         * [...] = number of method invocation parameters, in variable-length
         *         encoded format; then, for each parameter argument:
         *         -> the index of the Character-String constant for the parameter
         *            name, in variable-length encoded format
         *         -> the index of the Class constant specifying the parameter type,
         *            in variable-length encoded format, or the index of the String
         *            constant specifying the class or method specification
         *            parameter that identifies the type
         *         -> the index of the constant specifying the default argument
         *            value, in variable-length encoded format, or -1 if the
         *            argument is required
         * [...] = number of method return values, in variable-length encoded
         *         format; then, for each return value:
         *         -> the index of the Character-String constant for the return
         *            value name, in variable-length encoded format
         *         -> the index of the Class constant specifying the return type,
         *            in variable-length encoded format, or the index of the String
         *            constant specifying the class or method specification
         *            parameter that identifies the type
         *         -> the index of the constant specifying the default argument
         *            value, in variable-length encoded format, or -1 if the
         *            argument is required
         * </pre></code>
         */
        Method,

        /**
         * Constant-Type: Property (or Field).
         * <p>
         * Implementation-Class: {@link ConstantPool.PropertyConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0] = 12
         * [1..] = index of the Module, Package, Class, Property, or Method
         *         constant that contains this property, in variable-length
         *         encoded format
         * [...] = index of the Character-String constant that specifies the
         *         unqualified class name, in variable-length encoded format
         * </pre></code>
         */
        Property,

        Parameter;

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


    // ----- Constant Comparators for ordering ConstantPool --------------------

    /**
     * A Comparator of Constant values that orders the "most frequently used"
     * constants to the front of the ConstantPool.
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

            int cDif = o1.m_cRefs - o2.m_cRefs;

            // most used comes first
            return -cDif;
            }
        };


    // ----- data members ------------------------------------------------------

    /**
     * A cached index of the location of the Constant in the pool.
     */
    private transient int m_iPos = -1;

    /**
     * A calculated number of references to this constant; useful for priority
     * based ordering of the constant pool.
     */
    private transient int m_cRefs;
    }
