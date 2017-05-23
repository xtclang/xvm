package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.Version;


/**
 * Implements the logical "or" of any number of conditions.
 */
public class AnyCondition
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
    public AnyCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct an AnyCondition.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param aconstCond  an array of underlying conditions to evaluate
     */
    public AnyCondition(ConstantPool pool, ConditionalConstant... aconstCond)
        {
        super(pool, mergeOrs(aconstCond));
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        for (ConditionalConstant constCond : m_aconstCond)
            {
            if (constCond.evaluate(ctx))
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public boolean testEvaluate(long n)
        {
        for (ConditionalConstant constCond : m_aconstCond)
            {
            if (constCond.testEvaluate(n))
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public Set<Version> versions()
        {
        Set<Version> setVers = null;

        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
            {
            ConditionalConstant cond = iter.next();
            if (cond instanceof VersionedCondition)
                {
                if (setVers == null)
                    {
                    setVers = new TreeSet<>();
                    }
                setVers.add(((VersionedCondition) cond).getVersionConstant().getVersion());
                }
            }

        return setVers == null ? Collections.EMPTY_SET : setVers;
        }

    /**
     * @return true iff the AnyCondition contains only VersionedConditions
     */
    public boolean isOnlyVersions()
        {
        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
            {
            if (!(iter.next() instanceof VersionedCondition))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean isTerminalInfluenceBruteForce()
        {
        // unless the entire condition is just checking versions, then the whole thing needs to be
        // brute forced
        return !isOnlyVersions();
        }

    @Override
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex)
        {
        return !fInNot && isOnlyVersions();
        }

    @Override
    public Map<ConditionalConstant, Influence> terminalInfluences()
        {
        if (isOnlyVersions())
            {
            // these are all VersionedCondition
            Map<ConditionalConstant, Influence> influences = new HashMap<>();
            for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
                {
                influences.put(iter.next(), Influence.OR);
                }
            return influences;
            }

        return super.terminalInfluences();
        }

    @Override
    protected String getOperatorString()
        {
        return "||";
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionAny;
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Merge all of the nested "or" conditions into one bigger array of conditions.
     *
     * @param aconstCond  an array of conditional constants, some of which may be AnyConditions
     *
     * @return a potentially larger array of conditional constants, logically equivalent to those
     *         passed in, and of which none should be an AnyCondition
     */
    protected static ConditionalConstant[] mergeOrs(ConditionalConstant[] aconstCond)
        {
        assert aconstCond != null;
        assert aconstCond.length > 1;

        // scan the underlying conditions to see if there is anything to merge
        boolean fOrs   = false;
        int     cConds = 0;
        for (ConditionalConstant cond : aconstCond)
            {
            if (cond instanceof AnyCondition)
                {
                fOrs    = true;
                cConds += mergeOrs(((AnyCondition) cond).m_aconstCond).length;
                }
            else
                {
                ++cConds;
                }
            }

        if (!fOrs)
            {
            // nothing to merge
            return aconstCond;
            }

        // merge the "ors"
        ConditionalConstant[] aconstMerged = new ConditionalConstant[cConds];
        int ofNew = 0;
        for (ConditionalConstant cond : aconstCond)
            {
            if (cond instanceof AnyCondition)
                {
                ConditionalConstant[] aconstCopy = mergeOrs(((AnyCondition) cond).m_aconstCond);
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
