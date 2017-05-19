package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ConditionalConstant.Relation;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.util.ListMap;
import org.xvm.util.LongList;


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
     * Construct a SimulatedLinkerContext using the specified condition.
     * 
     * @param cond  a conditional constant, or null (meaning unconditional)
     */
    public SimulatedLinkerContext(ConditionalConstant cond)
        {
        this.cond = cond;
        }

    /**
     * Construct a SimulatedLinkerContext using the specified conditions.
     * 
     * @param conds  any number of conditions, which will be treated as if they all need to be met
     */
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
    public boolean isPresent(IdentityConstant constVMStruct)
        {
        if (cond == null)
            {
            return false;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isVersionMatch(ModuleConstant constModule, VersionConstant constVer)
        {
        if (cond == null)
            {
            return false;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isVersion(VersionConstant constVer)
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


    // ----- inner class: BruteForceTester ---------------------------------------------------------

    public static class BruteForceTester
        {
        public BruteForceTester(ConditionalConstant cond)
            {
            assert cond != null;
            this.cond = cond;
            
            Set<ConditionalConstant> terminals  = cond.terminals();
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
                    for (Map.Entry<Integer, Relation> entry : conflicts)
                        {
                        int iCondThat = entry.getKey();
                        skipMasks.add((1L << iCondThat) | (1L << iCondThis));
                        switch (entry.getValue())
                            {
                            case MUTEX:
                                // can't both be true
                                skipPtrns.add((1L << iCondThat) | (1L << iCondThis))
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

            assert cConds > 0;
            boolean fSkips    = !skipMasks.isEmpty();
            int     cSkips    = skipMasks.size();
            long[]  amaskSkip = skipMasks.toArray();
            long[]  aptrnSkip = skipPtrns.toArray();
            long[]  aResultFF = new long[cConds];
            long[]  aResultFT = new long[cConds];
            long[]  aResultTF = new long[cConds];
            long[]  aResultTT = new long[cConds];
            int     cTrue     = 0;
            int     cFalse    = 0;
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

                if (cond.testEvaluate(nTest))
                    {
                    // result is true
                    ++cTrue;
                    for (int iCond = 0; iCond < cConds; ++iCond)
                        {
                        if ((nTest | (1L << iCond)) != 0)
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
                    ++cFalse;
                    for (int iCond = 0; iCond < cConds; ++iCond)
                        {
                        if ((nTest | (1L << iCond)) != 0)
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

            for (ConditionalConstant terminal : terminals)
                {
                int     iCond   = terminal.iTest;
                boolean fInvert = iCond < 0;
                if (fInvert)
                    {
                    iCond = -1 - iCond;
                    }

                }
            }

        
        
        private ConditionalConstant cond;
        }

    // ----- fields --------------------------------------------------------------------------------

    private ConditionalConstant cond;
    }
