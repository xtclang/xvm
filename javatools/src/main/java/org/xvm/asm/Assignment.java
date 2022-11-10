package org.xvm.asm;


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

    Assignment(boolean fUnassigned, boolean fAssigned, boolean fExactlyOnce)
        {
        assert !(fAssigned & fUnassigned);

        fSplit                = false;
        fUnassignedWhenFalse  = fUnassignedWhenTrue   = fUnassigned;
        fAssignedWhenFalse    = fAssignedWhenTrue     = fAssigned;
        fExactlyOnceWhenFalse = fExactlyOnceWhenTrue  = fExactlyOnce;
        }

    Assignment(Assignment whenFalse, Assignment whenTrue)
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
        // unassigned branch doesn't contradict "effectively final" semantics
        return fExactlyOnceWhenFalse & fExactlyOnceWhenTrue |
               fExactlyOnceWhenFalse & fUnassignedWhenTrue  |
               fUnassignedWhenFalse  & fExactlyOnceWhenTrue;
        }

    /**
     * Apply an assignment to this Assignment state.
     *
     * @return the result of assignment
     */
    public Assignment applyAssignment()
        {
        if (fSplit)
            {
            return join(this.whenFalse().applyAssignment(),
                        this.whenTrue() .applyAssignment());
            }

        return isDefinitelyUnassigned()
                ? AssignedOnce
                : Assigned;
        }

    /**
     * Apply a potentially asynchronous assignment that occurs from a lambda or anonymous inner
     * cass via a variable capture. Because the assignment is potentially asynchronous, its
     * outcome may be indeterminate.
     *
     * @return the resulting Assignment state
     */
    public Assignment applyAssignmentFromCapture()
        {
        if (fSplit)
            {
            return join(this.whenFalse().applyAssignmentFromCapture(),
                        this.whenTrue() .applyAssignmentFromCapture());
            }

        return isDefinitelyAssigned()
                ? Assigned
                : Unknown;
        }

    /**
     * Apply assignment information from an Assignment from a non-completing inner context to this
     * Assignment.
     *
     * @param that  the assignment from a non-completing inner context
     *
     * @return the resulting Assignment state
     */
    public Assignment promoteFromNonCompleting(Assignment that)
        {
        if (this == that)
            {
            return this;
            }

        if (this.fSplit || that.fSplit)
            {
            return join(this.whenFalse().promoteFromNonCompleting(that.whenFalse()),
                        this.whenTrue() .promoteFromNonCompleting(that.whenTrue()));
            }

        return this.isEffectivelyFinal() && !that.isEffectivelyFinal()
                ? forFlags(getFlags() & 0b110110)   // erase effectively final flags
                : this;
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
     * @return the negated form of this Assignment, such that the state for "when false" and
     *         "when true" are reversed
     */
    public Assignment negate()
        {
        return fSplit
                ? join(whenTrue(), whenFalse())
                : this;
        }

    /**
     * Combine information from this Assignment with information from another in a manner that
     * equally weighs the information from each.
     *
     * @param that  a second Assignment
     *
     * @return the resulting Assignment
     */
    public Assignment join(Assignment that)
        {
        return forFlags((this.fUnassignedWhenFalse  & that.fUnassignedWhenFalse  ? 0b100000 : 0)
                |       (this.fAssignedWhenFalse    & that.fAssignedWhenFalse    ? 0b010000 : 0)
                |       (this.fExactlyOnceWhenFalse & that.fExactlyOnceWhenFalse ? 0b001000 : 0)
                |       (this.fUnassignedWhenTrue   & that.fUnassignedWhenTrue   ? 0b000100 : 0)
                |       (this.fAssignedWhenTrue     & that.fAssignedWhenTrue     ? 0b000010 : 0)
                |       (this.fExactlyOnceWhenTrue  & that.fExactlyOnceWhenTrue  ? 0b000001 : 0));
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
     * Combine the portions an Assignment from a "when false" or "when true" fork with this
     * Assignment.
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
                        this.whenTrue() .joinLoop(that.whenTrue()));
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
     * Look up an Assignment enum by its ordinal.
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
    private static final Assignment[] BY_FLAGS = new Assignment[64];

    static
        {
        for (Assignment assignment : ASSIGNMENTS)
            {
            int n = assignment.getFlags();
            assert BY_FLAGS[n] == null;
            BY_FLAGS[n] = assignment;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private final boolean fSplit;
    private final boolean fUnassignedWhenFalse;
    private final boolean fAssignedWhenFalse;
    private final boolean fExactlyOnceWhenFalse;
    private final boolean fUnassignedWhenTrue;
    private final boolean fAssignedWhenTrue;
    private final boolean fExactlyOnceWhenTrue;
    }