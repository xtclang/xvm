package org.xvm.runtime.template;


import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native Exception implementation.
 */
public class xException
        extends xConst
    {
    public static xException INSTANCE;

    public xException(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

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
            s_clzDeadlock                   = f_container.getTemplate("Deadlock"                     ).getCanonicalClass();
            s_clzIllegalArgument            = f_container.getTemplate("IllegalArgument"              ).getCanonicalClass();
            s_clzIllegalState               = f_container.getTemplate("IllegalState"                 ).getCanonicalClass();
            s_clzInvalidType                = f_container.getTemplate("reflect.InvalidType"           ).getCanonicalClass();
            s_clzNotImplemented             = f_container.getTemplate("NotImplemented"               ).getCanonicalClass();
            s_clzOutOfBounds                = f_container.getTemplate("OutOfBounds"                  ).getCanonicalClass();
            s_clzReadOnly                   = f_container.getTemplate("ReadOnly"                     ).getCanonicalClass();
            s_clzSizeLimited                = f_container.getTemplate("collections.SizeLimited"      ).getCanonicalClass();
            s_clzTimedOut                   = f_container.getTemplate("TimedOut"                     ).getCanonicalClass();
            s_clzTypeMismatch               = f_container.getTemplate("TypeMismatch"                 ).getCanonicalClass();
            s_clzUnsupported                = f_container.getTemplate("Unsupported"                  ).getCanonicalClass();
            s_clzDivisionByZero             = f_container.getTemplate("numbers.Number.DivisionByZero").getCanonicalClass();
            s_clzPathException              = f_container.getTemplate("fs.PathException"             ).getCanonicalClass();
            s_clzFileNotFoundException      = f_container.getTemplate("fs.FileNotFound"              ).getCanonicalClass();
            s_clzAccessDeniedException      = f_container.getTemplate("fs.AccessDenied"              ).getCanonicalClass();
            s_clzFileAlreadyExistsException = f_container.getTemplate("fs.FileAlreadyExists"         ).getCanonicalClass();
            s_clzIOException                = f_container.getTemplate("io.IOException"               ).getCanonicalClass();
            s_clzIOIllegalUTF               = f_container.getTemplate("io.IllegalUTF"                ).getCanonicalClass();

            METHOD_FORMAT_EXCEPTION = getStructure().findMethod("formatExceptionString", 2);

            markNativeMethod("toString", VOID, STRING);

            invalidateTypeInfo();
            }
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        return makeMutableStruct(frame, clazz, null);
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ExceptionHandle hException = (ExceptionHandle) hTarget;
        if (idProp.getName().equals("text"))
            {
            ObjectHandle hText = hException.getField(frame, "text");
            if (hException.f_sRTError != null)
                {
                String sTag = ((StringHandle) hText).getStringValue();
                System.err.println("*** " + sTag + '\n' + hException.f_sRTError);
                }
            return frame.assignValue(iReturn, hText);
            }
        return super.getFieldValue(frame, hTarget, idProp, iReturn);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        ExceptionHandle hException = (ExceptionHandle) hTarget;

        // String formatExceptionString(String exceptionName, String stackTrace)

        ObjectHandle[] ahVars = new ObjectHandle[METHOD_FORMAT_EXCEPTION.getMaxVars()];
        ahVars[0] = xString.makeHandle(getClassConstant().getValueString()); // appender
        ahVars[1] = hException.getField(frame, "stackTrace");

        return frame.call1(METHOD_FORMAT_EXCEPTION, hException, ahVars, iReturn);
        }


    // ---- stock exceptions -----------------------------------------------------------------------

    public static ExceptionHandle immutableObject(Frame frame)
        {
        return makeHandle(frame, "Immutable object");
        }

    public static ExceptionHandle notFreezableProperty(Frame frame, String sProp, TypeConstant type)
        {
        String sDesc = type.isConstant() ? "const" : "an immutable";
        return makeHandle(frame, "Property \"" + sProp + "\" on " + sDesc + " \"" +
                type.removeAccess().getValueString() + "\" is not freezable");
        }

    public static ExceptionHandle immutableObjectProperty(Frame frame, String sProp, TypeConstant type)
        {
        String sDesc = type.isConstant() ? "const" : "an immutable";
        return makeHandle(frame, s_clzReadOnly,
                "Attempt to modify property \"" + sProp + "\" on " + sDesc + " \"" +
                    type.removeAccess().getValueString() + '"');
        }

    public static ExceptionHandle unknownProperty(Frame frame, String sProp, TypeConstant type)
        {
        return makeHandle(frame, "Unknown property: \"" + sProp + "\" on " + type.getValueString());
        }

    public static ExceptionHandle serviceTerminated(Frame frame, String sService)
        {
        return makeHandle(frame, "Service terminated: " + sService);
        }

    public static ExceptionHandle deadlock(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzDeadlock, sMsg);
        }

    public static ExceptionHandle illegalArgument(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzIllegalArgument, sMsg);
        }

    public static ExceptionHandle typeMismatch(Frame frame, String sType)
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

    public static ExceptionHandle mutableObject(Frame frame, TypeConstant type)
        {
        type = type.removeAccess().
                    resolveGenerics(frame.poolContext(), frame.getGenericsResolver(true));
        return illegalArgument(frame, "Mutable object of type \"" + type.getValueString()
                + "\" cannot be used for a service call");
        }

    public static ExceptionHandle notImplemented(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzNotImplemented, sMsg);
        }

    public static ExceptionHandle outOfBounds(Frame frame, long lIndex, long cSize)
        {
        return outOfBounds(frame, lIndex < 0 ?
                "Negative index: " + lIndex :
                "Index " + lIndex + " out of range 0.." + (cSize-1));
        }

    public static ExceptionHandle outOfBounds(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzOutOfBounds, sMsg);
        }

    public static ExceptionHandle readOnly(Frame frame, xArray.Mutability mutability)
        {
        String sMsg = switch (mutability)
            {
            case Constant   -> "Constant array";
            case Fixed      -> "Fixed size array";
            case Persistent -> "Persistent array";
            default         -> throw new IllegalStateException();
            };
        return makeHandle(frame, s_clzReadOnly, sMsg);
        }

    public static ExceptionHandle readOnly(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzReadOnly, sMsg);
        }

    public static ExceptionHandle sizeLimited(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzSizeLimited, sMsg);
        }

    public static ExceptionHandle timedOut(Frame frame, String sMsg, ObjectHandle hTimeout)
        {
        ExceptionHandle hEx = makeHandle(frame, s_clzTimedOut, sMsg);
        hEx.setField(frame, "timeout", hTimeout);
        return hEx;
        }

    public static boolean isTimedOut(ExceptionHandle e)
        {
        return e.getComposition() == s_clzTimedOut;
        }

    public static ExceptionHandle unassignedValue(Frame frame, String sName)
        {
        return illegalState(frame, "Unassigned value: \"" + sName + '"');
        }

    public static ExceptionHandle unassignedFields(Frame frame, String sClass, List<String> listNames)
        {
        return illegalState(frame, "Unassigned fields for \"" + sClass + "\": " + listNames);
        }

    public static ExceptionHandle unassignedReference(Frame frame)
        {
        return illegalState(frame, "Unassigned reference");
        }

    public static ExceptionHandle unsupported(Frame frame)
        {
        return unsupported(frame, null);
        }

    public static ExceptionHandle unsupported(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzUnsupported, sMsg);
        }

    public static ExceptionHandle divisionByZero(Frame frame)
        {
        return makeHandle(frame, s_clzDivisionByZero, null);
        }

    public static ExceptionHandle pathException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzPathException, sMsg);
        hException.setField(frame, "path", path);
        return hException;
        }

    public static ExceptionHandle fileNotFoundException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzFileNotFoundException, sMsg);
        hException.setField(frame, "path", path);
        return hException;
        }

    public static ExceptionHandle accessDeniedException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzAccessDeniedException, sMsg);
        hException.setField(frame, "path", path);
        return hException;
        }

    public static ExceptionHandle fileAlreadyExistsException(Frame frame, String sMsg, ObjectHandle path)
        {
        ExceptionHandle hException = makeHandle(frame, s_clzFileAlreadyExistsException, sMsg);
        hException.setField(frame, "path", path);
        return hException;
        }

    public static ExceptionHandle ioException(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzIOException, sMsg);
        }

    public static ExceptionHandle illegalUTF(Frame frame, String sMsg)
        {
        return makeHandle(frame, s_clzIOIllegalUTF, sMsg);
        }


    // ---- ObjectHandle helpers -------------------------------------------------------------------

    public static ExceptionHandle makeHandle(Frame frame, String sMessage)
        {
        return makeHandle(frame, s_clzException, sMessage, (ExceptionHandle) null);
        }

    public static ExceptionHandle makeHandle(Frame frame, String sMessage, ExceptionHandle hCause)
        {
        return makeHandle(frame, s_clzException, sMessage, hCause);
        }

    public static ExceptionHandle makeHandle(Frame frame, TypeComposition clzEx, String sMessage)
        {
        return makeHandle(frame, clzEx, sMessage, (ExceptionHandle) null);
        }

    public static ExceptionHandle makeHandle(Frame frame, TypeComposition clzEx,
                                             String sMessage, ExceptionHandle hCause)
        {
        ExceptionHandle hException = makeMutableStruct(frame, clzEx, null);

        hException.setField(frame, "text",  sMessage == null ? xNullable.NULL : xString.makeHandle(sMessage));
        hException.setField(frame, "cause", hCause == null   ? xNullable.NULL : hCause);
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
        }

    /**
     * Create a runtime exception that creates an obscured "tag" exception and hides the actual
     * message to be logged to the system console.
     *
     * @return an exception handle with an obscured message
     */
    public static ExceptionHandle makeObscure(Frame frame, String sErr)
        {
        return makeHandle(frame, s_clzException, "RTError: " + System.currentTimeMillis(), sErr);
        }

    public static ExceptionHandle obscureIoException(Frame frame, String sErr)
        {
        return makeHandle(frame, s_clzIOException, "RTError: " + System.currentTimeMillis(), sErr);
        }

    public static ExceptionHandle makeHandle(Frame frame, TypeComposition clzEx,
                                             String sMessage, String sRtError)
        {
        ExceptionHandle hException = makeMutableStruct(frame, clzEx, sRtError);

        hException.setField(frame, "text",  sMessage == null ? xNullable.NULL : xString.makeHandle(sMessage));
        hException.setField(frame, "cause", xNullable.NULL);
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
        }

    private static ExceptionHandle makeMutableStruct(Frame frame, TypeComposition clxEx, String sRTError)
        {
        clxEx = clxEx.ensureAccess(Access.STRUCT);

        ExceptionHandle hException = new ExceptionHandle(clxEx, sRTError);

        hException.setField(frame, "stackTrace", xString.makeHandle(
                frame == null ? "" : frame.getStackTrace()));

        return hException;
        }

    // ----- well-known exception classes ----------------------------------------------------------

    private static ClassComposition s_clzDeadlock;
    private static ClassComposition s_clzException;
    private static ClassComposition s_clzIllegalArgument;
    private static ClassComposition s_clzIllegalState;
    private static ClassComposition s_clzInvalidType;
    private static ClassComposition s_clzNotImplemented;
    private static ClassComposition s_clzOutOfBounds;
    private static ClassComposition s_clzReadOnly;
    private static ClassComposition s_clzSizeLimited;
    private static ClassComposition s_clzTimedOut;
    private static ClassComposition s_clzTypeMismatch;
    private static ClassComposition s_clzUnsupported;
    private static ClassComposition s_clzDivisionByZero;
    private static ClassComposition s_clzPathException;
    private static ClassComposition s_clzFileNotFoundException;
    private static ClassComposition s_clzAccessDeniedException;
    private static ClassComposition s_clzFileAlreadyExistsException;
    private static ClassComposition s_clzIOException;
    private static ClassComposition s_clzIOIllegalUTF;

    private static MethodStructure METHOD_FORMAT_EXCEPTION;
    }