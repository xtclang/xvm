package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
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
        m_argIndex  = argIndex;
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
        m_argIndex  = argIndex;
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
        m_nIndex  = readPackedInt(in);
        if (isAssignOp())
            {
            m_nRetValue = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nIndex  = encodeArgument(m_argIndex, registry);
            if (isAssignOp())
                {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
                }
            }

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
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nIndex}, 2);

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, ahArg[0], ahArg[1]);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    /**
     * Complete the op processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hIndex)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * Retrieve cached call chain.
     */
    protected CallChain getOpChain(Frame frame, TypeConstant typeTarget)
        {
        ServiceContext ctx   = frame.f_context;
        CallChain      chain = (CallChain) ctx.getOpInfo(this, Category.Chain);
        if (chain != null)
            {
            TypeConstant typePrevTarget = (TypeConstant) ctx.getOpInfo(this, Category.Type);
            if (typeTarget.equals(typePrevTarget))
                {
                return chain;
                }
            }
        return null;
        }

    /**
     * Cache the specified call chain for the given target.
     */
    protected void saveOpChain(Frame frame, TypeConstant typeTarget, CallChain chain)
        {
        ServiceContext ctx = frame.f_context;
        ctx.setOpInfo(this, Category.Chain, chain);
        ctx.setOpInfo(this, Category.Type, typeTarget);
        }

    @Override
    public void resetSimulation()
        {
        if (isAssignOp())
            {
            resetRegister(m_argReturn);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isAssignOp())
            {
            checkNextRegister(scope, m_argReturn, m_nRetValue);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_argIndex = registerArgument(m_argIndex, registry);
        if (isAssignOp())
            {
            m_argReturn = registerArgument(m_argReturn, registry);
            }
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' '  + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argIndex,  m_nIndex)
                + ", " + Argument.toIdString(m_argReturn, m_nRetValue);
        }

    protected int m_nTarget;
    protected int m_nIndex;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argIndex;
    private Argument m_argReturn;

    // categories for cached info
    enum Category {Chain, Type};
    }
