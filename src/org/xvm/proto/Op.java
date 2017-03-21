package org.xvm.proto;


/**
 * The ops.
 *
 * @author gg 2017.02.21
 */
public abstract class Op
    {
    // execution-registers; [0] = iScope
    public static final int I_SCOPE = 0;

    // return values
    public static final int RETURN_NORMAL = -1;
    public static final int RETURN_EXCEPTION = -2;


    protected ObjectHandle resolveArgument(Frame frame, TypeName typeName, int nValue)
        {
        return nValue > 0 ?
                frame.f_ahVars[nValue] :
                frame.f_context.f_heap.resolveConstHandle(frame.f_hTarget, typeName, -nValue);
        }

    // returns a positive iPC or a negative RETURN_*
    public abstract int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar);
    }
