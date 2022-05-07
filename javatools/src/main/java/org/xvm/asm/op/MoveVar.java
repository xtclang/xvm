package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.reflect.xRef.RefHandle;
import org.xvm.runtime.template.reflect.xVar;


/**
 * MOV_VAR rvalue-src, lvalue-dest ; move Var-to-source to destination
 */
public class MoveVar
        extends OpMove
    {
    /**
     * Construct a MOV_VAR op for the passed arguments.
     *
     * @param regSrc   the source Register
     * @param argDest  the destination Argument
     */
    public MoveVar(Register regSrc, Argument argDest)
        {
        super(regSrc, argDest);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveVar(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV_VAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int          nFrom   = m_nFromValue;
        VarInfo      infoSrc = frame.getVarInfo(nFrom);
        TypeConstant typeSrc = infoSrc.getType();

        if (infoSrc.isDynamicVar())
            {
            // the "dynamic ref" register must contain a RefHandle itself
           int       nTo  = m_nToValue;
           RefHandle hRef = (RefHandle) frame.f_ahVar[nFrom];

            if (frame.isNextRegister(nTo))
                {
                frame.introduceResolvedVar(nTo, typeSrc);
                }

            // the destination type must be the same as the source
            return frame.assignValue(nTo, hRef);
            }

        try
            {
            if (frame.isAssigned(nFrom))
                {
                ObjectHandle hReferent = frame.getArgument(nFrom);
                if (isDeferred(hReferent))
                    {
                    return hReferent.proceed(frame, frameCaller -> complete(frameCaller, typeSrc));
                    }
                }
            return complete(frame, typeSrc);
            }
       catch (ExceptionHandle.WrapperException e)
           {
           return frame.raiseException(e);
           }
        }

    protected int complete(Frame frame, TypeConstant typeSrc)
        {
        TypeComposition clzRef = xVar.INSTANCE.ensureParameterizedClass(frame.f_context.f_container, typeSrc);

        int       nTo  = m_nToValue;
        RefHandle hRef = new RefHandle(clzRef, frame, m_nFromValue);

        if (frame.isNextRegister(nTo))
            {
            frame.introduceResolvedVar(nTo, hRef.getType());
            }

        // the destination type must be the same as the source
        return frame.assignValue(nTo, hRef);
        }
    }