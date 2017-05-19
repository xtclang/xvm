package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.Version;


/**
 * Implements the logical "and" of any number of conditions.
 */
public class AllCondition
        extends MultiCondition
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public AllCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct an AllCondition.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param aconstCond  an array of underlying conditions to evaluate
     */
    public AllCondition(ConstantPool pool, ConditionalConstant... aconstCond)
        {
        super(pool, mergeAnds(aconstCond));
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        for (ConditionalConstant constCond : m_aconstCond)
            {
            if (!constCond.evaluate(ctx))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean testEvaluate(long n)
        {
        for (ConditionalConstant constCond : m_aconstCond)
            {
            if (!constCond.testEvaluate(n))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public Set<Version> versions()
        {
        Set<Version> setVers = null;

        for (ConditionalConstant cond : m_aconstCond)
            {
            Set<Version> setNew = cond.versions();
            if (!setNew.isEmpty())
                {
                if (setVers == null)
                    {
                    setVers = setNew;
                    }
                else
                    {
                    throw new IllegalStateException("can't have two version conditions in an AllCondition: "
                            + setVers + ", " + setNew);
                    }
                }
            }

        return setVers == null ? Collections.EMPTY_SET : setVers;
        }

    @Override
    public boolean isTerminalInfluenceBruteForce()
        {
        return !isTerminalInfluenceFinessable(false, new HashSet<>(), new HashSet<>());
        }

    @Override
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex)
        {
        // unfortunately the "else" of an ANDed conditional can't be finessed, because the result
        // is the equivalent of an ORed list of NOTs
        if (fInNot)
            {
            return false;
            }

        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
            {
            if (!iter.next().isTerminalInfluenceFinessable(fInNot, setSimple, setComplex))
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public Map<ConditionalConstant, Influence> terminalInfluences()
        {
        if (isTerminalInfluenceBruteForce())
            {
            return super.terminalInfluences();
            }

        Map<ConditionalConstant, Influence> influences  = new HashMap<>();
        Set<VersionedCondition>             setVerConds = new HashSet<>();
        Set<Version>                        setVers     = null;
        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
            {
            ConditionalConstant cond = iter.next();
            if (cond instanceof VersionedCondition || cond instanceof AnyCondition)
                {
                // keep track of what versions survive the conditional(s)
                if (setVers == null)
                    {
                    setVers = new HashSet<>(cond.versions());
                    }
                else
                    {
                    setVers.retainAll(cond.versions());
                    }

                // collect the terminal VersionedConditions
                if (cond instanceof VersionedCondition)
                    {
                    setVerConds.add((VersionedCondition) cond);
                    }
                else
                    {
                    for (Iterator<ConditionalConstant> iterVerCond = ((AnyCondition) cond).flatIterator();
                            iterVerCond.hasNext(); )
                        {
                        setVerConds.add((VersionedCondition) iterVerCond.next());
                        }
                    }
                }
            else
                {
                for (Map.Entry<ConditionalConstant, Influence> entry : cond.terminalInfluences().entrySet())
                    {
                    ConditionalConstant cond
                    if (influences.containsKey())
                    // TODO all other conditions - ask for their influences
                    influences.put(terminal, Influence.OR);
                    }
                }
            }

        if (setVers != null)
            {
            // there were version conditions
            if (setVers.isEmpty())
                {
                // the version conditions are impossible to meet; this condition is unsatisfiable
                for (Map.Entry<ConditionalConstant, Influence> entry : influences.entrySet())
                    {
                    entry.setValue(Influence.ALWAYS_F);
                    }
                }
            else
                {
                // factor in the version condition into the existing set of influences
                // TODO
                }
            }

        return influences;
        }

    @Override
    protected String getOperatorString()
        {
        return "&&";
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionAll;
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Merge all of the nested "and" conditions into one bigger array of conditions.
     *
     * @param aconstCond  an array of conditional constants, some of which may be AllConditions
     *
     * @return a potentially larger array of conditional constants, logically equivalent to those
     *         passed in, and of which none should be an AllCondition
     */
    protected static ConditionalConstant[] mergeAnds(ConditionalConstant[] aconstCond)
        {
        assert aconstCond != null;
        assert aconstCond.length > 1;

        // scan the underlying conditions to see if there is anything to merge
        boolean fAnds   = false;
        int     cConds = 0;
        for (ConditionalConstant cond : aconstCond)
            {
            if (cond instanceof AllCondition)
                {
                fAnds   = true;
                cConds += mergeAnds(((AllCondition) cond).m_aconstCond).length;
                }
            else
                {
                ++cConds;
                }
            }

        if (!fAnds)
            {
            // nothing to merge
            return aconstCond;
            }

        // merge the "ands"
        ConditionalConstant[] aconstMerged = new ConditionalConstant[cConds];
        int ofNew = 0;
        for (ConditionalConstant cond : aconstCond)
            {
            if (cond instanceof AllCondition)
                {
                ConditionalConstant[] aconstCopy = mergeAnds(((AllCondition) cond).m_aconstCond);
                int cCopy = aconstCopy.length;
                System.arraycopy(aconstCopy, 0, aconstMerged, ofNew, cCopy);
                ofNew += cCopy;
                }
            else
                {
                aconstMerged[ofNew++] = cond;
                }
            }
        assert ofNew == cConds;

        return aconstMerged;
        }
    }
