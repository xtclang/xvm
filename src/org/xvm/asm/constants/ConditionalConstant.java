package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
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
 * <li>{@link VersionCondition VersionCondition} - evaluates to true iff the version of this module
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
 * <li>{@link Only1Condition Only1Condition} - evaluates to true iff exactly one of the specified
 *     conditions evaluates to true, i.e. an"xor" condition;</li>
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
    }
