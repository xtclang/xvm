package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Common base for NVOK_ ops.
 */
public abstract class OpInvocable extends Op
    {
    /**
     * Construct an op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     */
    protected OpInvocable(Argument argTarget, MethodConstant constMethod)
        {
        m_argTarget   = argTarget;
        m_constMethod = constMethod;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpInvocable(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget   = readPackedInt(in);
        m_nMethodId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nMethodId = encodeArgument(m_constMethod, registry);
            }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nMethodId);
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
    public void resetSimulation()
        {
        if (isMultiReturn())
            {
            resetRegisters(m_aArgReturn);
            }
        else
            {
            resetRegister(m_argReturn);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isMultiReturn())
            {
            checkNextRegisters(scope, m_aArgReturn, m_anRetValue);
            }
        else
            {
            checkNextRegister(scope, m_argReturn, m_nRetValue);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_constMethod = (MethodConstant) registerArgument(m_constMethod, registry);

        if (isMultiReturn())
            {
            registerArguments(m_aArgReturn, registry);
            }
        else
            {
            m_argReturn = registerArgument(m_argReturn, registry);
            }
        }

    // helper methods
     protected CallChain getCallChain(Frame frame, ObjectHandle hTarget)
        {
        ServiceContext  context   = frame.f_context;
        CallChain       chain     = (CallChain) context.getOpInfo(this, Category.Chain);
        TypeComposition clazzPrev = (TypeComposition) context.getOpInfo(this, Category.Composition);
        TypeComposition clazz     = hTarget.getComposition();

        if (chain != null && clazz == clazzPrev)
            {
            return chain;
            }

        context.setOpInfo(this, Category.Composition, clazz);

        MethodConstant  idMethod = (MethodConstant) frame.getConstant(m_nMethodId);
        MethodStructure method   = (MethodStructure) idMethod.getComponent();

        m_constMethod = idMethod; // used by "toString()" only

        if (method != null && method.getAccess() == Access.PRIVATE)
            {
            chain = new CallChain(method);

            context.setOpInfo(this, Category.Chain, chain);
            return chain;
            }

        Object nid = idMethod.resolveNestedIdentity(frame.poolContext(), frame.getGenericsResolver());

        chain = clazz.getMethodCallChain(nid);
        if (chain.getDepth() == 0)
            {
            return new CallChain.ExceptionChain(idMethod, hTarget.getType());
            }

        context.setOpInfo(this, Category.Chain, chain);
        return chain;
        }

    /**
     * Ensure that register for the return value is allocated.
     *
     * TODO: the register type should be injected by the compiler/verifier
     */
    protected void checkReturnRegister(Frame frame, ObjectHandle hTarget)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceMethodReturnVar(m_nRetValue, m_nMethodId, 0);
            }
        }

    /**
     * Ensure that register for the return Tuple value is allocated.
     *
     * TODO: the register type should be injected by the compiler/verifier
     */
    protected void checkReturnTupleRegister(Frame frame, ObjectHandle hTarget)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceMethodReturnVar(m_nRetValue, m_nMethodId, -1);
            }
        }

    /**
     * Ensure that registers for the return values are allocated.
     *
     * TODO: the register types should be injected by the compiler/verifier
     */
    protected void checkReturnRegisters(Frame frame, ObjectHandle hTarget)
        {
        assert isMultiReturn();

        int[] anRet = m_anRetValue;
        for (int i = 0, c = anRet.length; i < c; i++)
            {
            if (frame.isNextRegister(anRet[i]))
                {
                frame.introduceMethodReturnVar(anRet[i], m_nMethodId, i);
                }
            }
        }

    @Override
    public String toString()
        {
        return super.toString() + ' ' + getTargetString() + '.' + getMethodString() +
                '(' + getParamsString() + ") -> " + getReturnsString();
        }
    protected String getTargetString()
        {
        return Argument.toIdString(m_argTarget, m_nTarget);
        }
    protected String getMethodString()
        {
        return m_constMethod == null ? "???" : m_constMethod.getName();
        }
    protected String getParamsString()
        {
        return "";
        }
    protected static String getParamsString(int[] anArgValue, Argument[] aArgValue)
        {
        StringBuilder sb = new StringBuilder();
        int cArgNums = anArgValue == null ? 0 : anArgValue.length;
        int cArgRefs = aArgValue == null ? 0 : aArgValue.length;
        for (int i = 0, c = Math.max(cArgNums, cArgRefs); i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(Argument.toIdString(i < cArgRefs ? aArgValue[i] : null,
                                          i < cArgNums ? anArgValue[i] : Register.UNKNOWN));
            }
        return sb.toString();
        }
    protected String getReturnsString()
        {
        if (m_anRetValue != null || m_aArgReturn != null)
            {
            // multi-return
            StringBuilder sb = new StringBuilder();
            int cArgNums = m_anRetValue == null ? 0 : m_anRetValue.length;
            int cArgRefs = m_aArgReturn == null ? 0 : m_aArgReturn.length;
            for (int i = 0, c = Math.max(cArgNums, cArgRefs); i < c; ++i)
                {
                sb.append(i == 0 ? "(" : ", ")
                  .append(Argument.toIdString(i < cArgRefs ? m_aArgReturn[i] : null,
                                              i < cArgNums ? m_anRetValue[i] : Register.UNKNOWN));
                }
            return sb.append(')').toString();
            }

        if (m_nRetValue != A_IGNORE || m_argReturn != null)
            {
            return Argument.toIdString(m_argReturn, m_nRetValue);
            }

        return "void";
        }

    protected int   m_nTarget;
    protected int   m_nMethodId;
    protected int   m_nRetValue = A_IGNORE;
    protected int[] m_anRetValue;

    protected Argument       m_argTarget;
    protected MethodConstant m_constMethod;
    protected Argument       m_argReturn;  // optional
    protected Argument[]     m_aArgReturn; // optional

    // categories for cached info
    enum Category {Chain, Composition};
    }
