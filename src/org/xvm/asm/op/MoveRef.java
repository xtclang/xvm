package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.OpInvocable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.Ref;
import org.xvm.proto.template.Ref.RefHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;

/**
 * MOV_REF lvalue-src, lvalue-dest ; move reference-to-source to destination
 *
 * @author gg 2017.03.08
 */
public class MoveRef extends OpInvocable
    {
    private final int f_nSrcValue;
    private final int f_nDestValue;

    public MoveRef(int nSource, int nDest)
        {
        f_nSrcValue = nSource;
        f_nDestValue = nDest;
        }

    public MoveRef(DataInput in)
            throws IOException
        {
        f_nSrcValue = in.readInt();
        f_nDestValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_MOV_REF);
        out.writeInt(f_nSrcValue);
        out.writeInt(f_nDestValue);
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
    }
