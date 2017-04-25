package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;


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
    public AnyCondition(ConstantPool pool, ConditionalConstant[] aconstCond)
        {
        super(pool, aconstCond);
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    protected String getOperatorString()
        {
        return "||";
        }

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


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionAny;
        }
    }
