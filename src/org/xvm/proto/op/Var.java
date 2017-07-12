package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;

/**
 * VAR CONST_CLASS  ; (next register is an uninitialized anonymous variable)
 *
 * @author gg 2017.03.08
 */
public class Var extends Op
    {
    private final int f_nClassConstId;

    public Var(int nClassConstId)
        {
        f_nClassConstId = nClassConstId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clazz = frame.f_context.f_types.ensureComposition(f_nClassConstId);

        frame.introduceVar(clazz, null, Frame.VAR_STANDARD, null);

        return iPC + 1;
        }
    }
