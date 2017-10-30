package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    /**
     * Construct an op based on the passed argument.
     *
     * @param argFunction  the function Argument
     */
    protected OpCallable(Argument argFunction)
        {
        m_argFunction = argFunction;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpCallable(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nFunctionId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argFunction != null)
            {
            m_nFunctionId = encodeArgument(m_argFunction, registry);
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nFunctionId);
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

        MethodConstant constFunction = (MethodConstant) frame.getConstant(m_nFunctionId);

        return m_function = (MethodStructure) constFunction.getComponent();
        }


    protected int m_nFunctionId;

    private Argument m_argFunction;

    // function caching
    private MethodStructure m_function;   // cached function
    }
