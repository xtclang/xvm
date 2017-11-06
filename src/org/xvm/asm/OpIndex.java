package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for I_ (index based) and IIP_ (index based in-place) op codes.
 */
public abstract class OpIndex
        extends Op
    {
    /**
     * Construct an "index based" op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     */
    protected OpIndex(Argument argTarget, Argument argIndex)
        {
        assert(!isAssignOp());

        m_argTarget = argTarget;
        m_argIndex = argIndex;
        }

    /**
     * Construct an "in-place and assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpIndex(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argIndex = argIndex;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpIndex(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget = readPackedInt(in);
        m_nIndex = readPackedInt(in);
        if (isAssignOp())
            {
            m_nRetValue = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nIndex = encodeArgument(m_argIndex, registry);
            if (isAssignOp())
                {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
                }
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nIndex);
        if (isAssignOp())
            {
            writePackedLong(out, m_nRetValue);
            }
        }

    /**
     * A "virtual constant" indicating whether or not this op is an assigning one.
     *
     * @return true iff the op is an assigning one
     */
    protected boolean isAssignOp()
        {
        // majority of the ops are assigning; let's default to that
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hIndex  = frame.getArgument(m_nIndex);
            if (hTarget == null || hIndex == null)
                {
                return R_REPEAT;
                }

            if (isAssignOp() && frame.isNextRegister(m_nRetValue))
                {
                introduceAssignVar(frame);
                }

            if (isProperty(hTarget) || isProperty(hIndex))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hTarget, hIndex};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], (JavaLong) ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, hTarget, (JavaLong) hIndex);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    /**
     * Introduce a register for the resulting value.
     *
     * This method should be overridden by I_Ref to introduce a Ref of an element instead.
     */
    protected void introduceAssignVar(Frame frame)
        {
        frame.introduceElementVar(m_nTarget);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isAssignOp())
            {
            checkNextRegister(scope, m_argReturn);
            }

        // TODO: remove when deprecated construction is removed
        if (isAssignOp() && scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argTarget, registry);
        registerArgument(m_argIndex, registry);
        if (isAssignOp())
            {
            registerArgument(m_argReturn, registry);
            }
        }

    protected int m_nTarget;
    protected int m_nIndex;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argIndex;
    private Argument m_argReturn;
    }
