package org.xvm.runtime.template;


import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.text.xString;


/**
 * Native Exception implementation.
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
    public void initNative()
        {
        if (this == INSTANCE)
            {
            // cache all the well-known exception classes
            s_clzException                  = INSTANCE.getCanonicalClass();
            s_clzIllegalArgument            = f_templates.getTemplate("IllegalArgument"              ).getCanonicalClass();
            s_clzIllegalState               = f_templates.getTemplate("IllegalState"                 ).getCanonicalClass();
            s_clzInvalidType                = f_templates.getTemplate("reflect.InvalidType"          ).getCanonicalClass();
            s_clzOutOfBounds                = f_templates.getTemplate("OutOfBounds"                  ).getCanonicalClass();
            s_clzReadOnly                   = f_templates.getTemplate("ReadOnly"                     ).getCanonicalClass();
            s_clzTimedOut                   = f_templates.getTemplate("TimedOut"                     ).getCanonicalClass();
            s_clzTypeMismatch               = f_templates.getTemplate("TypeMismatch"                 ).getCanonicalClass();
            s_clzUnsupportedOperation       = f_templates.getTemplate("UnsupportedOperation"         ).getCanonicalClass();
            s_clzDivisionByZero             = f_templates.getTemplate("numbers.Number.DivisionByZero").getCanonicalClass();
            s_clzPathException              = f_templates.getTemplate("fs.PathException"             ).getCanonicalClass();
            s_clzFileNotFoundException      = f_templates.getTemplate("fs.FileNotFound"              ).getCanonicalClass();
            s_clzAccessDeniedException      = f_templates.getTemplate("fs.AccessDenied"              ).getCanonicalClass();
            s_clzFileAlreadyExistsException = f_templates.getTemplate("fs.FileAlreadyExists"         ).getCanonicalClass();
            s_clzIOException                = f_templates.getTemplate("io.IOException"               ).getCanonicalClass();

            METHOD_FORMAT_EXCEPTION = getStructure().findMethod("formatExceptionString", 2);

            markNativeMethod("toString", VOID, STRING);

            getCanonicalType().invalidateTypeInfo();
            }
        }

    @Override
    public ObjectHandle createStruct(Frame frame, ClassComposition clazz)
        {
        return makeMutableStruct(frame, clazz, null);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        ExceptionHandle hException = (ExceptionHandle) hTarget;

        // String formatExceptionString(String exceptionName, String stackTrace)

        ObjectHandle[] ahVars = new ObjectHandle[METHOD_FORMAT_EXCEPTION.getMaxVars()];
        ahVars[0] = xString.makeHandle(getClassConstant().getValueString()); // appender
        ahVars[1] = hException.getField("stackTrace");

        return frame.call1(METHOD_FORMAT_EXCEPTION, hException, ahVars, iReturn);
        }


    // ---- stock exceptions -----------------------------------------------------------------------

    public static ExceptionHandle immutableObject(Frame frame)
        {
        return makeHandle(frame, "Immutable object");
        }

    public static ExceptionHandle serviceTerminated(Frame frame, String sService)
        {
        return makeHandle(frame, "Service terminated: " + sService);
        }

    public static ExceptionHandle illegalArgument(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzIllegalArgument, sMsg);
        }

    public static ExceptionHandle illegalCast(Frame frame, String sType)
        {
        return makeHandle(frame, s_clzTypeMismatch, sType);
        }

    public static ExceptionHandle illegalState(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzIllegalState, sMsg);
        }

    public static ExceptionHandle invalidType(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzInvalidType, sMsg);
        }

    public static ExceptionHandle mutableObject(Frame frame)
        {
        return illegalArgument(frame, "mutable object cannot be used for a service call");
        }

    public static ExceptionHandle outOfBounds(Frame frame, long lIndex, long cSize)
        {
        return outOfBounds(frame, lIndex < 0 ?
                "Negative index: " + lIndex :
                "Array index " + lIndex + " out of range 0.." + (cSize-1));
        }

    public static ExceptionHandle outOfBounds(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzOutOfBounds, sMsg);
        }

    public static ExceptionHandle readOnly(Frame frame)
        {
        return makeHandle(frame, s_clzReadOnly, null);
        }

    public static ExceptionHandle timedOut(Frame frame, String sMs)
        {
        return makeHandle(frame, s_clzTimedOut, sMs);
        }

    public static ExceptionHandle unassignedFields(Frame frame, String sClass, List<String> listNames)
        {
        return illegalState(frame, "Unassigned fields for \"" + sClass + "\": " + listNames);
        }

    public static ExceptionHandle unassignedReference(Frame frame)
        {
        return illegalState(frame, "Unassigned reference");
        }

    public static ExceptionHandle unsupportedOperation(Frame frame)
        {
        return unsupportedOperation(frame, null);
        }

    public static ExceptionHandle unsupportedOperation(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzUnsupportedOperation, sMsg);
        }

    public static ExceptionHandle divisionByZero(Frame frame)
        {
        return makeHandle(frame, s_clzDivisionByZero, null);
        }

    public static ExceptionHandle pathException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzPathException, sMsg);
        hException.setField("path", path);
        return hException;
        }

    public static ExceptionHandle fileNotFoundException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzFileNotFoundException, sMsg);
        hException.setField("path", path);
        return hException;
        }

    public static ExceptionHandle accessDeniedException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzAccessDeniedException, sMsg);
        hException.setField("path", path);
        return hException;
        }

    public static ExceptionHandle fileAlreadyExistsException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzFileAlreadyExistsException, sMsg);
        hException.setField("path", path);
        return hException;
        }

    public static ExceptionHandle ioException(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzIOException, sMsg);
        }


    // ---- ObjectHandle helpers -------------------------------------------------------------------

    public static ExceptionHandle makeHandle(Frame frame, String sMessage)
        {
        return makeHandle(frame, s_clzException, sMessage, null);
        }

    public static ExceptionHandle makeHandle(Frame frame, String sMessage, ExceptionHandle hCause)
        {
        return makeHandle(frame, s_clzException, sMessage, hCause);
        }

    public static ExceptionHandle makeHandle(Frame frame, ClassComposition clzEx, String sMessage)
        {
        return makeHandle(frame, clzEx, sMessage, null);
        }

    public static ExceptionHandle makeHandle(Frame frame, ClassComposition clzEx,
                                             String sMessage, ExceptionHandle hCause)
        {
        ExceptionHandle hException = makeMutableStruct(frame, clzEx, null);

        hException.setField("text",  sMessage == null ? xNullable.NULL : xString.makeHandle(sMessage));
        hException.setField("cause", hCause == null   ? xNullable.NULL : hCause);
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
        }

    private static ExceptionHandle makeMutableStruct(Frame frame, ClassComposition clxEx, Throwable eCause)
        {
        clxEx = clxEx.ensureAccess(Access.STRUCT);

        ExceptionHandle hException = new ExceptionHandle(clxEx, true, eCause);

        hException.setField("stackTrace", xString.makeHandle(
                frame == null ? "" : frame.getStackTrace()));

        return hException;
        }

    // ----- well-known exception classes ----------------------------------------------------------

    private static ClassComposition s_clzException;
    private static ClassComposition s_clzIllegalArgument;
    private static ClassComposition s_clzIllegalState;
    private static ClassComposition s_clzInvalidType;
    private static ClassComposition s_clzOutOfBounds;
    private static ClassComposition s_clzReadOnly;
    private static ClassComposition s_clzTimedOut;
    private static ClassComposition s_clzTypeMismatch;
    private static ClassComposition s_clzUnsupportedOperation;
    private static ClassComposition s_clzDivisionByZero;
    private static ClassComposition s_clzPathException;
    private static ClassComposition s_clzFileNotFoundException;
    private static ClassComposition s_clzAccessDeniedException;
    private static ClassComposition s_clzFileAlreadyExistsException;

    private static ClassComposition s_clzIOException;

    private static MethodStructure METHOD_FORMAT_EXCEPTION;
    }
