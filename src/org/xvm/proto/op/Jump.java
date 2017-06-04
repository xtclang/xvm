package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * JUMP rel-addr
 *
 * @author gg 2017.03.08
 */
public class Jump extends Op
    {
    private final int f_nRelAddr;

    public Jump(int nRelAddr)
        {
        f_nRelAddr = nRelAddr;
        }


    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + f_nRelAddr;
        }
    }
