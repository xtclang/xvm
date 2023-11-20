package org.xvm.asm.constants;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.Version;

import org.xvm.util.ListMap;
import org.xvm.util.LongList;


/**
 * Represents a condition that can be evaluated at link-time. Conditional constants are used to
 * encode boolean expressions into VM structures themselves, allowing structures and portions of
 * structures to be conditionally present at runtime. This has the net effect of supporting the same
 * "conditional compilation" as a pre-processor would provide for a language such as C/C++, but
 * instead of compiling only one "path" through the specified conditionals, Ecstasy compiles all of the
 * paths, verifying that every single path meets the requirements of the language and the
 * dependencies implied by (and deferred to) link-time. Among other purposes, this allows Ecstasy code
 * to be tailored to the presence (or absence) of a specific library, such as only implementing a
 * library-specific interface and only consuming library functionality if that particular library is
 * present. Additionally, multiple versions of VM structures can be combined into a single VM
 * structure (for example, multiple versions of a module can be combined into a single module), by
 * using version conditions to delineate the differences among versions.
 * <p/>
 * Structural inclusion/exclusion occurs when a conditional constant is referenced by another VM
 * structure, indicating that the presence at runtime of the VM structure depends on the result of
 * the evaluation of the conditional constant. Similarly, logical inclusion/exclusion occurs when a
 * conditional constant is referenced by an Ecstasy op-code, indicating that the presence at runtime of
 * that particular block of code depends on the result of the evaluation of the conditional
 * constant.
 * <p/>
 * Three basic conditional constants exist to test for specific conditions:
 * <p/>
 * <ul>
 * <li>{@link NamedCondition NamedCondition} - similar in concept to the use of {@code #ifdef} in
 *     the C/C++ pre-processor, a NamedCondition evaluates to true iff the specified name is
 *     defined;</li>
 * <li>{@link PresentCondition PresentCondition} - evaluates to true iff the specified VM structure
 *     (and optionally a particular version of that VM structure) is present at runtime, allowing
 *     optional dependencies to be supported;</li>
 * <li>{@link VersionedCondition VersionedCondition} - evaluates to true iff the version of this
 *     module is of a specified version.</li>
 * </ul>
 * <p/>
 * Three additional conditional constants support the composition of other conditions:
 * <ul>
 * <li>{@link NotCondition NotCondition} - evaluates to true iff the specified condition evaluates
 *     to false, i.e. a "not" condition;</li>
 * <li>{@link AllCondition AllCondition} - evaluates to true iff each of the specified conditions
 *     evaluate to true, i.e. an "and" condition;</li>
 * <li>{@link AnyCondition AnyCondition} - evaluates to false iff each of the specified conditions
 *     evaluate to false, i.e. an "or" condition;</li>
 * </ul>
 */
public abstract class ConditionalConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ConditionalConstant.
     *
     * @param pool the ConstantPool that will contain this Constant
     */
    protected ConditionalConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Evaluate this condition for inclusion in a container whose context is provided.
     *
     * @param ctx the context of the container being created
     *
     * @return true whether this condition is met in the container
     */
    public abstract boolean evaluate(LinkerContext ctx);

    /**
     * Used to brute-force evaluate every possible condition without using a LinkerContext.
     *
     * @param n the test number
     *
     * @return the result for this condition
     */
    public boolean testEvaluate(long n)
        {
        assert isTerminal();
        return iTest < 0
                ? (n & (1L << (-1 - iTest))) == 0
                : (n & (1L << iTest)) != 0;
        }

    /**
     * Determine the set of terminal conditions that make up this ConditionalConstant.
     *
     * @return a set of terminals that are referenced by this ConditionalConstant
     */
    public Set<ConditionalConstant> terminals()
        {
        if (isTerminal())
            {
            return Collections.singleton(this);
            }

        Set<ConditionalConstant> terminals = new HashSet<>();
        collectTerminals(terminals);
        return terminals;
        }

    /**
     * Determine the set of terminal conditions that make up this ConditionalConstant.
     *
     * @param terminals
     * @return a set of terminals that are referenced by this ConditionalConstant
     */
    protected void collectTerminals(Set<ConditionalConstant> terminals)
        {
        assert isTerminal();
        terminals.add(this);
        }

    /**
     * Determine if the specified terminal ConditionalConstant is present in this
     * ConditionalConstant.
     *
     * @param that a ConditionalConstant
     * @return true iff the specified ConditionalConstant is found inside this ConditionalConstant
     */
    public boolean containsTerminal(ConditionalConstant that)
        {
        return this.equals(that);
        }

    /**
     * @return true iff this is a terminal ConditionalConstant
     */
    public boolean isTerminal()
        {
        return false;
        }

    /**
     * Determine the relation between two terminal ConditionalConstant objects.
     *
     * @param that
     * @return
     */
    public Relation calcRelation(ConditionalConstant that)
        {
        assert !isTerminal();
        throw new UnsupportedOperationException("only terminal ConditionalConstants are supported");
        }

    /**
     * Determine the versions specified for the ConditionalConstant, if any.
     * <p>
     * A conditional can include a version in one of three ways:
     * <ul>
     * <li>A VersionedCondition;</li>
     * <li>An AnyCondition that contains one or more VersionConditions; or</li>
     * <li>An AllCondition that contains exactly one of the above.</li>
     * </ul>
     *
     * @return a Set of Version objects
     */
    public Set<Version> versions()
        {
        return Collections.emptySet();
        }

    /**
     * Add the specified version to this conditional.
     *
     * @param ver the version to add
     *
     * @return a new conditional
     */
    public ConditionalConstant addVersion(Version ver)
        {
        if (versions().contains(ver))
            {
            return this;
            }

        ConstantPool pool = getConstantPool();
        return new AllCondition(pool, this, pool.ensureVersionedCondition(ver));
        }

    /**
     * Remove the specified version from this conditional.
     *
     * @param ver the version to remove
     *
     * @return a new conditional
     */
    public ConditionalConstant removeVersion(Version ver)
        {
        return this;
        }

    /**
     * Obtain a ConditionalConstant that represents the union of {@code this} and {@code that}
     * condition.
     *
     * @param that another condition
     *
     * @return a condition representing the "and" of the {@code this} and {@code that} conditions
     */
    public ConditionalConstant addAnd(ConditionalConstant that)
        {
        if (this.equals(that))
            {
            return this;
            }

        if (this instanceof AllCondition || that instanceof AllCondition)
            {
            // collect a unique set of conditions
            ListMap<ConditionalConstant, ConditionalConstant> conds = new ListMap<>();
            if (this instanceof AllCondition thisAll)
                {
                for (ConditionalConstant cond : thisAll.m_aconstCond)
                    {
                    conds.putIfAbsent(cond, cond);
                    }
                }
            else
                {
                conds.put(this, this);
                }

            if (that instanceof AllCondition thatAll)
                {
                for (ConditionalConstant cond : thatAll.m_aconstCond)
                    {
                    conds.putIfAbsent(cond, cond);
                    }
                }
            else
                {
                conds.putIfAbsent(that, that);
                }

            if (conds.size() == 1)
                {
                return conds.keySet().iterator().next();
                }

            return new AllCondition(getConstantPool(), conds.keySet().toArray(new ConditionalConstant[0]));
            }

        return new AllCondition(getConstantPool(), this, that);
        }

    /**
     * Obtain a ConditionalConstant that represents the option of {@code this} or {@code that}
     * condition.
     *
     * @param that another condition
     *
     * @return a condition representing the "or" of the {@code this} and {@code that} conditions
     */
    public ConditionalConstant addOr(ConditionalConstant that)
        {
        if (this.equals(that))
            {
            return this;
            }

        if (this instanceof AnyCondition || that instanceof AnyCondition)
            {
            // collect a unique set of conditions
            ListMap<ConditionalConstant, ConditionalConstant> conds = new ListMap<>();
            if (this instanceof AnyCondition thisAny)
                {
                for (ConditionalConstant cond : thisAny.m_aconstCond)
                    {
                    conds.putIfAbsent(cond, cond);
                    }
                }
            else
                {
                conds.put(this, this);
                }

            if (that instanceof AnyCondition thatAny)
                {
                for (ConditionalConstant cond : thatAny.m_aconstCond)
                    {
                    conds.putIfAbsent(cond, cond);
                    }
                }
            else
                {
                conds.putIfAbsent(that, that);
                }

            if (conds.size() == 1)
                {
                return conds.keySet().iterator().next();
                }

            return new AnyCondition(getConstantPool(), conds.keySet().toArray(new ConditionalConstant[0]));
            }

        return new AnyCondition(getConstantPool(), this, that);
        }

    /**
     * @return a negation of this ConditionalConstant
     */
    public ConditionalConstant negate()
        {
        return getConstantPool().ensureNotCondition(this);
        }

    /**
     * @return true if the ConditionalConstant will use brute force to determine the influences of
     *         the terminal conditions
     */
    public boolean isTerminalInfluenceBruteForce()
        {
        assert isTerminal();
        return false;
        }

    /**
     * Try to figure out if the combination of logic is "finessable" such that brute force isn't
     * necessary.
     *
     * @param fInNot      true if we're nested under a NotCondition
     * @param setSimple   (input/output) collect the terminals that occur outside of NotConditions
     * @param setComplex  (input/output) collect the terminals that occur inside a NotCondition
     *
     * @return true iff this ConditionalConstant can calculate its terminal influence without
     *         resorting to brute force
     */
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex)
        {
        assert isTerminal();
        if (fInNot)
            {
            // we're inside some convoluted logic, with at least one negation going on, so if this
            // condition is already referenced at all, then give up on the hope to finesse
            if (setSimple.contains(this) || setComplex.contains(this))
                {
                return false;
                }

            setComplex.add(this);
            return true;
            }

        // we're not inside convoluted logic, so as long as this condition isn't already in the
        // convoluted list, we're ok
        if (setComplex.contains(this))
            {
            return false;
            }

        // this is finessable; register this condition in the non-convoluted list
        setSimple.add(this);
        return true;
        }

    /**
     * Calculate the influence of each terminal condition on the result of the conditional.
     * <p/>
     * This is the <a href="https://en.wikipedia.org/wiki/Boolean_satisfiability_problem">Boolean
     * Satisfiability Problem</a>.
     *
     * @return a map from each terminal condition to its corresponding Influence
     */
    public Map<ConditionalConstant, Influence> terminalInfluences()
        {
        if (isTerminal())
            {
            return Collections.singletonMap(this, Influence.IDENTITY);
            }

        Set<ConditionalConstant> terminals  = terminals();
        int                      cTerminals = terminals.size();

        ConditionalConstant[] acond     = new ConditionalConstant[cTerminals];
        LongList              skipMasks = new LongList();
        LongList              skipPtrns = new LongList();
        int                   cConds    = 0;

        NextTerminal: for (ConditionalConstant terminal : terminals)
            {
            Map<Integer, Relation> conflicts = null;
            for (int i = 0; i < cConds; ++i)
                {
                Relation rel = acond[i].calcRelation(terminal);
                switch (rel)
                    {
                    case INDEP:
                        break;

                    case EQUIV:
                        terminal.iTest = i;
                        continue NextTerminal;

                    case INVERSE:
                        terminal.iTest = -1 - i;
                        continue NextTerminal;

                    case MUTEX:
                    case MUTIN:
                    case IMPLIES:
                    case IMPLIED:
                        if (conflicts == null)
                            {
                            conflicts = new ListMap<>();
                            }
                        conflicts.put(i, rel);
                        break;
                    }
                }

            int iCondThis    = cConds++;
            terminal.iTest   = iCondThis;
            acond[iCondThis] = terminal;

            if (conflicts != null)
                {
                for (Map.Entry<Integer, Relation> entry : conflicts.entrySet())
                    {
                    int iCondThat = entry.getKey();
                    skipMasks.add((1L << iCondThat) | (1L << iCondThis));
                    switch (entry.getValue())
                        {
                        case MUTEX:
                            // can't both be true
                            skipPtrns.add((1L << iCondThat) | (1L << iCondThis));
                            break;

                        case MUTIN:
                            // can't both be false
                            skipPtrns.add(0L);
                            break;

                        case IMPLIES:
                            // if that is true, then this can't be false (that implies this to
                            // be true, i.e. this is implied to be true by that being true)
                            skipPtrns.add(1L << iCondThat);
                            break;

                        case IMPLIED:
                            // that can't be false if this is true (that is implied to be true
                            // by this being true)
                            skipPtrns.add(1L << iCondThis);
                            break;
                        }
                    }
                }
            }

        long[] aResultFF = new long[cConds];
        long[] aResultFT = new long[cConds];
        long[] aResultTF = new long[cConds];
        long[] aResultTT = new long[cConds];
        bruteForce(cConds, skipMasks.toArray(), skipPtrns.toArray(), aResultFF, aResultFT, aResultTF, aResultTT);

        Map<ConditionalConstant, Influence> influences = new HashMap<>();
        for (ConditionalConstant terminal : terminals)
            {
            int     iCond   = terminal.iTest;
            boolean fInvert = iCond < 0;
            if (fInvert)
                {
                iCond = -1 - iCond;
                }

            Influence influence = Influence.translate(
                    aResultFF[iCond], aResultFT[iCond], aResultTF[iCond], aResultTT[iCond]);
            influences.put(terminal, fInvert ? influence.inverse() : influence);
            }

        return influences;
        }

    /**
     * Brute force test every single possible input on this condition.
     * <p/>
     * This is broken out in a hope that it will be easier for the JVM to optimize.
     *
     * @param cConds     the number of input conditions being tested
     * @param amaskSkip  the masks to check for skipping specific tests
     * @param aptrnSkip  the patterns associated with the masks for determining which tests to skip
     * @param aResultFF  the number of tests with a false condition input and a false result;
     *                   this is an output parameter, indexed by condition
     * @param aResultFT  the number of tests with a false condition input and a true result;
     *                   this is an output parameter, indexed by condition
     * @param aResultTF  the number of tests with a true condition input and a false result;
     *                   this is an output parameter, indexed by condition
     * @param aResultTT  the number of tests with a true condition input and a true result;
     *                   this is an output parameter, indexed by condition
     *
     * @return the number of tests run
     */
    private void bruteForce(int cConds, long[] amaskSkip, long[] aptrnSkip,
            long[] aResultFF,  long[] aResultFT, long[] aResultTF, long[] aResultTT)
        {
        assert cConds > 0;
        int     cSkips    = amaskSkip.length;
        boolean fSkips    = cSkips > 0;
        NextTest: for (long nTest = 0L, cIters = (1L << cConds); nTest < cIters; ++nTest)
            {
            if (fSkips)
                {
                for (int iSkip = 0; iSkip < cSkips; ++iSkip)
                    {
                    if ((nTest & amaskSkip[iSkip]) == aptrnSkip[iSkip])
                        {
                        continue NextTest;
                        }
                    }
                }

            if (testEvaluate(nTest))
                {
                // result is true
                for (int iCond = 0; iCond < cConds; ++iCond)
                    {
                    if ((nTest & (1L << iCond)) != 0)
                        {
                        // input is true, result is true
                        ++aResultTT[iCond];
                        }
                    else
                        {
                        // input is false, result is true
                        ++aResultFT[iCond];
                        }
                    }
                }
            else
                {
                // result is false
                for (int iCond = 0; iCond < cConds; ++iCond)
                    {
                    if ((nTest & (1L << iCond)) != 0)
                        {
                        // input is true, result is false
                        ++aResultTF[iCond];
                        }
                    else
                        {
                        // input is false, result is false
                        ++aResultFF[iCond];
                        }
                    }
                }
            }
        }

    /**
     * @return {@link Influence#ALWAYS_F} to indicate that the result of the condition is always
     *         false, {@link Influence#ALWAYS_T} to indicate that the result of the condition is
     *         always true, or {@link Influence#CONTRIB} to indicate that the result of the
     *         condition depends on the values of its terminal conditions
     */
    public Influence getSatisfiability()
        {
        Influence result = Influence.NONE;

        for (Influence influence : terminalInfluences().values())
            {
            switch (influence)
                {
                case ALWAYS_F:
                case ALWAYS_T:
                    assert result == Influence.NONE || result == influence;
                    result = influence;
                    break;

                default:
                    return Influence.CONTRIB;
                }
            }

        return result;
        }

    /**
     * Evaluate this condition against that condition to determine how to minimally bifurcate.
     * TODO examples
     *
     * @param that  a different ConditionalConstant from this, that may or may not be an extension
     *              of this ConditionalConstant (or of the negation of this ConditionalConstant)
     *
     * @return a Bifurcation object that indicates how to minimally bifurcate
     */
    public Bifurcation bifurcate(ConditionalConstant that)
        {
        // unwrap a single negation of either this or that
        boolean fNegate = false;
        ConditionalConstant condThis = this;
        if (condThis instanceof NotCondition condNot)
            {
            fNegate  = !fNegate;
            condThis = condNot.getUnderlyingCondition();
            }
        ConditionalConstant condThat = that;
        if (condThat instanceof NotCondition condNot)
            {
            fNegate  = !fNegate;
            condThat = condNot.getUnderlyingCondition();
            }

        // easiest case is when both are terminals
        if (condThis.isTerminal() && condThat.isTerminal())
            {
            switch (condThis.calcRelation(condThat))
                {
                case INVERSE:
                    fNegate = !fNegate;
                    // fall through
                case EQUIV:
                    return new Bifurcation(!fNegate, null, fNegate, null);

                default:
                    return new Bifurcation(true, condThat, true, condThat);
                }
            }

        // a common case generated by the compiler is when that contains this (or often !this)
        ConditionalConstant thisNeg = this.negate();
        if (that instanceof AllCondition thatAll && that.terminals().containsAll(this.terminals()))
            {
            // first look for "this" and "!this" in the AllCondition
            ConditionalConstant[] acondThat = thatAll.m_aconstCond;
            for (int i = 0, c = acondThat.length; i < c; ++i)
                {
                ConditionalConstant cond = acondThat[i];
                if (cond.equals(this))
                    {
                    return new Bifurcation(true, thatAll.remove(i), false, null);
                    }

                if (cond.equals(thisNeg))
                    {
                    return new Bifurcation(false, null, true, thatAll.remove(i));
                    }
                }

            // it's possible that this condition shows up in that condition by way of merging two
            // all conditions
            MatchPieces: if (this instanceof AllCondition thisAll)
                {
                ConditionalConstant   condReduced = that;
                ConditionalConstant[] acondThis   = thisAll.m_aconstCond;
                if (acondThis.length < acondThat.length)
                    {
                    for (int i = 0, c = acondThis.length; i < c; ++i)
                        {
                        MultiCondition condBefore = (MultiCondition) condReduced;
                        condReduced = condBefore.remove(acondThis[i]);
                        if (condReduced == condBefore)
                            {
                            break MatchPieces;
                            }
                        }
                    return new Bifurcation(true, condReduced, false, null);
                    }
                }
            }

        // hardest case is when they're both non-terminals, and "this" doesn't show up in an obvious
        // manner inside of "that"
        Influence influenceTrue  = this.addAnd(that).getSatisfiability();
        Influence influenceFalse = thisNeg.addAnd(that).getSatisfiability();
        return new Bifurcation(influenceTrue != Influence.ALWAYS_F, condThat,
                               influenceFalse != Influence.ALWAYS_F, condThat);
        }


    // ----- Constant methods ----------------------------------------------------------------------


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "condition=" + getValueString();
        }


    // ----- Relation enum -------------------------------------------------------------------------

    /**
     * Given that a condition must be able to evaluate to either false or to true, two conditions
     * can be related in one of seven ways:
     *
     * <ul>
     * <li><b>Independent:</b> <i>(Unrelated)</i> This condition is independent of that
     *     condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F | x │ x |
     *       that   +===+===+
     *            T | x │ x |
     *              +===+===+
     *      </pre></code>
     * <li><b>Equivalent:</b> This condition is equivalent to that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F | x │   |
     *       that   +===+===+
     *            T |   │ x |
     *              +===+===+
     *      </pre></code>
     * <li><b>Inverse:</b> This condition is the inverse of that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F |   │ x |
     *       that   +===+===+
     *            T | x │   |
     *              +===+===+
     *      </pre></code>
     * <li><b>Mutual-Exclusion:</b> This condition is mutually exclusive with that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F | x │ x |
     *       that   +===+===+
     *            T | x │   |
     *              +===+===+
     *      </pre></code>
     * <li><b>Mutual-Inclusion:</b> This condition is mutually inclusive with that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F |   │ x |
     *       that   +===+===+
     *            T | x │ x |
     *              +===+===+
     *      </pre></code>
     * <li><b>Implies:</b> If this is true, it implies that is true;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F | x │   |
     *       that   +===+===+
     *            T | x │ x |
     *              +===+===+
     *      </pre></code>
     * <li><b>Implied:</b> If that is true, this is implied to be true.</li>
     *     <code><pre>
     *                this
     *                F   T
     *              +===+===+
     *            F | x │ x |
     *       that   +===+===+
     *            T |   │ x |
     *              +===+===+
     *      </pre></code>
     * </ul>
     */
    public enum Relation
        {
        INDEP, EQUIV, INVERSE, MUTEX, MUTIN, IMPLIES, IMPLIED;

        /**
         * Swap the "this" and "that" relationship. This flips the table over the "\" diagonal.
         *
         * @return the relationship between "that" and "this"
         */
        public Relation reverse()
            {
            switch (this)
                {
                case IMPLIES:
                    return IMPLIED;
                case IMPLIED:
                    return IMPLIES;
                default:
                    return this;
                }
            }

        /**
         * Swap the true and false results. This flips the table over the "/" diagonal.
         *
         * @return the relationship that represents inverted results
         */
        public Relation inverse()
            {
            switch (this)
                {
                case MUTEX:
                    return MUTIN;
                case MUTIN:
                    return MUTEX;
                default:
                    return this;
                }
            }

        }


    // ----- Influence enum ------------------------------------------------------------------------

    /**
     * Represents a 3x3 truth table:
     * <p/>
     * <code><pre>
     *                          Input of True
     *                          Result is ...
     *
     *                           False      Mixed        True
     *                         +==========+==========+==========+
     *                  False  | ALWAYS_F │ AND      │ IDENTITY |
     * Input of False          |──────────┼──────────┼──────────|
     * Result is ...    Mixed  | INV_AND  │ CONTRIB  │ OR       |
     *                         |──────────┼──────────┼──────────|
     *                  True   | INVERSE  │ INV_OR   │ ALWAYS_T |
     *                         +==========+==========+==========+
     * </pre></code>
     * <p/>
     * The NONE influence is used to indicate that an input is not related to, and thus does not
     * influence, the result of a condition.
     */
    public enum Influence
        {
        NONE, ALWAYS_F, AND, IDENTITY, INV_AND, CONTRIB, OR, INVERSE, INV_OR, ALWAYS_T;

        /**
         * Determine the inverse (the "not") of the Influence.
         *
         * @return the inverse Influence of this Influence
         */
        public Influence inverse()
            {
            switch (this)
                {
                case AND:
                    return INV_OR;

                case INV_AND:
                    return OR;

                case OR:
                    return INV_AND;

                case INV_OR:
                    return AND;

                case IDENTITY:
                    return INVERSE;

                case INVERSE:
                    return IDENTITY;

                case ALWAYS_F:
                    return ALWAYS_T;

                case ALWAYS_T:
                    return ALWAYS_F;

                default:
                case NONE:
                case CONTRIB:
                    return this;
                }
            }

        /**
         * Collapse the true influences down to mixed influences, as will naturally occur when a
         * condition's influence is ANDed with another condition.
         *
         * @return the result of ANDing this influence with another
         */
        public Influence and()
            {
            switch (this)
                {
                case OR:
                case INV_OR:
                case ALWAYS_T:
                    return CONTRIB;

                case INVERSE:
                    return INV_AND;

                case IDENTITY:
                    return AND;

                default:
                    return this;
                }
            }

        /**
         * @return true if the influence implies that the condition is required in order for the
         *         result to be true
         */
        public boolean isRequired()
            {
            return this == AND || this == IDENTITY;
            }

        public static Influence translate(long cFalseInFalseOut, long cFalseInTrueOut,
                                          long cTrueInFalseOut,  long cTrueInTrueOut)
            {
            switch ((cFalseInFalseOut == 0 ? 0b0000 : 0b1000)
                  | (cFalseInTrueOut  == 0 ? 0b0000 : 0b0100)
                  | (cTrueInFalseOut  == 0 ? 0b0000 : 0b0010)
                  | (cTrueInTrueOut   == 0 ? 0b0000 : 0b0001))
                {
                case 0b0000:
                    // there were no test results? not sure here whether to assert, throw, or NONE
                    return NONE;

                case 0b0001:
                case 0b0100:
                case 0b0101:
                    return ALWAYS_T;

                case 0b0010:
                case 0b1000:
                case 0b1010:
                    return ALWAYS_F;

                case 0b0110:
                    return INVERSE;

                case 0b0111:
                    return INV_OR;

                case 0b1001:
                    return IDENTITY;

                case 0b1011:
                    return AND;

                case 0b1101:
                    return OR;

                case 0b1110:
                    return INV_AND;

                case 0b0011:
                case 0b1100:
                case 0b1111:
                default:
                    // some of each
                    return CONTRIB;
                }
            }
        }


    // ----- inner class: Bifurcation --------------------------------------------------------------

    /**
     * Result of a bifurcation analysis of a ConditionalConstant.
     */
    public static class Bifurcation
        {
        public Bifurcation(boolean fTruePossible, ConditionalConstant condTrue,
                           boolean fFalsePossible, ConditionalConstant condFalse)
            {
            this.fTruePossible  = fTruePossible;
            this.condTrue       = condTrue;
            this.fFalsePossible = fFalsePossible;
            this.condFalse      = condFalse;
            }

        public boolean isTruePossible()
            {
            return fTruePossible;
            }

        public ConditionalConstant getTrueCondition()
            {
            return condTrue;
            }

        public boolean isFalsePossible()
            {
            return fFalsePossible;
            }

        public ConditionalConstant getFalseCondition()
            {
            return condFalse;
            }

        private final boolean             fTruePossible;
        private final ConditionalConstant condTrue;
        private final boolean             fFalsePossible;
        private final ConditionalConstant condFalse;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Used by SimulatedLinkerContext.
     */
    public transient int iTest;
    }