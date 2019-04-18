package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
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

        // for debugging support
        m_constMethod = (MethodConstant) aconst[m_nMethodId];
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
            checkNextRegisters(scope, m_aArgReturn);
            }
        else
            {
            checkNextRegister(scope, m_argReturn);
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
        if (m_chain != null && m_clazz == hTarget.getComposition())
            {
            return m_chain;
            }

        TypeComposition clazz    = m_clazz = hTarget.getComposition();
        MethodConstant  idMethod = (MethodConstant) frame.getConstant(m_nMethodId);
        if (idMethod.isLambda())
            {
            return m_chain = new CallChain(idMethod);
            }

        SignatureConstant sig = idMethod.getSignature().resolveGenericTypes(
            frame.poolContext(), frame.getGenericsResolver());
        CallChain chain = clazz.getMethodCallChain(sig);
        if (chain.getDepth() == 0)
            {
            // TODO: create an exception throwing chain
            throw new IllegalStateException("No call chain for method \"" + sig.getValueString() +
                "\" on " + hTarget.getType().getValueString());
            }
        return m_chain = chain;
        }

    // check if a register for the return value needs to be allocated
    protected void checkReturnRegister(Frame frame, ObjectHandle hTarget)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            // TODO: there are a number of things that are not quite right with this approach:
            // 1) The type of the target may not be the same as the target we used to cache the
            //    "auto" register type and may need to be re-calculated
            // 2) The calculation of the type uses the compile-time method return type;
            //    while it's more correct than the run-time return type (that we used to use).
            //    it may still be narrower than the compiler assumed. Unfortunately, the correct
            //    compile-time type knowledge that existed in the corresponding Register, has been
            //    dropped when the {@link #simulate()} and later {@link #write} are called.
            //    We need to consider retaining/persisting that information on the Op itself.
            //
            // That same issue is clearly applicable to other "checkReturn" methods here.
            // All that said, at the moment of this writing (12/27/18), none of the generated code
            // is using the "auto" return registers, hence the fix for this issue is deferred.
            TypeConstant typeRet = m_typeRetReg;
            if (typeRet == null)
                {
                ConstantPool      pool        = frame.poolContext();
                MethodConstant    constMethod = (MethodConstant) frame.getConstant(m_nMethodId);
                SignatureConstant sigTarget   = getTargetSignature(pool, hTarget, constMethod);

                typeRet = m_typeRetReg = constMethod.getRawReturns()[0].
                        resolveGenerics(pool, sigTarget.getRawReturns()[0]);
                }

            frame.introduceResolvedVar(m_nRetValue, typeRet);
            }
        }

    // check if a register for the return Tuple value needs to be allocated
    protected void checkReturnTupleRegister(Frame frame, ObjectHandle hTarget)
        {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue))
            {
            TypeConstant typeRet = m_typeRetReg;
            if (typeRet == null)
                {
                ConstantPool      pool        = frame.poolContext();
                MethodConstant    constMethod = (MethodConstant) frame.getConstant(m_nMethodId);
                SignatureConstant sigTarget   = getTargetSignature(pool, hTarget, constMethod);

                typeRet = m_typeRetReg = pool.ensureParameterizedTypeConstant(
                    pool.typeTuple(), constMethod.getSignature().getRawReturns()).
                        resolveGenerics(pool, sigTarget.getRawReturns()[0]);
                }

            frame.introduceResolvedVar(m_nRetValue, typeRet);
            }
        }

    // check if any registers for the return values need to be allocated
    protected void checkReturnRegisters(Frame frame, ObjectHandle hTarget)
        {
        assert isMultiReturn();

        int[] anRet = m_anRetValue;
        for (int i = 0, c = anRet.length; i < c; i++)
            {
            if (frame.isNextRegister(anRet[i]))
                {
                TypeConstant[] atypeRet = m_atypeRetReg;
                if (atypeRet == null)
                    {
                    ConstantPool      pool        = frame.poolContext();
                    MethodConstant    constMethod = (MethodConstant) frame.getConstant(m_nMethodId);
                    SignatureConstant sigTarget   = getTargetSignature(pool, hTarget, constMethod);

                    atypeRet = m_atypeRetReg = constMethod.getRawReturns().clone();
                    for (int j = 0, cRet = atypeRet.length; j < cRet; j++)
                        {
                        atypeRet[j] = atypeRet[j].resolveGenerics(pool, sigTarget.getRawReturns()[j]);
                        }
                    }

                frame.introduceResolvedVar(anRet[i], atypeRet[i]);
                }
            }
        }

    /**
     * Use the target's type info to resolve the method's signature.
     * REVIEW GG - this seems very heavy handed; is there a better way?
     */
    private SignatureConstant getTargetSignature(ConstantPool pool, ObjectHandle hTarget, MethodConstant idMethod)
        {
        TypeConstant typeTarget = pool.ensureAccessTypeConstant(hTarget.getType(), Access.PRIVATE);
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();
        return infoTarget.getMethodBySignature(idMethod.getSignature()).getSignature();
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

    private TypeComposition m_clazz;       // cached class
    private CallChain       m_chain;       // cached call chain

    private TypeConstant    m_typeRetReg;   // cached return register type
    private TypeConstant[]  m_atypeRetReg;  // cached return registers types
    }
