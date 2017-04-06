package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.op.Return_0;
import org.xvm.proto.op.Set;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xException
        extends TypeCompositionTemplate
    {
    public static xException INSTANCE;

    public xException(TypeSet types)
        {
        super(types, "x:Exception", "x:Object", Shape.Const);

        addImplement("x:Const");

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        // @inject Iterable<StackFrame> stackTrace;
        ensurePropertyTemplate("text", "x:String");
        ensurePropertyTemplate("cause", "this.Type");
        ensurePropertyTemplate("stackTrace", "x:String"); // TODO: replace "x:String" with "x:Iterable<this.Type.StackFrame>"

        FunctionTemplate ct = addFunctionTemplate("construct", new String[]{"x:Exception", "x:String|x:Nullable", "x:Exception|x:Nullable"}, VOID);

        ct.m_aop = new Op[] // #0 - this:struct, #1 - text, #2 - cause
            {
            new Set(0, f_types.f_constantPool.getPropertyConstId("x:Exception", "text"), 1),
            new Set(0, f_types.f_constantPool.getPropertyConstId("x:Exception", "cause"), 2),
            new Return_0(),
            };
        ct.m_cVars = 3;
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ObjectHandle.ExceptionHandle(f_clazzCanonical, false);
        }

    @Override
    public ObjectHandle createStruct(Frame frame)
        {
        ObjectHandle.ExceptionHandle handle = new ObjectHandle.ExceptionHandle(f_clazzCanonical, true);
        setProperty(handle, "stackTrace", xString.makeHandle(frame.getStackTrace()));
        return handle;
        }

    }
