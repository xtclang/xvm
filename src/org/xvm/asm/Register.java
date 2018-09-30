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
        if (type == null && iArg != Op.A_IGNORE)
            {
            throw new IllegalArgumentException("type required");
            }

        validateIndex(iArg);

        m_type = type;
        m_iArg = iArg;
        m_fRO  = isPredefinedReadonly(iArg);
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
     * @param pool  the ConstantPool to place a potentially created new constant into
     * @param fRO   true iff the register is read-only
     *
     * @return the type of the register
     */
    public TypeConstant ensureRegType(ConstantPool pool, boolean fRO)
        {
        assert fRO | !m_fRO;

        if (m_typeReg != null)
            {
            return m_typeReg;
            }

        return pool.ensureParameterizedTypeConstant(fRO ? pool.typeRef() : pool.typeVar(), m_type);
        }

    /**
     * Specify the type of the register itself.
     *
     * @param typeReg  the type of the register (something that "is a" Ref)
     */
    public void specifyRegType(TypeConstant typeReg)
        {
        assert m_typeReg == null;
        assert typeReg != null && typeReg.isA(typeReg.getConstantPool().typeRef());

        this.m_typeReg = typeReg;
        if (!typeReg.isA(typeReg.getConstantPool().typeVar()))
            {
            m_fRO = true;
            }
        }

    /**
     * Create a register that is collocated with this register, but narrows its type.
     */
    public Register narrowType(TypeConstant typeNarrowed)
        {
        assert typeNarrowed.isA(m_type);

        return new ShadowRegister(typeNarrowed);
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
        if (m_iArg != UNKNOWN)
            {
            throw new IllegalStateException("index has already been assigned (old=" + m_iArg + ", new=" + iArg);
            }

        validateIndex(iArg);
        return m_iArg = iArg;
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
            case Op.A_STACK:
            case Op.A_IGNORE:
            case Op.A_PUBLIC:
            case Op.A_PROTECTED:
            case Op.A_PRIVATE:
            case Op.A_TARGET:
            case Op.A_STRUCT:
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
            case Op.A_PUBLIC:
            case Op.A_PROTECTED:
            case Op.A_PRIVATE:
            case Op.A_TARGET:
            case Op.A_STRUCT:
            case Op.A_SERVICE:
            case Op.A_MODULE:
            case Op.A_TYPE:
            case Op.A_SUPER:
            case Op.A_THIS:
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
     * @return true iff the register represents "this:target" pre-defined argument
     */
    public boolean isTarget()
        {
        return m_iArg == Op.A_TARGET;
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
     * Force the register to be treated as effectively final from this point forward.
     */
    public void markEffectivelyFinal()
        {
        m_fRO               = true;
        m_fEffectivelyFinal = true;
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
     * @return true iff this register has been marked as being effectively final
     */
    public boolean isEffectivelyFinal()
        {
        return m_fEffectivelyFinal;
        }

    /**
     * @return true iff this is a normal (not D_VAR), readable and writable, local variable
     */
    public boolean isNormal()
        {
        return !isPredefined() && isReadable() && isWritable() && !isDVar();
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
        public TypeConstant ensureRegType(ConstantPool pool, boolean fRO)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public void specifyRegType(TypeConstant typeReg)
            {
            throw new UnsupportedOperationException();
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
        public boolean isTarget()
            {
            return Register.this.isTarget();
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
            return " shadow of " + Register.this.toString();
            }
        }

    // ----- inner class: Assignment ---------------------------------------------------------------

    /**
     * The Assignment enumeration represents the various possible states of a variable's assignment.
     * <p/>
     * Specifically:
     * <ul>
     * <li>If the variable is known to be definitely unassigned;</li>
     * <li>If the variable is known to be definitely assigned;</li>
     * <li>If the variable is known to be effectively final.</li>
     * </ul>
     */
    public enum Assignment
        {
        //           def    def    eff
        //           unasn  asn    final
        //           -----  -----  -----
        Unassigned  (true , false, false),      // definitely unassigned
        UnknownOnce (false, false, true ),      // not definitely assigned; effectively final
        Unknown     (false, false, false),      // not definitely assigned
        AssignedOnce(false, true , true ),      // definitely assigned; effectively final
        Assigned    (false, true , false),      // definitely assigned

        // combinations for "when false / when true"
        Unassigned_Unknown1  (Unassigned,   UnknownOnce),
        Unassigned_Unknown   (Unassigned,   Unknown),
        Unassigned_Assigned1 (Unassigned,   AssignedOnce),
        Unassigned_Assigned  (Unassigned,   Assigned),

        Unknown1_Unassigned  (UnknownOnce,  Unassigned),
        Unknown1_Unknown     (UnknownOnce,  Unknown),
        Unknown1_Assigned1   (UnknownOnce,  AssignedOnce),
        Unknown1_Assigned    (UnknownOnce,  Assigned),

        Unknown_Unassigned   (Unknown,      Unassigned),
        Unknown_Unknown1     (Unknown,      UnknownOnce),
        Unknown_Assigned1    (Unknown,      AssignedOnce),
        Unknown_Assigned     (Unknown,      Assigned),

        Assigned1_Unassigned (AssignedOnce, Unassigned),
        Assigned1_Unknown1   (AssignedOnce, UnknownOnce),
        Assigned1_Unknown    (AssignedOnce, Unknown),
        Assigned1_Assigned   (AssignedOnce, Assigned),

        Assigned_Unassigned  (Assigned,     Unassigned),
        Assigned_Unknown1    (Assigned,     UnknownOnce),
        Assigned_Unknown     (Assigned,     Unknown),
        Assigned_Assigned1   (Assigned,     AssignedOnce);

        private Assignment(boolean fUnassigned, boolean fAssigned, boolean fExactlyOnce)
            {
            assert !(fAssigned & fUnassigned);

            fSplit                = false;
            fUnassignedWhenFalse  = fUnassignedWhenTrue   = fUnassigned;
            fAssignedWhenFalse    = fAssignedWhenTrue     = fAssigned;
            fExactlyOnceWhenFalse = fExactlyOnceWhenTrue  = fExactlyOnce;
            }

        private Assignment(Assignment whenFalse, Assignment whenTrue)
            {
            fSplit                = true;
            fUnassignedWhenFalse  = whenFalse.isDefinitelyUnassigned();
            fAssignedWhenFalse    = whenFalse.isDefinitelyAssigned();
            fExactlyOnceWhenFalse = whenFalse.isEffectivelyFinal();
            fUnassignedWhenTrue   = whenTrue.isDefinitelyUnassigned();
            fAssignedWhenTrue     = whenTrue.isDefinitelyAssigned();
            fExactlyOnceWhenTrue  = whenTrue.isEffectivelyFinal();
            }

        /**
         * @return true iff the Assignment indicates definite <b>un</b>assignment
         */
        public boolean isDefinitelyUnassigned()
            {
            return fUnassignedWhenFalse & fUnassignedWhenTrue;
            }

        /**
         * @return true iff the Assignment indicates definite assignment
         */
        public boolean isDefinitelyAssigned()
            {
            return fAssignedWhenFalse & fAssignedWhenTrue;
            }

        /**
         * @return true iff the Assignment indicates "effectively final" assignment
         */
        public boolean isEffectivelyFinal()
            {
            return fExactlyOnceWhenFalse & fExactlyOnceWhenTrue;
            }

        /**
         * @return the Assignment representing the "when false" portion of this Assignment
         */
        public Assignment whenFalse()
            {
            if (!fSplit)
                {
                return this;
                }

            return forFlags((fUnassignedWhenFalse ? 0b100100 : 0)
                    |       (fAssignedWhenFalse   ? 0b010010 : 0)
                    |       (fExactlyOnceWhenFalse? 0b001001 : 0));
            }

        /**
         * @return the Assignment representing the "when true" portion of this Assignment
         */
        public Assignment whenTrue()
            {
            if (!fSplit)
                {
                return this;
                }

            return forFlags((fUnassignedWhenTrue ? 0b100100 : 0)
                    |       (fAssignedWhenTrue   ? 0b010010 : 0)
                    |       (fExactlyOnceWhenTrue? 0b001001 : 0));
            }

        /**
         * Combine an Assignment from a "when false" or "when true" fork with this Assignment.
         *
         * @param that       the Assignment representing the "when true" or "when false" fork of
         *                   this Assignment
         * @param fWhenTrue  true iff the passed Assignment represents the "when true" fork
         *
         * @return the Assignment representing the combined Assignment states
         */
        public Assignment join(Assignment that, boolean fWhenTrue)
            {
            return fWhenTrue
                    ? forFlags((this.fUnassignedWhenFalse     ? 0b100000 : 0)
                        |      (this.fAssignedWhenFalse       ? 0b010000 : 0)
                        |      (this.fExactlyOnceWhenFalse    ? 0b001000 : 0)
                        |      (that.isDefinitelyUnassigned() ? 0b000100 : 0)
                        |      (that.isDefinitelyAssigned()   ? 0b000010 : 0)
                        |      (that.isEffectivelyFinal()     ? 0b000001 : 0))
                    : forFlags((that.isDefinitelyUnassigned() ? 0b100000 : 0)
                        |      (that.isDefinitelyAssigned()   ? 0b010000 : 0)
                        |      (that.isEffectivelyFinal()     ? 0b001000 : 0)
                        |      (this.fUnassignedWhenTrue      ? 0b000100 : 0)
                        |      (this.fAssignedWhenTrue        ? 0b000010 : 0)
                        |      (this.fExactlyOnceWhenTrue     ? 0b000001 : 0));
            }

        /**
         * Combine the portions an Assignment from a "when false" or "when true" fork with this Assignment.
         *
         * @param whenFalse  the Assignment representing the Assignment status "when false"
         * @param whenTrue   the Assignment representing the Assignment status "when true"
         *
         * @return the Assignment representing the combined Assignment states
         */
        public static Assignment join(Assignment whenFalse, Assignment whenTrue)
            {
            return forFlags((whenFalse.isDefinitelyUnassigned() ? 0b100000 : 0)
                        |   (whenFalse.isDefinitelyAssigned()   ? 0b010000 : 0)
                        |   (whenFalse.isEffectivelyFinal()     ? 0b001000 : 0)
                        |   (whenTrue .isDefinitelyUnassigned() ? 0b000100 : 0)
                        |   (whenTrue .isDefinitelyAssigned()   ? 0b000010 : 0)
                        |   (whenTrue .isEffectivelyFinal()     ? 0b000001 : 0));
            }

        /**
         * If this is an assignment at the end of a single iteration of a loop, calculate what the
         * assignment would be after multiple iterations of the same.
         *
         * @param that  the state of the assignment after the loop ran a single time
         *
         * @return the Assignment representing the state of this Assignment as if the loop had been
         *         executed multiple times
         */
        public Assignment joinLoop(Assignment that)
            {
            if (this == that)
                {
                return this;
                }

            if (this.fSplit || that.fSplit)
                {
                return join(this.whenFalse().joinLoop(that.whenFalse()),
                            this.whenTrue().joinLoop(that.whenTrue()));
                }

            switch (that)
                {
                case UnknownOnce:
                    return Unknown;

                case AssignedOnce:
                    return Assigned;

                default:
                    return that;
                }
            }

        /**
         * If an Assignment contains information that differs between "when false" and "when true"
         * states, obtain the Assignment that represents the union of that information into a
         * single, consistent Assignment whose "when false" and "when true" states each represents
         * the combined form of this Assignment.
         *
         * @return an Assignment whose "when false" and "when true" states are identical, and
         *         represents the same combined state as this Assignment
         *
         */
        public Assignment demux()
            {
            if (!fSplit)
                {
                return this;
                }

            return forFlags((isDefinitelyUnassigned() ? 0b100100 : 0)
                    |       (isDefinitelyAssigned()   ? 0b010010 : 0)
                    |       (isEffectivelyFinal()     ? 0b001001 : 0));
            }

        /**
         * Apply an assignment to this Assignment state.
         *
         * @return the result of assignment
         */
        public Assignment applyAssignment()
            {
            return isDefinitelyUnassigned()
                    ? AssignedOnce
                    : Assigned;
            }

//        /**
//         * Apply an assignment, but only to the "when false" portion of this Assignment state.
//         *
//         * @return the result of assignment only "when false"
//         */
//        public Assignment applyAssignmentWhenFalse()
//            {
//            return join(whenFalse().applyAssignment(), whenTrue());
//            }
//
//        /**
//         * Apply an assignment, but only to the "when true" portion of this Assignment state.
//         *
//         * @return the result of assignment only "when true"
//         */
//        public Assignment applyAssignmentWhenTrue()
//            {
//            return join(whenFalse(), whenTrue().applyAssignment());
//            }

        /**
         * Apply a potentially asynchronous assignment that occurs from a lambda or anonymous inner
         * cass via a variable capture. Because the assignment is potentially asynchronous, its
         * outcome may be indeterminate.
         *
         * @return the resulting Assignment state
         */
        public Assignment applyAssignmentFromCapture()
            {
            return isDefinitelyAssigned()
                    ? Assigned
                    : Unknown;
            }

        /**
         * Look up a Assignment enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Assignment enum for the specified ordinal
         */
        public static Assignment valueOf(int i)
            {
            return ASSIGNMENTS[i];
            }

        /**
         * All of the Assignment enums.
         */
        private static final Assignment[] ASSIGNMENTS = Assignment.values();

        private int getFlags()
            {
            return    (fUnassignedWhenFalse  ? 0b100000 : 0)
                    | (fAssignedWhenFalse    ? 0b010000 : 0)
                    | (fExactlyOnceWhenFalse ? 0b001000 : 0)
                    | (fUnassignedWhenTrue   ? 0b000100 : 0)
                    | (fAssignedWhenTrue     ? 0b000010 : 0)
                    | (fExactlyOnceWhenTrue  ? 0b000001 : 0);
            }

        private static Assignment forFlags(int nFlags)
            {
            Assignment assignment = BY_FLAGS[nFlags];
            assert assignment != null;
            return assignment;
            }

        /**
         * All of the Assignment enums, indexed by their boolean flags.
         */
        private static final Assignment[] BY_FLAGS    = new Assignment[64];
        static
            {
            for (Assignment assignment : ASSIGNMENTS)
                {
                int n = assignment.getFlags();
                assert BY_FLAGS[n] == null;
                BY_FLAGS[n] = assignment;
                }
            }

        private final boolean fSplit;
        private final boolean fUnassignedWhenFalse;
        private final boolean fAssignedWhenFalse;
        private final boolean fExactlyOnceWhenFalse;
        private final boolean fUnassignedWhenTrue;
        private final boolean fAssignedWhenTrue;
        private final boolean fExactlyOnceWhenTrue;
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
    }
