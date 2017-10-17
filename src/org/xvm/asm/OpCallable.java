package org.xvm.asm;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.writePackedLong;


/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    /**
     * Construct an op.
     *
     * @param nFunctionValue  r-value that specifies the function being called
     */
    protected OpCallable(int nFunctionValue)
        {
        m_nFunctionValue = nFunctionValue;
        }

    /**
     * Construct an op based on the passed argument.
     *
     * @param argFunction  the function Argument
     */
    public OpCallable(Argument argFunction)
        {
        m_argFunction = argFunction;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argFunction != null)
            {
            m_nFunctionValue = encodeArgument(m_argFunction, registry);
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nFunctionValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argFunction, registry);
        }

    // get the structure for the function constant
    protected MethodStructure getMethodStructure(Frame frame)
        {
        // there is no need to cache the id, since it's a constant for a given op-code
        if (m_function != null)
            {
            return m_function;
            }

        assert m_nFunctionValue < 0;

        MethodConstant constFunction = (MethodConstant)
                frame.f_context.f_pool.getConstant(-m_nFunctionValue);

        return m_function = (MethodStructure) constFunction.getComponent();
        }


    protected int m_nFunctionValue;

    private Argument m_argFunction;

    // function caching
    private MethodStructure m_function;   // cached function
    }
