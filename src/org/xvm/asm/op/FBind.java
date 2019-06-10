package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;

import org.xvm.asm.OpCallable;
import org.xvm.asm.Register;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xFunction.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * FBIND rvalue-fn, #params:(param-index, rvalue-param), lvalue-fn-result
 */
public class FBind
        extends OpCallable
    {
    /**
     * Construct an FBIND op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param anParamIx    the indexes of parameter(s) to bind (sorted in ascending order)
     * @param aArgValue    the array of Arguments to bind the values to
     * @param argReturn    the return Argument
     */
    public FBind(Argument argFunction, int[] anParamIx, Argument[] aArgValue, Argument argReturn)
        {
        super(argFunction);

        m_anParamIx = anParamIx;
        m_aArgParam = aArgValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FBind(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        int c = readPackedInt(in);

        m_anParamIx = new int[c];
        m_anParamValue = new int[c];

        for (int i = 0; i < c; i++)
            {
            m_anParamIx[i]    = readPackedInt(in);
            m_anParamValue[i] = readPackedInt(in);
            }
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgParam != null)
            {
            m_anParamValue = encodeArguments(m_aArgParam, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        int c = m_anParamIx.length;
        writePackedLong(out, c);
        for (int i = 0; i < c; i++)
            {
            writePackedLong(out, m_anParamIx[i]);
            writePackedLong(out, m_anParamValue[i]);
            }
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_FBIND;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            FunctionHandle hFunction;

            if (m_nFunctionId == A_SUPER)
                {
                CallChain chain = frame.m_chain;
                if (chain == null)
                    {
                    throw new IllegalStateException();
                    }
                hFunction = xFunction.makeHandle(chain, frame.m_nDepth + 1);
                }
            else if (m_nFunctionId < CONSTANT_OFFSET)
                {
                hFunction = xFunction.makeHandle(getMethodStructure(frame));
                }
            else
                {
                hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
                if (hFunction == null)
                    {
                    return R_REPEAT;
                    }
                }

            int cParams = m_anParamIx.length;
            ObjectHandle[] ahParam = new ObjectHandle[cParams];
            boolean fAnyProperty = false;

            for (int i = 0; i < cParams; i++)
                {
                ObjectHandle hParam = frame.getArgument(m_anParamValue[i]);
                if (hParam == null)
                    {
                    return R_REPEAT;
                    }
                ahParam[i] = hParam;
                fAnyProperty |= isDeferred(hParam);
                }

            if (fAnyProperty)
                {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, hFunction, ahParam);

                return new Utils.GetArguments(ahParam, stepNext).doNext(frame);
                }

            return complete(frame, hFunction, ahParam);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, FunctionHandle hFunction, ObjectHandle[] ahParam)
        {
        // TODO: introduce bindMulti() method to reduce array copying
        // we assume that the indexes are sorted in the ascending order
        for (int i = 0, c = m_anParamIx.length; i < c; i++)
            {
            // after every step, the resulting function accepts one less
            // parameter, so it needs to compensate the absolute position
            hFunction = hFunction.bind(m_anParamIx[i] - i, ahParam[i]);
            }

        return frame.assignValue(m_nRetValue, hFunction);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgParam, registry);
        }

    @Override
    protected String getParamsString()
        {
        StringBuilder sb = new StringBuilder();
        int cArgNums = m_anParamValue == null ? 0 : m_anParamValue.length;
        int cArgRefs = m_aArgParam    == null ? 0 : m_aArgParam   .length;
        for (int i = 0, c = m_anParamIx.length; i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append('[')
              .append(m_anParamIx[i])
              .append("]=")
              .append(Argument.toIdString(i < cArgRefs ? m_aArgParam[i] : null,
                      i < cArgNums ? m_anParamValue[i] : Register.UNKNOWN));
            }
        return sb.toString();
        }

    private int[] m_anParamIx;
    private int[] m_anParamValue;

    private Argument[] m_aArgParam;
    }
