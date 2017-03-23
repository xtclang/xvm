package org.xvm.proto;


/**
 * The ops.
 *
 * @author gg 2017.02.21
 */
public abstract class Op
    {
    // execution-registers
    public static final int I_SCOPE = 0;
    public static final int I_GUARD = 1;

    // return values
    public static final int RETURN_NORMAL = -1;
    public static final int RETURN_EXCEPTION = -2;

    // below methods are non static for future caching purposes

    protected ObjectHandle resolveConstArgument(Frame frame, int nArg, int nValue)
        {
        return resolveConst(frame, frame.f_function.m_argTypeName[nArg], nValue);
        }

    protected ObjectHandle resolveConstReturn(Frame frame, int nReturn, int nValue)
        {
        return resolveConst(frame, frame.f_function.m_retTypeName[nReturn], nValue);
        }

    protected ObjectHandle resolveConst(Frame frame, TypeName typeName, int nValue)
        {
        return frame.f_context.f_heap.resolveConstHandle(typeName, -nValue);
        }

    protected TypeComposition resolveClassTemplate(Frame frame, int nClassConstId)
        {
        return frame.resolveClassTemplate(nClassConstId);
        }

    // returns a positive iPC or a negative RETURN_*
    public abstract int process(Frame frame, int iPC);
    }
