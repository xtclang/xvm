package org.xvm.asm;


import org.xvm.asm.constants.TypeConstant;


/**
 * A Register represents a specific, typed, machine register of the XVM.
 */
public class Register
        implements Argument
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
        if (type == null && iArg != Op.A_DEFAULT)
            {
            throw new IllegalArgumentException("type required");
            }

        validateIndex(iArg);

        m_type = type;
        m_iArg = iArg;
        m_fRO  = isPredefinedReadonly(iArg);
        m_fOriginallyUnknown = iArg == UNKNOWN;
        }

    /**
     * Mark this register as an in-place replacement of the original.
     */
    public void markInPlace()
        {
        throw new IllegalStateException();
        }

    /**
     * @return true iff this register is an in-place replacement of the original
     */
    public boolean isInPlace()
        {
        return true;
        }


    // ----- Argument methods ----------------------------------------------------------------------

    /**
     * Determine the type of the value that the Register holds.
     *
     * @return the Register's type
     */
    @Override
    public TypeConstant getType()
        {
        return m_type;
        }

    @Override
    public boolean isStack()
        {
        return m_iArg == Op.A_STACK;
        }

    @Override
    public Register registerConstants(Op.ConstantRegistry registry)
        {
        if (m_typeReg != null)
            {
            m_typeReg = (TypeConstant) registry.register(m_typeReg);
            }
        else if (m_type != null)
            {
            m_type = (TypeConstant) registry.register(m_type);
            }
        return this;
        }


    // ----- Register methods ----------------------------------------------------------------------

    /**
     * @return true iff this register was created by a DVAR op
     */
    public boolean isDVar()
        {
        return m_typeReg != null;
        }

    /**
     * Obtain a register type.
     *
     * @param fRO  true iff the register is read-only
     *
     * @return the type of the register
     */
    public TypeConstant ensureRegType(boolean fRO)
        {
        assert fRO | !m_fRO;

        if (m_typeReg != null)
            {
            return m_typeReg;
            }

        ConstantPool pool = m_type.getConstantPool();
        return pool.ensureParameterizedTypeConstant(fRO ? pool.typeRef() : pool.typeVar(), m_type);
        }

    /**
     * Specify the type of the register itself.
     *
     * @param typeReg  the type of the register (something that "is a" Ref)
     */
    public void specifyRegType(TypeConstant typeReg)
        {
        ConstantPool pool = typeReg.getConstantPool();

        // annotated types may re-resolve the annotation arguments
        assert m_typeReg == null || typeReg.isAnnotated();
        assert typeReg.isA(pool.typeRef());

        this.m_typeReg = typeReg;
        if (!typeReg.isA(pool.typeVar()))
            {
            m_fRO = true;
            }
        }

    public void specifyActualType(TypeConstant type)
        {
        assert type != null && type.isA(m_type);
        m_type = type;
        }

    /**
     * Create a register that is collocated with this register, but narrows its type.
     * <p/>
     * To be technically correct, there are scenarios when typeNarrowed is actually wider than
     * the original type. Imagine a following Ecstasy code:
     *   <pre><code>
     *   Element el = ...;
     *   Consumer<Element> consumer1 = new Consumer(Element>();
     *   if (Element.is(Type<Int>))
     *       {
     *       Consumer<Element> consumer2 = new Consumer(Element);
     *       }
     *   </code></pre>
     *
     * In the enclosed "if" context it's known that the Element is an Int, which makes
     * consumer2 not assignable to (not narrower than) consumer1, but in effect wider.
     *
     * @param typeNarrowed  the new register type
     *
     * @return a shadow of the original register reflecting the new type
     */
    public Register narrowType(TypeConstant typeNarrowed)
        {
        // even when the types are the same, the shadow carries "not-in-place" flag
        return new ShadowRegister(typeNarrowed);
        }

    /**
     * @return the original type, which could be different from {@link #getType} for narrowed
     *         registers
     */
    public TypeConstant getOriginalType()
        {
        return getType();
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
    public int assignIndex(int iArg)
        {
        if (m_iArg != UNKNOWN && m_iArg != iArg)
            {
            throw new IllegalStateException("index has already been assigned (old=" + m_iArg + ", new=" + iArg);
            }

        validateIndex(iArg);
        return m_iArg = iArg;
        }

    /**
     * Reset the register to an unknown index to allow for a re-run of the simulation that assigns
     * variable indexes.
     */
    public void resetIndex()
        {
        if (m_fOriginallyUnknown)
            {
            m_iArg = UNKNOWN;
            }
        }

    /**
     * Determine if the specified argument index is for a pre-defined read-only register.
     *
     * @param iArg  the argument index
     *
     * @return true iff the index specifies a pre-defined argument that is in a read-only register
     */
    protected static boolean isPredefinedReadonly(int iArg)
        {
        switch (iArg)
            {
            case Op.A_DEFAULT:
            case Op.A_PUBLIC:
            case Op.A_PROTECTED:
            case Op.A_PRIVATE:
            case Op.A_THIS:
            case Op.A_TARGET:
            case Op.A_STRUCT:
            case Op.A_CLASS:
            case Op.A_SERVICE:
            case Op.A_SUPER:
            case Op.A_LABEL:
                return true;

            default:
            case Op.A_STACK:
            case Op.A_IGNORE:
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
     * @return true iff the register represents "super" pre-defined argument
     */
    public boolean isSuper()
        {
        return m_iArg == Op.A_SUPER;
        }

    /**
     * @return true iff the register represents "this:struct" pre-defined argument
     */
    public boolean isStruct()
        {
        return m_iArg == Op.A_STRUCT;
        }

    /**
     * @return true iff the register represents a label
     */
    public boolean isLabel()
        {
        return m_iArg == Op.A_LABEL;
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
        return m_iArg != Op.A_IGNORE && m_iArg != Op.A_LABEL;
        }

    /**
     * Determine if this register is writable. This is equivalent to the Ref for the register
     * supporting the set() operation.
     *
     * @return true iff this register is writable
     */
    public boolean isWritable()
        {
        return !m_fRO;
        }

    /**
     * Force the register to be treated as effectively final from this point forward.
     */
    public void markEffectivelyFinal()
        {
        m_fRO               = true;
        m_fEffectivelyFinal = true;
        }

    /**
     * @return true iff this register has been marked as being effectively final
     */
    public boolean isEffectivelyFinal()
        {
        return m_fEffectivelyFinal;
        }

    /**
     * @return true iff this is a normal (not D_VAR), readable and writable, local variable (and
     *         not the stack)
     */
    public boolean isNormal()
        {
        return !isPredefined() && isReadable() && isWritable() && !isDVar();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (this == obj)
            {
            return true;
            }

        if (obj instanceof Register)
            {
            Register that = (Register) obj;
            return this.m_iArg              == that.m_iArg
                && this.m_fRO               == that.m_fRO
                && this.m_fEffectivelyFinal == that.m_fEffectivelyFinal
                && this.isInPlace()         == that.isInPlace()
                && this.getType().equals(      that.getType());
            }
        return false;
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

        if (m_fEffectivelyFinal)
            {
            sb.append("@Final ");
            }
        else if (m_fRO)
            {
            sb.append("@RO ");
            }

        sb.append(getIdString());

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


    // ----- helper methods ------------------------------------------------------------------------

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
            case Op.A_STACK:
            case Op.A_IGNORE:
            case Op.A_DEFAULT:
            case Op.A_PUBLIC:
            case Op.A_PROTECTED:
            case Op.A_PRIVATE:
            case Op.A_THIS:
            case Op.A_TARGET:
            case Op.A_STRUCT:
            case Op.A_CLASS:
            case Op.A_SERVICE:
            case Op.A_SUPER:
            case Op.A_LABEL:
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

            case Op.A_DEFAULT:
                return "<default>";

            case Op.A_THIS:
                return "this";

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

            case Op.A_CLASS:
                return "this:class";

            case Op.A_SERVICE:
                return "this:service";

            case Op.A_SUPER:
                return "super";

            case Op.A_LABEL:
                return "<label>";

            case UNKNOWN:
                return "#???";

            default:
                return "#" + nReg;
            }
        }


    // ----- inner classes -------------------------------------------------------------------------

    /**
     * A register that represents the underlying (base) register, but overrides its type.
     */
    private class ShadowRegister
            extends Register
        {
        /**
         * Create a ShadowRegister of the specified type.
         *
         * @param typeNew  the overriding type
         */
        protected ShadowRegister(TypeConstant typeNew)
            {
            super(typeNew);
            }

        @Override
        public TypeConstant getType()
            {
            // the narrowed type
            return super.getType();
            }

        @Override
        public Register registerConstants(Op.ConstantRegistry registry)
            {
            // register the narrowed type
            return super.registerConstants(registry);
            }

        @Override
        public void markInPlace()
            {
            m_fInPlace = true;
            }

        @Override
        public boolean isInPlace()
            {
            return m_fInPlace;
            }

        @Override
        public boolean isStack()
            {
            return Register.this.isStack();
            }

        @Override
        public boolean isDVar()
            {
            return Register.this.isDVar();
            }

        @Override
        public TypeConstant ensureRegType(boolean fRO)
            {
            // the register type is using against the narrowed type
            return super.ensureRegType(fRO);
            }

        @Override
        public void specifyRegType(TypeConstant typeReg)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public void specifyActualType(TypeConstant type)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public Register narrowType(TypeConstant typeNarrowed)
            {
            // no reason to shadow the shadow
            return Register.this.narrowType(typeNarrowed);
            }

        @Override
        public TypeConstant getOriginalType()
            {
            return Register.this.getOriginalType();
            }

        @Override
        public int getIndex()
            {
            return Register.this.getIndex();
            }

        @Override
        public int assignIndex(int iArg)
            {
            return Register.this.assignIndex(iArg);
            }

        @Override
        public boolean isPredefined()
            {
            return Register.this.isPredefined();
            }

        @Override
        public boolean isLabel()
            {
            return false;
            }

        @Override
        public boolean isUnknown()
            {
            return Register.this.isUnknown();
            }

        @Override
        public boolean isReadable()
            {
            return Register.this.isReadable();
            }

        @Override
        public boolean isWritable()
            {
            return Register.this.isWritable();
            }

        @Override
        public void markEffectivelyFinal()
            {
            Register.this.markEffectivelyFinal();
            }

        @Override
        public boolean isEffectivelyFinal()
            {
            return Register.this.isEffectivelyFinal();
            }

        @Override
        public boolean isNormal()
            {
            return Register.this.isNormal();
            }

        @Override
        public String getIdString()
            {
            return "shadow of " + Register.this.toString();
            }

        /**
         * Indicates that this register is a replacement of the original.
         */
        protected boolean m_fInPlace = false;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of registers.
     */
    public static final Register[] NO_REGS = new Register[0];

    /**
     * Register representing a default method argument.
     */
    public static final Register DEFAULT = new Register(null, Op.A_DEFAULT);

    /**
     * A reserved argument index that represents an unknown or otherwise unassigned index.
     */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /**
     * The type of the value that will be held in the register.
     */
    private TypeConstant m_type;

    /**
     * The type of the register itself (typically null).
     */
    private TypeConstant m_typeReg;

    /**
     * The register ID (>=0), or the pre-defined argument identifier in the range -1 to -16.
     */
    private int m_iArg;

    /**
     * Read-only flag.
     */
    private boolean m_fRO;

    /**
     * Effectively final flag.
     */
    private boolean m_fEffectivelyFinal;

    /**
     * A record of whether the register was created without its index being known.
     */
    private boolean m_fOriginallyUnknown;
    }
