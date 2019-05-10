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
        // cache all the well-known exception classes
        s_clzException       = INSTANCE.getCanonicalClass();
        s_clzIllegalArgument = f_templates.getTemplate("IllegalArgument").getCanonicalClass();
        s_clzIllegalState    = f_templates.getTemplate("IllegalState").getCanonicalClass();
        s_clzPathException   = f_templates.getTemplate("fs.PathException").getCanonicalClass();

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

    public static ExceptionHandle mutableObject()
        {
        return makeHandle("mutable object cannot be used for a service call");
        }

    public static ExceptionHandle illegalOperation()
        {
        return makeHandle("IllegalOperation");
        }

    public static ExceptionHandle illegalCast(String sType)
        {
        return makeHandle("IllegalCast: " + sType);
        }

    public static ExceptionHandle unsupportedOperation()
        {
        return makeHandle("UnsupportedOperation");
        }

    public static ExceptionHandle pathException(String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(s_clzPathException, "IOException: " + sMsg);
        hException.setField("path", path);
        return hException;
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
        return makeHandle(s_clzException, sMessage);
        }

    public static ExceptionHandle makeHandle(ClassComposition clz, String sMessage)
        {
        ExceptionHandle hException = makeMutableStruct(clz, null, null);

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

    // ----- well-known exception classes ----------------------------------------------------------

    private static ClassComposition s_clzException;
    private static ClassComposition s_clzIllegalArgument;
    private static ClassComposition s_clzIllegalState;
    private static ClassComposition s_clzPathException;
    }
