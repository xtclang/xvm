package org.xvm.asm.constants;


import java.util.Collections;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.asm.Version;
import org.xvm.util.ListMap;


/**
 * Represents a condition that can be evaluated at link-time. Conditional constants are used to
 * encode boolean expressions into VM structures themselves, allowing structures and portions of
 * structures to be conditionally present at runtime. This has the net effect of supporting the same
 * "conditional compilation" as a pre-processor would provide for a language such as C/C++, but
 * instead of compiling only one "path" through the specified conditionals, XTC compiles all of the
 * paths, verifying that every single path meets the requirements of the language and the
 * dependencies implied by (and deferred to) link-time. Among other purposes, this allows XTC code
 * to be tailored to the presence (or absence) of a specific library, such as only implementing a
 * library-specific interface and only consuming library functionality if that particular library is
 * present. Additionally, multiple versions of VM structures can be combined into a single VM
 * structure (for example, multiple versions of a module can be combined into a single module), by
 * using version conditions to delineate gthe differences among versions.
 * <p/>
 * Structural inclusion/exclusion occurs when a conditional constant is referenced by another VM
 * structure, indicating that the presence at runtime of the VM structure depends on the result of
 * the evaluation of the conditional constant. Similarly, logical inclusion/exclusion occurs when a
 * conditional constant is referenced by an XTC op-code, indicating that the presence at runtime of
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
 * <li>{@link VersionedCondition VersionedCondition} - evaluates to true iff the version of this module
 *     is of a specified version.</li>
 * </ul>
 * <p/>
 * Four additional conditional constants support the composition of other conditions:
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
     * Determine the set of terminal conditions that make up this ConditionalConstant.
     *
     * @return a set of terminals that are referenced by this ConditionalConstant
     */
    public abstract Set<ConditionalConstant> terminals();

    /**
     * Determine if the specified terminal ConditionalConstant is present in this
     * ConditionalConstant.
     *
     * @param that  a ConditionalConstant
     *
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
     *
     * @return
     */
    public Relation calcRelation(ConditionalConstant that)
        {
        assert !isTerminal();
        throw new UnsupportedOperationException("on terminal ConditionalConstants are supported");
        }

    /**
     * Determine the versions specified for the ConditionalConstant, if any.
     * <p/>
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
     * Obtain a ConditionalConstant that represents the union of {@code this} and {@code that}
     * condition.
     * 
     * @param that  another condition
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
            if (this instanceof AllCondition)
                {
                for (ConditionalConstant cond : ((AllCondition) this).m_aconstCond)
                    {
                    conds.putIfAbsent(cond, cond);
                    }
                }
            else
                {
                conds.put(this, this);
                }
            
            if (that instanceof AllCondition)
                {
                for (ConditionalConstant cond : ((AllCondition) that).m_aconstCond)
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
            
            return new AllCondition(getConstantPool(), conds.keySet().toArray(new ConditionalConstant[conds.size()]));
            }
        
        return new AllCondition(getConstantPool(), this, that);
        }

    /**
     * Obtain a ConditionalConstant that represents the option of {@code this} or {@code that}
     * condition.
     *
     * @param that  another condition
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
            if (this instanceof AnyCondition)
                {
                for (ConditionalConstant cond : ((AnyCondition) this).m_aconstCond)
                    {
                    conds.putIfAbsent(cond, cond);
                    }
                }
            else
                {
                conds.put(this, this);
                }

            if (that instanceof AnyCondition)
                {
                for (ConditionalConstant cond : ((AnyCondition) that).m_aconstCond)
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

            return new AnyCondition(getConstantPool(), conds.keySet().toArray(new ConditionalConstant[conds.size()]));
            }

        return new AnyCondition(getConstantPool(), this, that);
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
     *              ╔═══╤═══╗
     *            F ║ x │ x ║
     *       that   ╟───┼───╢
     *            T ║ x │ x ║
     *              ╚═══╧═══╝
     *      </pre></code>
     * <li><b>Equivalent:</b> This condition is equivalent to that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              ╔═══╤═══╗
     *            F ║ x │   ║
     *       that   ╟───┼───╢
     *            T ║   │ x ║
     *              ╚═══╧═══╝
     *      </pre></code>
     * <li><b>Inverse:</b> This condition is the inverse of that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              ╔═══╤═══╗
     *            F ║   │ x ║
     *       that   ╟───┼───╢
     *            T ║ x │   ║
     *              ╚═══╧═══╝
     *      </pre></code>
     * <li><b>Mutual-Exclusion:</b> This condition is mutually exclusive with that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              ╔═══╤═══╗
     *            F ║ x │ x ║
     *       that   ╟───┼───╢
     *            T ║ x │   ║
     *              ╚═══╧═══╝
     *      </pre></code>
     * <li><b>Mutual-Inclusion:</b> This condition is mutually inclusive with that condition;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              ╔═══╤═══╗
     *            F ║   │ x ║
     *       that   ╟───┼───╢
     *            T ║ x │ x ║
     *              ╚═══╧═══╝
     *      </pre></code>
     * <li><b>Implies:</b> If this is true, it implies that is true;</li>
     *     <code><pre>
     *                this
     *                F   T
     *              ╔═══╤═══╗
     *            F ║ x │   ║
     *       that   ╟───┼───╢
     *            T ║ x │ x ║
     *              ╚═══╧═══╝
     *      </pre></code>
     * <li><b>Implied:</b> If that is true, this is implied to be true.</li>
     *     <code><pre>
     *                this
     *                F   T
     *              ╔═══╤═══╗
     *            F ║ x │ x ║
     *       that   ╟───┼───╢
     *            T ║   │ x ║
     *              ╚═══╧═══╝
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
    }
