package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.OpInvocable;

/**
 * Nop
 *
 * @author gg 2017.03.08
 */
public class Nop extends OpInvocable
    {
    public Nop()
        {
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }
    }
