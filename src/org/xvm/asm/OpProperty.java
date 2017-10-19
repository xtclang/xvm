package org.xvm.asm;


import java.io.DataOutput;
import java.io.IOException;


import static org.xvm.util.Handy.writePackedLong;

/**
 * Common base for property related (P_) ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpProperty extends Op
    {
    /**
     * Construct an op.
     *
     * @param nPropId  the property to increment
     */
    protected OpProperty(int nPropId)
        {
        m_nPropId = nPropId;
        }

    /**
     * Construct an op.
     *
     * @param argProperty  the property Argument
     */
    protected OpProperty(Argument argProperty)
        {
        m_argProperty = argProperty;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(getOpCode());

        if (m_argProperty != null)
            {
            m_nPropId = encodeArgument(m_argProperty, registry);
            }

        writePackedLong(out, m_nPropId);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argProperty, registry);
        }

    protected int m_nPropId;

    private Argument m_argProperty;
    }
