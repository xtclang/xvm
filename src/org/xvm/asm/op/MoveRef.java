package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref;
import org.xvm.runtime.template.Ref.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MOV_REF lvalue-src, lvalue-dest ; move reference-to-source to destination
 */
public class MoveRef
        extends OpInvocable
    {
    /**
     * Construct a MOV_REF op.
     *
     * @param nSource  the source location
     * @param nDest    the destination location
     */
    public MoveRef(int nSource, int nDest)
        {
        f_nSrcValue  = nSource;
        f_nDestValue = nDest;
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
        f_nSrcValue  = readPackedInt(in);
        f_nDestValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_MOV_REF);
        writePackedLong(out, f_nSrcValue);
        writePackedLong(out, f_nDestValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(f_nSrcValue);
        RefHandle hRef;

        if (infoSrc.m_nStyle == Frame.VAR_DYNAMIC_REF)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            hRef = (RefHandle) frame.f_ahVar[f_nSrcValue];
            }
        else
            {
            TypeComposition clzRef = Ref.INSTANCE.ensureClass(
                    Collections.singletonMap("RefType", infoSrc.f_clazz.ensurePublicType()));

            hRef = new RefHandle(clzRef, frame, f_nSrcValue);
            }

        frame.f_ahVar[f_nDestValue] = hRef;

        return iPC + 1;
        }

    private final int f_nSrcValue;
    private final int f_nDestValue;
    }
