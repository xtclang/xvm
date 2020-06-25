package org.xvm.asm.op;


import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;


/**
 * A synthetic op used to call a default initializer for a struct.
 */
public class SynInit
        extends Op
    {
    /**
     * Construct a Synthetic Init op.
     */
    public SynInit()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_SYN_INIT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle    hStruct  = frame.getThis();
        MethodStructure methodAI = hStruct.getComposition().ensureAutoInitializer();

        return methodAI == null
                ? iPC + 1
                : frame.call1(methodAI, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE);
        }
    }