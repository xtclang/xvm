package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.MethodConstant;
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
        m_argFunction = registerArgument(m_argFunction, registry);

        if (isMultiReturn())
            {
            registerArguments(m_aArgReturn, registry);
            }
        else
            {
            m_argReturn = registerArgument(m_argReturn, registry);
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

    // check if a register for the return value needs to be allocated
    protected void checkReturnRegister(Frame frame, MethodStructure method)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            TypeConstant typeRet = m_typeRetReg;
            if (typeRet == null)
                {
                typeRet = m_typeRetReg = method.getReturnTypes()[0].
                    resolveGenerics(frame.poolContext(), frame.getGenericsResolver());
                }

            frame.introduceResolvedVar(m_nRetValue, typeRet);
            }
        }

    // check if a register for the return Tuple value needs to be allocated
    protected void checkReturnTupleRegister(Frame frame, MethodStructure method)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            TypeConstant typeRet = m_typeRetReg;
            if (typeRet == null)
                {
                ConstantPool pool = frame.poolContext();

                typeRet = m_typeRetReg = pool.ensureParameterizedTypeConstant(
                    pool.typeTuple(), method.getReturnTypes()).
                        resolveGenerics(pool, frame.getGenericsResolver());
                }

            frame.introduceResolvedVar(m_nRetValue, typeRet);
            }
        }

    // check if any registers for the return values need to be allocated
    protected void checkReturnRegisters(Frame frame, MethodStructure method)
        {
        assert isMultiReturn();

        ConstantPool pool = frame.poolContext();

        int[] anRet = m_anRetValue;
        for (int i = 0, c = anRet.length; i < c; i++)
            {
            if (frame.isNextRegister(anRet[i]))
                {
                TypeConstant[] atypeRet = m_atypeRetReg;
                if (atypeRet == null)
                    {
                    atypeRet = m_atypeRetReg = method.getReturnTypes(); // a clone
                    for (int j = 0, cRet = atypeRet.length; j < cRet; j++)
                        {
                        atypeRet[j] = atypeRet[j].resolveGenerics(pool, frame.getGenericsResolver());
                        }
                    }

                frame.introduceResolvedVar(anRet[i], atypeRet[i]);
                }
            }
        }

    protected int   m_nFunctionId;
    protected int   m_nRetValue = A_IGNORE;
    protected int[] m_anRetValue;

    protected Argument   m_argFunction;
    protected Argument   m_argReturn;  // optional
    protected Argument[] m_aArgReturn; // optional

    private MethodStructure m_function;   // cached function

    private TypeConstant m_typeRetReg;     // cached return register type
    private TypeConstant[]  m_atypeRetReg; // cached return registers types
    }
