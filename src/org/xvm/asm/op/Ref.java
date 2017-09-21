package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.OpInvocable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.Ref.RefHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;

/**
 * REF lvalue ; next register represents the Variable ref for the specified variable
 *
 * @author gg 2017.03.08
 */
public class Ref extends OpInvocable
    {
    private final int f_nSrcValue;

    public Ref(int nSrc)
        {
        f_nSrcValue = nSrc;
        }

    public Ref(DataInput in)
            throws IOException
        {
        f_nSrcValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_REF);
        out.writeInt(f_nSrcValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(f_nSrcValue);
        TypeComposition clzReferent = infoSrc.f_clazz;

        if (infoSrc.m_nStyle == Frame.VAR_DYNAMIC_REF)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            RefHandle hRef = (RefHandle) frame.f_ahVar[f_nSrcValue];

            frame.introduceVar(clzReferent, null, Frame.VAR_STANDARD, hRef);
            }
        else
            {
            TypeComposition clzRef = org.xvm.proto.template.Ref.INSTANCE.ensureClass(
                    Collections.singletonMap("RefType", infoSrc.f_clazz.ensurePublicType()));

            RefHandle hRef = new RefHandle(clzRef, frame, f_nSrcValue);

            frame.introduceVar(clzRef, null, Frame.VAR_STANDARD, hRef);
            }

        return iPC + 1;
        }
    }
