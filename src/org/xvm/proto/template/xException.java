package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;
import org.xvm.proto.op.Return_0;
import org.xvm.proto.op.Set;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xException
        extends xObject
    {
    public xException(TypeSet types)
        {
        super(types, "x:Exception", "x:Object", Shape.Const);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Const");

        // @inject Iterable<StackFrame> stackTrace;
        addPropertyTemplate("text", "x:String");
        addPropertyTemplate("cause", "x:Exception");
        addPropertyTemplate("stackTrace", "x:String"); // TODO: replace "x:String" with "x:Iterable<x:Exception.StackFrame>"

        FunctionTemplate ct = addFunctionTemplate("construct", new String[]{"x:Exception", "x:String|x:Nullable", "x:Exception|x:Nullable"}, VOID);
        ct.m_aop = new Op[] // #0 - this:struct, #1 - text, #2 - cause
            {
            new Set(0, f_types.f_constantPool.ensureConstantValue("text"), 1),
            new Set(0, f_types.f_constantPool.ensureConstantValue("cause"), 2),
            new Return_0(),
            };
        ct.m_cVars = 3;
        }

    @Override
    public ObjectHandle createStruct(Frame frame)
        {
        GenericHandle handle = (GenericHandle) super.createStruct(frame);
        handle.m_mapFields.put("cause", xString.makeHandle(frame.toString()));
        return handle;
        }


    public static xException INSTANCE;
    }
