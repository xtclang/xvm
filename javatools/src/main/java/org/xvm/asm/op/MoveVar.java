package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
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
        try
            {
            int nFrom = m_nFromValue;
            if (frame.isDynamicVar(nFrom))
                {
                // the "dynamic ref" register must contain a RefHandle itself
                int       nTo  = m_nToValue;
                RefHandle hRef = (RefHandle) frame.f_ahVar[nFrom];

                if (frame.isNextRegister(nTo))
                    {
                    frame.introduceResolvedVar(nTo, hRef.getType());
                    }

                // the destination type must be the same as the source
                return frame.assignValue(nTo, hRef);
                }

            ObjectHandle hReferent = null;
            if (frame.isAssigned(nFrom))
                {
                hReferent = frame.getArgument(nFrom);
                if (isDeferred(hReferent))
                    {
                    return hReferent.proceed(frame, frameCaller ->
                            complete(frameCaller, frameCaller.popStack()));
                    }
                }
            return complete(frame, hReferent);
            }
       catch (ExceptionHandle.WrapperException e)
           {
           return frame.raiseException(e);
           }
        }

    protected int complete(Frame frame, ObjectHandle hReferent)
        {
        int     nFrom    = m_nFromValue;
        int     nTo      = m_nToValue;
        boolean fNextReg = frame.isNextRegister(nTo);

        RefHandle hRef;
        if (fNextReg || nTo == Op.A_STACK)
            {
            TypeConstant typeReferent = hReferent == null ? null : hReferent.getType();

            // the ref type must be "known" by this container
            if (typeReferent == null || !typeReferent.isShared(frame.poolContext()))
                {
                typeReferent = nFrom == Op.A_STACK
                        ? frame.poolContext().typeObject()
                        : frame.getVarInfo(nFrom).getType();
                }
            TypeComposition clzRef = xVar.INSTANCE.
                    ensureParameterizedClass(frame.f_context.f_container, typeReferent);
            hRef = new RefHandle(clzRef, frame, nFrom);
            if (fNextReg)
                {
                frame.introduceResolvedVar(nTo, hRef.getType());
                }
            }
        else
            {
            TypeComposition clzRef = frame.getVarInfo(nTo).getType().ensureClass(frame);
            hRef = new RefHandle(clzRef, frame, nFrom);
            }

        // the destination type must be the same as the source
        return frame.assignValue(nTo, hRef);
        }
    }