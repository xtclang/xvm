package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Common base for CALL_ ops.
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
    public boolean usesSuper()
        {
        return m_nFunctionId == A_SUPER;
        }

    /**
     * A "virtual constant" indicating whether or not this op has multiple return values.
     *
     * @return true iff the op has multiple return values.
     */
    protected boolean isMultiReturn()
        {
        return false;
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isMultiReturn())
            {
            checkNextRegisters(scope, m_aArgReturn);

            // TODO: remove when deprecated construction is removed
            for (int i = 0, c = m_anRetValue.length; i < c; i++)
                {
                if (scope.isNextRegister(m_anRetValue[i]))
                    {
                    scope.allocVar();
                    }
                }
            }
        else
            {
            checkNextRegister(scope, m_argReturn);

            // TODO: remove when deprecated construction is removed
            if (scope.isNextRegister(m_nRetValue))
                {
                scope.allocVar();
                }
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argFunction, registry);

        if (isMultiReturn())
            {
            registerArguments(m_aArgReturn, registry);
            }
        else
            {
            registerArgument(m_argReturn, registry);
            }
        }

    // ----- helper methods -----

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

    protected void checkReturnRegister(Frame frame, PropertyConstant constProperty)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceVar(constProperty.getRefType());
            }
        }

    protected void checkReturnRegister(Frame frame, MethodConstant constMethod)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceVar(constMethod.getSignature().getRawReturns()[0]);
            }
        }

    protected void checkReturnTupleRegister(Frame frame, MethodConstant constMethod)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceReturnTuple(A_FRAME, constMethod);
            }
        }

    protected void checkReturnRegisters(Frame frame, MethodConstant constMethod)
        {
        assert isMultiReturn();

        int[] anRet = m_anRetValue;
        for (int i = 0, c = anRet.length; i < c; i++)
            {
            TypeConstant[] aRetType = constMethod.getSignature().getRawReturns();

            if (frame.isNextRegister(anRet[i]))
                {
                frame.introduceVar(aRetType[i]);
                }
            }
        }

    protected int   m_nFunctionId;
    protected int   m_nRetValue = Frame.RET_UNUSED;
    protected int[] m_anRetValue;

    protected Argument   m_argFunction;
    protected Argument   m_argReturn;  // optional
    protected Argument[] m_aArgReturn; // optional

    // function caching
    private MethodStructure m_function;   // cached function
    }
