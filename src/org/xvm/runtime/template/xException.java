package org.xvm.runtime.template;


import com.sun.istack.internal.Nullable;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Return_0;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xException
        extends Const
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
        // TODO: remove everything when compiler generates the constructors
        f_types.f_adapter.addMethod(f_struct, "construct", new String[]{"String", "Exception"}, VOID);
        markNativeMethod("to", VOID, STRING);

        MethodStructure ct = ensureMethodStructure("construct", new String[] {"String", "Exception"});
        ct.setOps(new Op[] // #0 - text, #1 - cause
            {
            new L_Set(Op.CONSTANT_OFFSET - getProperty("text").getIdentityConstant().getPosition(), 0),
            new L_Set(Op.CONSTANT_OFFSET - getProperty("cause").getIdentityConstant().getPosition(), 1),
            new Return_0(),
            });
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        return makeStructHandle(null, null);
        }

    // ---- ObjectHandle helpers -----

    public static ExceptionHandle immutable()
        {
        return xException.makeHandle("Immutable object");
        }

    public static ExceptionHandle makeHandle(String sMessage)
        {
        ExceptionHandle hException = makeStructHandle(null, null);

        INSTANCE.setFieldValue(hException,
                INSTANCE.getProperty("text"), xString.makeHandle(sMessage));

        return (ExceptionHandle)
                hException.f_clazz.ensureAccess(hException, Constants.Access.PUBLIC);
        }

    private static ExceptionHandle makeStructHandle(ExceptionHandle hCause, Throwable eCause)
        {
        ExceptionHandle hException = eCause == null ?
                new ExceptionHandle(INSTANCE.f_clazzCanonical, true, null) :
                new ExceptionHandle(INSTANCE.f_clazzCanonical, true, eCause);

        ServiceContext context = ServiceContext.getCurrentContext();
        Frame frame = context.getCurrentFrame();

        INSTANCE.setFieldValue(hException, INSTANCE.getProperty("stackTrace"),
                xString.makeHandle(frame.getStackTrace()));
        INSTANCE.setFieldValue(hException, INSTANCE.getProperty("cause"),
            hCause == null ? xNullable.NULL : hCause);

        return hException;
        }
    }
