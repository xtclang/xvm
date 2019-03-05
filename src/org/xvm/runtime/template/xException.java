package org.xvm.runtime.template;


import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;

import org.xvm.runtime.ClassComposition;
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
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public ObjectHandle createStruct(Frame frame, ClassComposition clazz)
        {
        return makeMutableStruct(clazz, null, null);
        }

    // stock exception: TODO: use the actual exceptions

    public static ExceptionHandle unassignedReference()
        {
        return makeHandle("Unassigned reference");
        }

    public static ExceptionHandle unassignedFields(List<String> listNames)
        {
        return makeHandle("Unassigned fields: " + listNames);
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
        hException.setField("cause", xNullable.NULL);
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

        return hException;
        }
    }
