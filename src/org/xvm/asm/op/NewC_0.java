package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWC_0 CONSTRUCT, rvalue-parent, lvalue ; virtual "new" for child classes
 */
public class NewC_0
        extends OpCallable
    {
    /**
     * Construct a NEWC_0 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argParent    the parent Argument
     * @param argReturn    the return Argument
     */
    public NewC_0(MethodConstant constMethod, Argument argParent, Argument argReturn)
        {
        super(constMethod);

        m_argParent = argParent;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewC_0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nParentValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argParent != null)
            {
            m_nParentValue = encodeArgument(m_argParent, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nParentValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWC_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hParent = frame.getArgument(m_nParentValue);
            if (hParent == null)
                {
                return R_REPEAT;
                }

            MethodStructure constructor = getVirtualConstructor(frame, hParent);
            if (constructor == null)
                {
                return frame.raiseException(reportMissingConstructor(frame, hParent));
                }

            if (isDeferred(hParent))
                {
                ObjectHandle[] ahHolder = new ObjectHandle[] {hParent};
                Frame.Continuation stepNext = frameCaller ->
                    constructChild(frameCaller, constructor, ahHolder[0]);

                return new Utils.GetArguments(ahHolder, stepNext).doNext(frame);
                }
            return constructChild(frame, constructor, hParent);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int constructChild(Frame frame, MethodStructure constructor, ObjectHandle hParent)
        {
        IdentityConstant constClz = constructor.getParent().getParent().getIdentityConstant();
        ClassTemplate template = frame.ensureTemplate(constClz);
        TypeComposition clzTarget = template.getCanonicalClass();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
            }

        return template.construct(frame, constructor, clzTarget, hParent, Utils.OBJECTS_NONE, m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argParent = registerArgument(m_argParent, registry);
        }

    private int m_nParentValue;

    private Argument m_argParent;
    }