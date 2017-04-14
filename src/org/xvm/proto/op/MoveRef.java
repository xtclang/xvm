package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.template.xRef;
import org.xvm.proto.template.xRef.RefHandle;

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

    @Override
    public int process(Frame frame, int iPC)
        {
        // TODO: validate the source/destination compatibility

        Frame.VarInfo infoSrc = frame.f_aInfo[f_nSrcValue];

        TypeComposition clzRef = xRef.INSTANCE.resolve(
                new TypeComposition[] {infoSrc.f_clazz});
        RefHandle hRef = new RefHandle(clzRef, frame, f_nSrcValue);

        frame.f_ahVar[f_nDestValue] = hRef;

        return iPC + 1;
        }

    }
