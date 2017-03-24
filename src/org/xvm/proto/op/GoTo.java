package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * GOTO abs-addr
 *
 * @author gg 2017.03.08
 */
public class GoTo extends Op
    {
    private final int f_nAbsAddr;

    public GoTo(int nAbsAddr)
        {
        f_nAbsAddr = nAbsAddr;
        }


    @Override
    public int process(Frame frame, int iPC)
        {
        return f_nAbsAddr;
        }
    }
