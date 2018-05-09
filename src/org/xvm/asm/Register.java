package org.xvm.asm;


import org.xvm.asm.constants.TypeConstant;


/**
 * A Register represents a specific, typed, machine register of the XVM.
 */
public class Register
        implements Op.Argument
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Register of the specified type.
     *
     * @param type  the TypeConstant specifying the Register type
     */
    public Register(TypeConstant type)
        {
        this(type, UNKNOWN);
        }

    /**
     * Construct a Register of the specified type.
     *
     * @param type  the TypeConstant specifying the Register type
     * @param iArg  the argument index, which is either a pre-defined argument index, or a register
     *              ID
     */
    public Register(TypeConstant type, int iArg)
        {
        this(type, iArg, false);
        }

    /**
     * Construct a Register of the specified type.
     *
     * @param type       the TypeConstant specifying the Register type
     * @param iArg       the argument index, which is either a pre-defined argument index, or a
     *                   register ID
     * @param fReadOnly  true if the register cannot be modified
     */
    public Register(TypeConstant type, int iArg, boolean fReadOnly)
        {
        if (type == null && iArg != Op.A_IGNORE)
            {
            throw new IllegalArgumentException("type required");
            }

        validateIndex(iArg);

        m_type = type;
        m_iArg = iArg;
        m_fRO  = fReadOnly || iArg != UNKNOWN && isPredefinedRegister(iArg) && iArg != Op.A_IGNORE;
        }


    // ----- Register methods ----------------------------------------------------------------------

    /**
     * Determine the type of the value that the Register holds.
     *
     * @return the Register's type
     */
    @Override
    public TypeConstant getRefType()
        {
        return m_type;
        }

    /**
     * @return the argument index for the Register
     */
    public int getIndex()
        {
        if (m_iArg == UNKNOWN)
            {
            throw new IllegalStateException("index has not been assigned!");
            }

        return m_iArg;
        }

    /**
     * Assign an argument index.
     *
     * @param iArg a valid argument index
     */
    public void assignIndex(int iArg)
        {
        if (m_iArg != UNKNOWN)
            {
            throw new IllegalStateException("index has already been assigned (old=" + m_iArg + ", new=" + iArg);
            }

        validateIndex(iArg);
        m_iArg = iArg;
        }

    /**
     * Verify that the specified argument index is valid.
     *
     * @param iReg  the argument index
     *
     * @throws IllegalArgumentException if the index is invalid
     */
    protected static void validateIndex(int iReg)
        {
        if (!(iReg >= 0 || iReg == UNKNOWN || isPredefinedRegister(iReg)))
            {
            throw new IllegalArgumentException("invalid register ID: " + iReg);
            }
        }

    /**
     * Determine if the specified argument index is for a pre-defined register.
     *
     * @param iArg  the argument index
     *
     * @return true iff the index specifies a pre-defined argument
     */
    protected static boolean isPredefinedRegister(int iArg)
        {
        switch (iArg)
            {
            case Op.A_IGNORE:
            case Op.A_PUBLIC:
            case Op.A_PROTECTED:
            case Op.A_PRIVATE:
            case Op.A_TARGET:
            case Op.A_STRUCT:
            case Op.A_FRAME:
            case Op.A_SERVICE:
            case Op.A_MODULE:
            case Op.A_TYPE:
            case Op.A_SUPER:
            case Op.A_THIS:
                return true;

            default:
                if (iArg < 0)
                    {
                    throw new IllegalArgumentException("illegal argument index: " + iArg);
                    }
                return false;
            }
        }

    /**
     * @return true iff the register is a pre-defined argument
     */
    public boolean isPredefined()
        {
        return m_iArg < 0 && m_iArg != UNKNOWN;
        }

    /**
     * Determine if this register has an "unknown" index. This is used to indicate a "next"
     * index.
     *
     * @return true iff this register has an "unknown" index
     */
    public boolean isUnknown()
        {
        return m_iArg == UNKNOWN;
        }

    /**
     * Determine if this register is readable. This is equivalent to the Ref for the register
     * supporting the get() operation.
     *
     * @return true iff this register is readable
     */
    public boolean isReadable()
        {
        return m_iArg != Op.A_IGNORE;
        }

    /**
     * Determine if this register is writable. This is equivalent to the Ref for the register
     * supporting the set() operation.
     *
     * TODO the registers for the parameters need to use the special constructor with readonly=true (currently no registers are created for parameters)
     *
     * @return true iff this register is writable
     */
    public boolean isWritable()
        {
        return !m_fRO;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (m_type != null)
            {
            sb.append(m_type.getValueString())
              .append(' ');
            }

        sb.append(getIdString());

        if (m_fRO)
            {
            sb.append(" (Read-Only)");
            }

        return sb.toString();
        }

    /**
     * @return a String that denotes the identity of the register, for debugging purposes
     */
    public String getIdString()
        {
        return m_iArg == UNKNOWN
                ? "#? (@" + System.identityHashCode(this) + ")"
                : getIdString(m_iArg);
        }

    /**
     * Format the register identifier into a String
     *
     * @param nReg  the register number
     *
     * @return an identity String, for debugging purposes
     */
    public static String getIdString(int nReg)
        {
        switch (nReg)
            {
            case Op.A_STACK:
                return "this:stack";

            case Op.A_IGNORE:
                return "_";

            case Op.A_TARGET:
                return "this:target";

            case Op.A_PUBLIC:
                return "this:public";

            case Op.A_PROTECTED:
                return "this:protected";

            case Op.A_PRIVATE:
                return "this:private";

            case Op.A_STRUCT:
                return "this:struct";

            case Op.A_SERVICE:
                return "this:service";

            case Op.A_SUPER:
                return "super";

            case UNKNOWN:
                return "#???";

            default:
                return "#" + nReg;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A singleton "black hole" register.
     */
    public static final Register IGNORE = new Register(null, Op.A_IGNORE);

    /**
     * Empty array of registers.
     */
    public static final Register[] NO_REGS = new Register[0];

    /**
     * A reserved argument index that represents an unknown or otherwise unassigned index.
     */
    static final int UNKNOWN = Integer.MIN_VALUE;

    /**
     * The type of the value that will be held in the register.
     */
    private TypeConstant m_type;

    /**
     * The register ID (>=0), or the pre-defined argument identifier in the range -1 to -16.
     */
    private int m_iArg;

    /**
     * Read-only flag.
     */
    private boolean m_fRO;
    }
