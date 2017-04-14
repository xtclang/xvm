package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.Utils;

/**
 * MOV rvalue-src, lvalue-dest
 *
 * @author gg 2017.03.08
 */
public class Move extends Op
    {
    final private int f_nFromValue;
    final private int f_nToValue;

    public Move(int nFrom, int nTo)
        {
        f_nToValue = nTo;
        f_nFromValue = nFrom;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // TODO: validate the source/destination compatibility

        frame.f_ahVar[f_nToValue] =
            f_nFromValue >= 0 ? frame.f_ahVar[f_nFromValue] :
                Utils.resolveConst(frame, f_nFromValue);

        return iPC + 1;
        }
    }
