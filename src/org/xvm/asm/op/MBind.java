package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.Function;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MBIND rvalue-target, CONST-METHOD, lvalue-fn-result
 */
public class MBind
        extends OpInvocable
    {
    /**
     * Construct an MBIND op.
     *
     * @param nTarget    the target object containing the method
     * @param nMethodId  the method id
     * @param nRet       the location to store the resulting function
     */
    public MBind(int nTarget, int nMethodId, int nRet)
        {
        super(nTarget, nMethodId);

        m_nResultValue = nRet;
        }

    /**
     * Construct an MBIND op based on the passed arguments.
     *
     * @param argTarget    the target argument
     * @param constMethod  the method constant
     * @param regReturn    the return value register
     */
    public MBind(Argument argTarget, MethodConstant constMethod, Register regReturn)
        {
        super(argTarget, constMethod);

        m_regReturn = regReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MBind(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(readPackedInt(in), readPackedInt(in));

        m_nResultValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_regReturn != null)
            {
            m_nResultValue = encodeArgument(m_regReturn, registry);
            }

        writePackedLong(out, m_nResultValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MBIND;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepLast = frameCaller -> proceed(frameCaller, ahTarget[0]);

                return new Utils.GetArgument(ahTarget, stepLast).doNext(frame);
                }

            return proceed(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int proceed(Frame frame, ObjectHandle hTarget)
        {
        TypeComposition clz = hTarget.f_clazz;

        CallChain chain = getCallChain(frame, clz);

        return frame.assignValue(m_nResultValue, clz.f_template.isService() ?
                Function.makeAsyncHandle(chain, 0).bindTarget(hTarget) :
                Function.makeHandle(chain, 0).bindTarget(hTarget));
        }

    private int m_nResultValue;

    private Register m_regReturn;
    }
