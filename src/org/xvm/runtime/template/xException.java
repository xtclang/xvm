package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Return_0;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xException
        extends xConst
    {
    public static xException INSTANCE;

    public xException(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        MethodStructure ct = getMethodStructure("construct", new String[]{"String?", "Exception?"}, VOID);
        ct.setOps(new Op[] // #0 - text, #1 - cause
            {
            new L_Set(Op.CONSTANT_OFFSET - getProperty("text").getIdentityConstant().getPosition(), 0),
            new L_Set(Op.CONSTANT_OFFSET - getProperty("cause").getIdentityConstant().getPosition(), 1),
            new Return_0(),
            });

        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        return makeMutableStruct(clazz, null, null);
        }

    // stock exception: TODO: use the actual exceptions

    public static ExceptionHandle unassignedReference()
        {
        return makeHandle("Unassigned reference");
        }

    public static ExceptionHandle unassignedFields()
        {
        return makeHandle("Unassigned fields");
        }

    public static ExceptionHandle immutableObject()
        {
        return makeHandle("Immutable object");
        }

    public static ExceptionHandle illegalOperation()
        {
        return makeHandle("IllegalOperation");
        }

    public static ExceptionHandle unsupportedOperation()
        {
        return makeHandle("UnsupportedOperation");
        }

    public static ExceptionHandle outOfRange(long lIndex, long cSize)
        {
        return makeHandle(lIndex < 0 ?
                "Negative index: " + lIndex :
                "Array index " + lIndex + " out of range 0.." + (cSize-1));
        }

    // ---- ObjectHandle helpers -----

    public static ExceptionHandle makeHandle(String sMessage)
        {
        ExceptionHandle hException = makeMutableStruct(INSTANCE.getCanonicalClass(), null, null);

        hException.setField("text", xString.makeHandle(sMessage));
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
        }

    private static ExceptionHandle makeMutableStruct(TypeComposition clazz,
                                                     ExceptionHandle hCause, Throwable eCause)
        {
        clazz = clazz.ensureAccess(Access.STRUCT);

        ExceptionHandle hException = new ExceptionHandle(clazz, true, eCause);

        Frame frame = ServiceContext.getCurrentContext().getCurrentFrame();

        hException.setField("stackTrace", xString.makeHandle(frame.getStackTrace()));
        hException.setField("cause", hCause == null ? xNullable.NULL : hCause);

        return hException;
        }
    }
