package org.xvm.asm;


import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.NarrowedExprAST;
import org.xvm.asm.ast.RegAllocAST;
import org.xvm.asm.ast.RegisterAST;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * A Register represents a specific, typed, machine register of the XVM.
 */
public class Register
        implements Argument
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an unknown Register of the specified type.
     *
     * @param type    the TypeConstant specifying the Register type
     * @param sName   the name given to the register, if any; otherwise null
     * @param method  the enclosing method
     */
    public Register(TypeConstant type, String sName, MethodStructure method)
        {
        if (type == null)
            {
            throw new IllegalArgumentException("type required");
            }
        else
            {
            type = type.resolveTypedefs();
            }

        m_fRO        = false;
        m_type       = type;
        m_sName      = sName;
        m_iArg       = UNKNOWN + (method == null ? 0 : method.getUnassignedRegisterIndex());
        f_nOrigIndex = m_iArg;
        }

    /**
     * Construct a Register of the specified type.
     *
     * @param type   the TypeConstant specifying the Register type
     * @param sName  the name given to the register, if any; otherwise null
     * @param iArg   the argument index, which is either a pre-defined argument index, or a
     *               register ID
     */
    public Register(TypeConstant type, String sName, int iArg)
        {
        if (type == null)
            {
            switch (iArg)
                {
                case Op.A_DEFAULT:
                case Op.A_IGNORE:
                case Op.A_IGNORE_ASYNC:
                    break;
                default:
                    throw new IllegalArgumentException("type required");
                }
            }
        else
            {
            type = type.resolveTypedefs();
            }

        validateIndex(iArg);

        m_fRO        = isPredefinedReadonly(iArg);
        m_type       = type;
        m_sName      = sName;
        m_iArg       = iArg;
        f_nOrigIndex = iArg;
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
            assert !m_typeReg.containsDynamicType(this);
            }
        else if (m_type != null)
            {
            if (m_type.containsDynamicType(this))
                {
                // get rid of the self-referencing type elements
                m_type = m_type.resolveDynamicConstraints(this);
                }

            m_type = (TypeConstant) registry.register(m_type);
            }
        return this;
        }


    // ----- Register methods ----------------------------------------------------------------------

    /**
     * @return true iff this register represents a non-dereferenced value
     */
    public boolean isVar()
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
        return new ShadowRegister(typeNarrowed, m_sName, f_nOrigIndex);
        }

    /**
     * Create a shadow register that has the same type as the original register.
     */
    public Register restoreType()
        {
        return new ShadowRegister(getOriginalType(), m_sName, f_nOrigIndex);
        }

    /**
     * @return the original register, which could be different from "this" for narrowed registers
     */
    public Register getOriginalRegister()
        {
        return this;
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
     * @return the register name, or null
     */
    public String getName()
        {
        return m_sName;
        }

    /**
     * @return the argument index for the Register, which could be in the "unassigned" range
     */
    public int getIndex()
        {
        return m_iArg;
        }

    /**
     * Assign an argument index.
     *
     * @param iArg a valid argument index
     */
    public int assignIndex(int iArg)
        {
        if (m_iArg < UNKNOWN && m_iArg != iArg)
            {
            throw new IllegalStateException(
                "index has already been assigned (old=" + m_iArg + ", new=" + iArg);
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
        m_iArg = f_nOrigIndex;
        }

    /**
     * @return the unique id for this register which is used to differentiate registers with the
     *         same index, but within different scopes inside of a single method
     */
    public int getId()
        {
        assert f_nOrigIndex == m_iArg || f_nOrigIndex >= UNKNOWN;

        return f_nOrigIndex < UNKNOWN ? f_nOrigIndex : f_nOrigIndex - UNKNOWN;
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
            case Op.A_IGNORE_ASYNC:
                return false;
            }
        }

    /**
     * @return true iff the register is a pre-defined argument
     */
    public boolean isPredefined()
        {
        return m_iArg < 0;
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
        return m_iArg >= UNKNOWN;
        }

    /**
     * Determine if this register is readable. This is equivalent to the Ref for the register
     * supporting the get() operation.
     *
     * @return true iff this register is readable
     */
    public boolean isReadable()
        {
        return switch (m_iArg)
            {
            case Op.A_IGNORE, Op.A_IGNORE_ASYNC, Op.A_LABEL -> false;
            default                                         -> true;
            };
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
        return !isPredefined() && isReadable() && isWritable() && !isVar();
        }

    /**
     * Mark the register as "final", which disallows "set()" unless it's definitely unassigned
     */
    public void markFinal()
        {
        m_fMarkedFinal = true;
        }

    /**
     * @return true iff this register has been explicitly marked as "final"
     */
    public boolean isMarkedFinal()
        {
        return m_fMarkedFinal;
        }

    /**
     * Mark the register as "allowed to be unassigned", which overrides any access check by the
     * compiler, but may throw an "Unassigned value" exception at run-time.
     */
    public void markAllowUnassigned()
        {
        m_fMarkedUnassigned = true;
        }

    /**
     * @return true iff this register has been marked as "allow unassigned"
     */
    public boolean isAllowedUnassigned()
        {
        return m_fMarkedUnassigned;
        }

    /**
     * @return a {@link RegAllocAST} that represents this register
     */
    public RegAllocAST getRegAllocAST()
        {
        RegAllocAST astAlloc = m_astAlloc;
        if (astAlloc == null)
            {
            StringConstant constName = m_sName == null
                    ? null
                    : m_type.getConstantPool().ensureStringConstant(m_sName);
            m_astAlloc = astAlloc = m_typeReg == null
                    ? new RegAllocAST(m_type, constName)
                    : new RegAllocAST(m_typeReg, m_type, constName);
            }
        return astAlloc;
        }

    /**
     * @return a {@link RegisterAST} (or {@link NarrowedExprAST}) that represents this register
     */
    public ExprAST getRegisterAST()
        {
        assert !isStack();

        if (isPredefined())
            {
            RegisterAST regSpecial = m_astSpecial;
            if (regSpecial == null)
                {
                regSpecial = m_astSpecial = new RegisterAST(m_iArg, getType(), null);
                }
            return regSpecial;
            }

        return getRegAllocAST().getRegister();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (this == obj)
            {
            return true;
            }

        if (obj instanceof Register that)
            {
            if (that instanceof ShadowRegister)
                {
                // ShadowRegister overrides "equals"
                assert !(this instanceof ShadowRegister);
                return false;
                }

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


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return a String that denotes the identity of the register, for debugging purposes
     */
    public String getIdString()
        {
        return m_iArg >= UNKNOWN
                ? "#? (@" + System.identityHashCode(this) + ")"
                : getIdString(m_iArg);
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
        if (!(0 <= iReg || iReg < UNKNOWN || isPredefinedRegister(iReg)))
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
            case Op.A_IGNORE_ASYNC:
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
            case Op.A_IGNORE_ASYNC:
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

            default:
                return nReg < UNKNOWN
                    ? "#" + nReg
                    : "#???"; // this can happen *only* during the compilation, before the registers
                              // get assigned
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
         * @param typeNew the overriding type
         * @param sName   the name of the register being overridden (or null)
         * @param iArg    the original index
         */
        protected ShadowRegister(TypeConstant typeNew, String sName, int iArg)
            {
            super(typeNew, sName, iArg);
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
        public boolean isVar()
            {
            return Register.this.isVar();
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
        public Register restoreType()
            {
            // no reason to shadow the shadow
            TypeConstant typeOrig = getOriginalType();
            return getType().equals(typeOrig)
                    ? this
                    : Register.this.narrowType(typeOrig);
            }

        @Override
        public Register getOriginalRegister()
            {
            return Register.this;
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
        public void markFinal()
            {
            Register.this.markFinal();
            }

        @Override
        public boolean isMarkedFinal()
            {
            return Register.this.isMarkedFinal();
            }

        @Override
        public void markAllowUnassigned()
            {
            Register.this.markAllowUnassigned();
            }

        @Override
        public boolean isAllowedUnassigned()
            {
            return Register.this.isAllowedUnassigned();
            }

        @Override
        public RegAllocAST getRegAllocAST()
            {
            // shadow doens't have an "alloc" register
            throw new IllegalStateException();
            }

        @Override
        public ExprAST getRegisterAST()
            {
            NarrowedExprAST astNarrowed = m_astNarrowed;
            if (astNarrowed == null)
                {
                astNarrowed = m_astNarrowed = new NarrowedExprAST(
                        Register.this.getRegisterAST(), getType());
                }
            return astNarrowed;
            }

        @Override
        public boolean isNormal()
            {
            return Register.this.isNormal();
            }

        @Override
        public String getIdString()
            {
            return "shadow of " + Register.this;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (this == obj)
                {
                return true;
                }

            return obj instanceof ShadowRegister that
                    && this.getOriginalRegister().equals(that.getOriginalRegister())
                    && this.getType()            .equals(that.getType());
            }

        /**
         * Indicates that this register is a replacement of the original.
         */
        private boolean m_fInPlace = false;

        /**
         * Cached NarrowedExprAST.
         */
        private NarrowedExprAST m_astNarrowed;
        }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Register representing a default method argument.
     */
    public static final Register DEFAULT = new Register(null, null, Op.A_DEFAULT);

    /**
     * Register representing an "async ignore" return.
     */
    public static final Register ASYNC = new Register(null, null, Op.A_IGNORE_ASYNC);

    /**
     * An index threshold that represents an unknown or otherwise unassigned registers. Any index
     * value above this threshold is considered "unassigned".
     */
    public static final int UNKNOWN = 1_000_000_000;

    /**
     * The type of the value that will be held in the register.
     */
    private TypeConstant m_type;

    /**
     * The optional name of the register.
     */
    private String m_sName;

    /**
     * The type of the register itself (typically null).
     */
    private TypeConstant m_typeReg;

    /**
     * The register ID (>=0), or the pre-defined argument identifier in the range -1 to
     * {@link Op#CONSTANT_OFFSET).
     */
    private int m_iArg;

    /**
     * Read-only flag.
     */
    private boolean m_fRO;

    /**
     * An original register index, which is either equal to the actual index (if assigned from the
     * outset) or an offset relative to the UNKNOWN value used to compute {@link #getId() the unique id}.
     */
    private final int f_nOrigIndex;

    /**
     * Effectively final flag (implicit).
     */
    private boolean m_fEffectivelyFinal;

    /**
     * Explicitly "@Final" register (disallow set unless definitely unassigned).
     */
    private boolean m_fMarkedFinal;

    /**
     * Explicitly "@Unassigned" register (allow compiler access).
     */
    private boolean m_fMarkedUnassigned;

    /**
     * The Binary AST register allocation that is required in order for this register to exist.
     */
    private transient RegAllocAST m_astAlloc;

    /**
     * The Binary AST register for special registers.
     */
    private transient RegisterAST m_astSpecial;
    }