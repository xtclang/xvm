package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;


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
