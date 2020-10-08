package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;


/**
 * MOV_REF rvalue-src, lvalue-dest ; move Ref-to-source to destination (read-only)
 */
public class MoveRef
        extends OpMove
    {
    /**
     * Construct a REF op for the passed arguments.
     *
     * @param regSrc   the source Register
     * @param argDest  the destination Argument
     */
    public MoveRef(Register regSrc, Argument argDest)
        {
        super(regSrc, argDest);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveRef(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        RefHandle    hRef;
        boolean      fNextReg = frame.isNextRegister(m_nToValue);
        TypeConstant typeReg  = null;
        if (m_nFromValue >= 0)
            {
            Frame.VarInfo infoSrc = frame.getVarInfo(m_nFromValue);
            if (infoSrc.isDynamic())
                {
                // the "dynamic ref" register must contain a RefHandle itself
                hRef = (RefHandle) frame.f_ahVar[m_nFromValue];
                if (fNextReg)
                    {
                    typeReg = infoSrc.getType();
                    }
                }
            else
                {
                ClassComposition clzRef = xRef.INSTANCE.ensureParameterizedClass(
                        frame.poolContext(), infoSrc.getType());
                hRef    = new RefHandle(clzRef, frame, m_nFromValue);
                typeReg = clzRef.getType();
                }
            }
        else
            {
            switch (m_nFromValue)
                {
                case A_THIS:
                case A_TARGET:
                case A_STRUCT:
                    {
                    ConstantPool pool      = frame.poolContext();
                    ObjectHandle hReferent = frame.getThis();

                    typeReg = pool.ensureParameterizedTypeConstant(pool.typeRef(), hReferent.getType());
                    hRef    = new RefHandle(frame.ensureClass(typeReg), null, hReferent);
                    break;
                    }

                default:
                    throw new IllegalStateException();
                }
            }

        if (fNextReg)
            {
            frame.introduceResolvedVar(m_nToValue, typeReg);
            }

        // the destination type must be the same as the source
        frame.assignValue(m_nToValue, hRef);

        return iPC + 1;
        }
    }
