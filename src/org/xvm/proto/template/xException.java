package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import org.xvm.proto.op.LSet;
import org.xvm.proto.op.Return_0;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xException
        extends xConst
    {
    public static xException INSTANCE;

    public xException(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // TODO: remove next two lines
        f_types.f_adapter.addMethod(f_struct, "construct", new String[]{"String", "Exception"}, VOID);
        markNativeMethod("to", VOID, STRING);

        MethodTemplate ct = ensureMethodTemplate("construct", new String[]{"String|Nullable", "Exception|Nullable"});
        ct.m_aop = new Op[] // #0 - text, #1 - cause
            {
            new LSet(getProperty("text").getIdentityConstant().getPosition(), 0),
            new LSet(getProperty("cause").getIdentityConstant().getPosition(), 1),
            new Return_0(),
            };
        ct.m_cVars = 2;
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        return makeHandle(null, null);
        }

    // ---- ObjectHandle helpers -----

    public static ExceptionHandle immutable()
        {
        return xException.makeHandle("Immutable object");
        }

    public static ExceptionHandle makeHandle(String sMessage)
        {
        ExceptionHandle hException = makeHandle(null, null);

        INSTANCE.setFieldValue(hException,
                INSTANCE.getProperty("text"), xString.makeHandle(sMessage));

        return hException;
        }

    public static ExceptionHandle makeHandle(ExceptionHandle hCause, Throwable eCause)
        {
        ExceptionHandle hException = eCause == null ?
                new ExceptionHandle(INSTANCE.f_clazzCanonical, true, null) :
                new ExceptionHandle(INSTANCE.f_clazzCanonical, true, eCause);

        ServiceContext context = ServiceContext.getCurrentContext();
        Frame frame = context.getCurrentFrame();

        INSTANCE.setFieldValue(hException, INSTANCE.getProperty("stackTrace"),
                xString.makeHandle(frame.getStackTrace()));
        INSTANCE.setFieldValue(hException, INSTANCE.getProperty("cause"), hCause);

        return hException;
        }
    }
