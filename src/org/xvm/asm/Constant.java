package org.xvm.asm;


import java.io.DataInput;
import java.io.IOException;
import java.io.PrintWriter;

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


    // ----- Comparable operations ---------------------------------------------

    @Override
    public int compareTo(Constant that)
        {
        if (this == that)
            {
            return 0;
            }

        int cDif = this.m_cRefs - that.m_cRefs;
        if (cDif != 0)
            {
            // most used comes first
            return -cDif;
            }

        // REVIEW can't be type because different classes have same type e.g. Conditionals
        // cDif = this.getType().ordinal() - that.getType().ordinal();
        cDif = this.getFormat().ordinal() - that.getFormat().ordinal();
        if (cDif != 0)
            {
            // arbitrary: order by types
            return cDif;
            }

        return compareDetails(that);
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
    protected int compareDetails(Constant that)
        {
        // inefficient and begs for optimization by sub-classes
        return this.toString().compareTo(that.toString());
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
        /**
         * Constant-Type: Byte (aka Octet).
         * <p>
         * Implementation-Class: {@link ConstantPool.ByteConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0] = 0
         * [1] = octet-value
         * </pre></code>
         */
        Byte,

        /**
         * Constant-Type: Byte-String (aka Octet-String, aka Binary).
         * <p>
         * Implementation-Class: {@link ConstantPool.ByteStringConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 1
         * [1..] = length of the Byte-String, in variable-length encoded format
         * [...] = octet-value of each octet in the Octet-String
         * </pre></code>
         */
        ByteString,

        /**
         * Constant-Type: Character (Unicode code-point).
         * <p>
         * Implementation-Class: {@link ConstantPool.CharConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 2
         * [1..] = Unicode code-point of the character, in UTF-8 encoded format
         * </pre></code>
         */
        Char,

        /**
         * Constant-Type: Character-String (aka String).
         * <p>
         * Implementation-Class: {@link ConstantPool.CharStringConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 3
         * [1..] = length of the UTF-8 encoding in bytes, in variable-length
         *         encoded format
         * [...] = Unicode code-point of each character of the string, in UTF-8
         *         encoded format
         * </pre></code>
         */
        CharString,

        /**
         * Constant-Type: Integer. There are two different intended use cases for
         * this particular constant format: First, to support integers of arbitrary
         * bit length that do not necessarily have a runtime type, such as when
         * defining a min and max for the parameters of the integer type itself,
         * and second, to provide a simple format for constants of the "default"
         * 64-bit signed integer type.
         * <p>
         * Implementation-Class: {@link ConstantPool.IntConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 4
         * [1..] = integer value, in variable-length encoded format
         * </pre></code>
         */
        Int,

        /**
         * Constant-Type: Parameterized Integer.
         * <p>
         * Implementation-Class: {@link ConstantPool.IntConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 5
         * [1..] = index of the Parameterized Class constant that specifies the
         *         parameterized integer type, in variable-length encoded format
         * [...] = integer value, in variable-length encoded format
         * </pre></code>
         */
        IntParmed, // TODO consider a UInt type, even though it would use the same format

        /**
         * Constant-Type: Version.
         * <p>
         * Implementation-Class: {@link ConstantPool.VersionConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = TODO
         * [1..] = TODO
         * </pre></code>
         */
        Version,

        ConditionNot,
        ConditionAll,
        ConditionAny,
        ConditionOnly1,
        ConditionNamed,
        ConditionPresent,
        ConditionVersion,

        /**
         * Constant-Type: Module.
         * <p>
         * Implementation-Class: {@link ConstantPool.ModuleConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 6
         * [1..] = index of the Character-String constant that specifies the module
         *         name, in variable-length encoded format
         * </pre></code>
         */
        Module,

        /**
         * Constant-Type: Package (or Namespace).
         * <p>
         * Implementation-Class: {@link ConstantPool.PackageConstant}
         * <p>
         * Binary-Format:
         * <p>
         * <code><pre>
         * [0]   = 7
         * [1..] = index of the Module or Package constant that contains this
         *         package, in variable-length encoded format
         * [...] = index of the Character-String constant that specifies the
         *         unqualified package name, in variable-length encoded format
         * [1..] = index of the Module that this Package serves as an alias of,
         *         (or -1 if the Package is not a module alias), in
         *         variable-length encoded format
         * </pre></code>
         */
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
         * TODO
         * REVIEW
         * [0] = 11
         */
        MethodParmed,

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

        /**
         *
         */
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


    // ----- intrinsic class identifiers ---------------------------------------

    /**
     * Enum: Predefined ClassConstants that are intrinsic to the XVM.
     */
// REVIEW what are these for?
//    enum Intrinsic
//        {
//        Byte,
//        Binary,
//        Char,
//        String,
//        Integer,
//        UInt1,
//        UInt2,
//        UInt4,
//        UInt8,
//        UInt16,
//        UInt32,
//        SInt1,
//        SInt2,
//        SInt4,
//        SInt8,
//        SInt16,
//        SInt32,
//        }


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
