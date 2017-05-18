package org.xvm.asm;


import java.util.HashSet;
import java.util.Set;
import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.VersionConstant;


/**
 * This is a compile-time LinkerContext emulator, that is driven off of the information managed by
 * the AssemblerContext -- and only that information! Specifically, it takes the conditions that
 * have (for a given code path) already been tested (specified), and thus are known to be true.
 * <p/>
 * For example, if a Module is marked as version 1 (using a VersionedCondition) and the package under
 * it is conditional on "debug" being defined, then the AssemblerContext at that point in the
 * hierarchy implies a LinkerContext that returns true for {@link #isSpecified(String)
 * isSpecified("debug")} and true for {@link #isVersionMatch(VersionConstant, boolean)
 * isVersion(1, true)}.
 *
 * @author cp 2017.05.17
 */
public class SimulatedLinkerContext
        implements LinkerContext
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a SimulatedLinkerContext from the current configuration of an AssemblerContext.
     * @param ctx
     */
    public SimulatedLinkerContext(AssemblerContext ctx)
        {
        // TODO
        }

    public SimulatedLinkerContext(ConditionalConstant cond)
        {
        this.cond = cond;
        }

    public SimulatedLinkerContext(ConditionalConstant... conds)
        {
        this(toCondition(conds));
        }

    /**
     * Turn an optional array of conditions into a condition.
     *
     * @param conds an array of conditions, or null
     *
     * @return null iff the array is null or zero-length, otherwise a condition representing the
     *         contents of the array
     */
    private static ConditionalConstant toCondition(ConditionalConstant[] conds)
        {
        if (conds == null || conds.length == 0)
            {
            return null;
            }
        else if (conds.length == 1)
            {
            return conds[0];
            }
        else
            {
            return new AllCondition(conds[0].getConstantPool(), conds);
            }
        }


    // ----- LinkerContext methods -----------------------------------------------------------------

    @Override
    public boolean isSpecified(String sName)
        {
        if (cond == null)
            {
            return false;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isVersionMatch(Constant constVMStruct)
        {
        if (cond == null)
            {
            return false;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isPresent(Constant constVMStruct, VersionConstant constVer,
            boolean fExactVer)
        {
        if (cond == null)
            {
            return false;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isVersionMatch(VersionConstant constVer, boolean fExactVer)
        {
        if (cond == null)
            {
            return false;
            }

        // TODO
        return false;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return super.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        return super.equals(obj);
        }

    @Override
    public String toString()
        {
        return "SimulatedLinkerContext{" + cond + "}"
        }


    // ----- inner class: Tester --

    class BruteForceTester
        {
        public BruteForceTester(ConditionalConstant cond)
            {
            // build a table
            cond.terminals()
            }

        }

    // ----- fields --------------------------------------------------------------------------

    private ConditionalConstant cond;

    private Set<String> defines;
    private
    }
