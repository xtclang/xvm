package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * RETURN_1 op-code.
 *
 * @author gg 2017.03.08
 */
public class Throw extends Op
    {
    private final int f_nValue;

    public Throw(int nValue)
        {
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // there are no "const" exceptions
        frame.m_hException = frame.f_ahVars[f_nValue];

        return RETURN_EXCEPTION;
        }
    }
