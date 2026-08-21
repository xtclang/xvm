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
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Lazy;


/**
 * Native Exception implementation.
 */
public class xException
        extends xConst {
    public xException(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        // The boolean is retained only for NativeContainer's legacy reflective constructor
        // signature. Exception ownership is resolved through NativeTemplates; this constructor no
        // longer publishes an Exception singleton or owner-derived metadata.
    }

    @Override
    public void initNative() {
        if (NativeTemplates.get(this).isException(this)) {
            markNativeMethod("toString", VOID, STRING);

            invalidateTypeInfo();

            // Preserve the old eager bootstrap work, but bind all cached exception classes to this
            // container instead of exposing them through process-global static fields.
            info();
        }
    }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz) {
        return makeMutableStruct(frame, clazz, null);
    }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn) {
        ExceptionHandle hException = (ExceptionHandle) hTarget;
        if (idProp.getName().equals("text")) {
            ObjectHandle hText = hException.getField(frame, "text");
            if (hException.f_sRTError != null) {
                String sTag = ((StringHandle) hText).getStringValue();
                System.err.println("*** " + sTag + '\n' + hException.f_sRTError);
            }
            return frame.assignValue(iReturn, hText);
        }
        return super.getFieldValue(frame, hTarget, idProp, iReturn);
    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        ExceptionHandle hException = (ExceptionHandle) hTarget;
        // The formatter is declared by the canonical Exception template. Concrete exception
        // subclasses share that owner-local metadata; asking a subclass template to compute it
        // can return null because subclasses do not declare formatExceptionString themselves.
        MethodStructure methodFormat =
                NativeTemplates.get(hException.getComposition().getContainer()).
                        exception().info().methodFormatException();

        // String formatExceptionString(String exceptionName, String stackTrace)

        ObjectHandle[] ahVars = new ObjectHandle[methodFormat.getMaxVars()];
        ahVars[0] = xString.makeHandle(frame, getClassConstant().getValueString()); // appender
        ahVars[1] = hException.getField(frame, "stackTrace");

        return frame.call1(methodFormat, hException, ahVars, iReturn);
    }


    // ---- stock exceptions -----------------------------------------------------------------------

    public static ExceptionHandle immutableObject(Frame frame) {
        return makeHandle(frame, "Immutable object");
    }

    public static ExceptionHandle notFreezableProperty(Frame frame, String sProp, TypeConstant type) {
        String sDesc = type.isConst() ? "const" : "an immutable";
        return makeHandle(frame, "Property \"" + sProp + "\" on " + sDesc + " \"" +
                type.removeAccess().getValueString() + "\" is not freezable");
    }

    public static ExceptionHandle immutableObjectProperty(Frame frame, String sProp, TypeConstant type) {
        String sDesc = type.isConst() ? "const" : "an immutable";
        return makeHandle(frame, info(frame).clzReadOnly(),
                "Attempt to modify property \"" + sProp + "\" on " + sDesc + " \"" +
                    type.removeAccess().getValueString() + '"');
    }

    public static ExceptionHandle unknownProperty(Frame frame, String sProp, TypeConstant type) {
        return makeHandle(frame, "Unknown property: \"" + sProp + "\" on " + type.getValueString());
    }

    public static ExceptionHandle serviceTerminated(Frame frame, String sService) {
        return makeHandle(frame, "Service terminated: " + sService);
    }

    public static ExceptionHandle deadlock(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzDeadlock(), sMsg);
    }

    public static ExceptionHandle illegalArgument(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzIllegalArgument(), sMsg);
    }

    public static ExceptionHandle typeMismatch(Frame frame, String sType) {
        return makeHandle(frame, info(frame).clzTypeMismatch(), sType);
    }

    public static ExceptionHandle typeMismatch(Frame frame,
                                               TypeConstant typeActual, TypeConstant typeExpected) {
        return typeMismatch(frame, "Expected \"" + typeExpected.getValueString() +
                                   "\", actual \"" + typeActual.getValueString() + '"');
    }

    public static ExceptionHandle illegalState(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzIllegalState(), sMsg);
    }

    public static ExceptionHandle invalidType(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzInvalidType(), sMsg);
    }

    public static ExceptionHandle mutableObject(Frame frame, TypeConstant type, boolean fResponse) {
        type = type.removeAccess().
                    resolveGenerics(frame.poolContext(), frame.getGenericsResolver(true));
        return illegalArgument(frame, "A mutable object of type \"" + type.getValueString()
                + "\" cannot be " + (fResponse
                    ? "returned from a service call"
                    : "used as an argument to a service call"));
    }

    public static ExceptionHandle notImplemented(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzNotImplemented(), sMsg);
    }

    public static ExceptionHandle outOfBounds(Frame frame, long lIndex, long cSize) {
        return outOfBounds(frame, lIndex < 0 ?
                "Negative index: " + lIndex :
                "Index " + lIndex + " out of range 0.." + (cSize-1));
    }

    public static ExceptionHandle outOfBounds(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzOutOfBounds(), sMsg);
    }

    public static ExceptionHandle outOfMemory(Frame frame) {
        return makeHandle(frame, info(frame).clzOutOfMemory(), null);
    }

    public static ExceptionHandle readOnly(Frame frame, xArray.Mutability mutability) {
        String sMsg = switch (mutability) {
            case Constant   -> "Constant array";
            case Fixed      -> "Fixed size array";
            case Persistent -> "Persistent array";
            default         -> throw new IllegalStateException();
        };
        return makeHandle(frame, info(frame).clzReadOnly(), sMsg);
    }

    public static ExceptionHandle readOnly(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzReadOnly(), sMsg);
    }

    public static ExceptionHandle sizeLimited(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzSizeLimited(), sMsg);
    }

    public static ExceptionHandle stackOverflow(Frame frame) {
        return makeHandle(frame, info(frame).clzStackOverflow(), null);
    }

    public static ExceptionHandle timedOut(Frame frame, String sMsg, ObjectHandle hTimeout) {
        ExceptionHandle hEx = makeHandle(frame, info(frame).clzTimedOut(), sMsg);
        hEx.setField(frame, "timeout", hTimeout);
        return hEx;
    }

    public static boolean isTimedOut(ExceptionHandle e) {
        ExceptionInfo info = NativeTemplates.get(e.getComposition().getContainer()).exception().info();
        return e.getComposition() == info.clzTimedOut();
    }

    public static ExceptionHandle unassignedValue(Frame frame, String sName) {
        return illegalState(frame, "Unassigned value: \"" + sName + '"');
    }

    public static ExceptionHandle unassignedFields(Frame frame, String sClass, List<String> listNames) {
        return illegalState(frame, "Unassigned fields for \"" + sClass + "\": " + listNames);
    }

    public static ExceptionHandle unassignedReference(Frame frame) {
        return illegalState(frame, "Unassigned reference");
    }

    public static ExceptionHandle unsupported(Frame frame) {
        return unsupported(frame, null);
    }

    public static ExceptionHandle unsupported(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzUnsupported(), sMsg);
    }

    public static ExceptionHandle divisionByZero(Frame frame) {
        return makeHandle(frame, info(frame).clzDivisionByZero(), null);
    }

    public static ExceptionHandle pathException(Frame frame, String sMsg, ObjectHandle path) {
        ExceptionHandle hException = makeHandle(frame, info(frame).clzPathException(), sMsg);
        hException.setField(frame, "path", path);
        return hException;
    }

    public static ExceptionHandle fileNotFoundException(Frame frame, String sMsg, ObjectHandle path) {
        ExceptionHandle hException = makeHandle(frame, info(frame).clzFileNotFoundException(), sMsg);
        hException.setField(frame, "path", path);
        return hException;
    }

    public static ExceptionHandle accessDeniedException(Frame frame, String sMsg, ObjectHandle path) {
        ExceptionHandle hException = makeHandle(frame, info(frame).clzAccessDeniedException(), sMsg);
        hException.setField(frame, "path", path);
        return hException;
    }

    public static ExceptionHandle fileAlreadyExistsException(Frame frame, String sMsg, ObjectHandle path) {
        ExceptionHandle hException = makeHandle(frame, info(frame).clzFileAlreadyExistsException(), sMsg);
        hException.setField(frame, "path", path);
        return hException;
    }

    public static ExceptionHandle ioException(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzIOException(), sMsg);
    }

    public static ExceptionHandle illegalUTF(Frame frame, String sMsg) {
        return makeHandle(frame, info(frame).clzIOIllegalUTF(), sMsg);
    }

    public static ExceptionHandle abstractMethod(Frame frame, String sMethod) {
        return makeHandle(frame, "No implementation for \"" + sMethod + '"');
    }

    public static ExceptionHandle unknownInjectable(Frame frame, TypeConstant type, String sName) {
        return makeHandle(frame, "Unknown injectable resource \"" + type.getValueString() +
                ' ' + sName + '"');
    }


    // ---- ObjectHandle helpers -------------------------------------------------------------------

    public static ExceptionHandle makeHandle(Frame frame, String sMessage) {
        return makeHandle(frame, info(frame).clzException(), sMessage, (ExceptionHandle) null);
    }

    public static ExceptionHandle makeHandle(Frame frame, String sMessage, ExceptionHandle hCause) {
        return makeHandle(frame, info(frame).clzException(), sMessage, hCause);
    }

    public static ExceptionHandle makeHandle(Container container, String sMessage) {
        return makeHandleWithoutFrame(
                NativeTemplates.get(container).exception().info().clzException(), sMessage);
    }

    public static ExceptionHandle makeHandle(Frame frame, TypeComposition clzEx, String sMessage) {
        return makeHandle(frame, clzEx, sMessage, (ExceptionHandle) null);
    }

    public static ExceptionHandle makeHandle(Frame frame, TypeComposition clzEx,
                                             String sMessage, ExceptionHandle hCause) {
        ExceptionHandle hException = makeMutableStruct(frame, clzEx, null);

        hException.setField(frame, "text",  sMessage == null
                ? xNullable.makeHandle(frame)
                : xString.makeHandle(clzEx.getContainer(), sMessage));
        hException.setField(frame, "cause", hCause == null   ? xNullable.makeHandle(frame) : hCause);
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
    }

    /**
     * Create a runtime exception that creates an obscured "tag" exception and hides the actual
     * message to be logged to the system console.
     *
     * @return an exception handle with an obscured message
     */
    public static ExceptionHandle makeObscure(Frame frame, String sErr) {
        return makeHandle(frame, info(frame).clzException(), "RTError: " +
                frame.f_context.f_container.currentTimeMillis(), sErr);
    }

    public static ExceptionHandle obscureIoException(Frame frame, String sErr) {
        return makeHandle(frame, info(frame).clzIOException(), "RTError: " +
                frame.f_context.f_container.currentTimeMillis(), sErr);
    }

    public static ExceptionHandle makeHandle(Frame frame, TypeComposition clzEx,
                                             String sMessage, String sRtError) {
        ExceptionHandle hException = makeMutableStruct(frame, clzEx, sRtError);

        hException.setField(frame, "text",  sMessage == null
                ? xNullable.makeHandle(frame)
                : xString.makeHandle(clzEx.getContainer(), sMessage));
        hException.setField(frame, "cause", xNullable.makeHandle(frame));
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
    }

    private static ExceptionHandle makeHandleWithoutFrame(TypeComposition clzEx, String sMessage) {
        // Java Throwable translation has a container owner but no live XTC frame. The null frame
        // here only preserves the legacy empty stack trace; ownership comes from clzEx.
        ExceptionHandle hException = makeMutableStruct(null, clzEx, null);

        hException.setField(null, "text", sMessage == null
                ? xNullable.makeHandle(clzEx.getContainer())
                : xString.makeHandle(clzEx.getContainer(), sMessage));
        hException.setField(null, "cause", xNullable.makeHandle(clzEx.getContainer()));
        hException.makeImmutable();

        return (ExceptionHandle) hException.ensureAccess(Access.PUBLIC);
    }

    private static ExceptionHandle makeMutableStruct(Frame frame, TypeComposition clxEx, String sRTError) {
        clxEx = clxEx.ensureAccess(Access.STRUCT);

        ExceptionHandle hException = new ExceptionHandle(clxEx, sRTError);

        hException.setField(frame, "stackTrace", xString.makeHandle(
                clxEx.getContainer(), frame == null ? "" : frame.getStackTrace()));

        return hException;
    }

    /**
     * @return immutable owner-scoped metadata for the canonical Exception template
     */
    private ExceptionInfo info() {
        return f_info.get();
    }

    private static ExceptionInfo info(Frame frame) {
        return NativeTemplates.get(frame).exception().info();
    }

    private ExceptionInfo createExceptionInfo() {
        // All of these compositions are container-owned. Keeping them together prevents the old
        // split-static failure mode where one exception class came from container A and another from
        // container B while both were being initialized.
        ClassComposition clzException                  = getCanonicalClass();
        ClassComposition clzDeadlock                   = f_container.getTemplate("Deadlock"                     ).getCanonicalClass();
        ClassComposition clzIllegalArgument            = f_container.getTemplate("IllegalArgument"              ).getCanonicalClass();
        ClassComposition clzIllegalState               = f_container.getTemplate("IllegalState"                 ).getCanonicalClass();
        ClassComposition clzInvalidType                = f_container.getTemplate("reflect.InvalidType"           ).getCanonicalClass();
        ClassComposition clzNotImplemented             = f_container.getTemplate("NotImplemented"               ).getCanonicalClass();
        ClassComposition clzOutOfBounds                = f_container.getTemplate("OutOfBounds"                  ).getCanonicalClass();
        ClassComposition clzOutOfMemory                = f_container.getTemplate("OutOfMemory"                  ).getCanonicalClass();
        ClassComposition clzReadOnly                   = f_container.getTemplate("ReadOnly"                     ).getCanonicalClass();
        ClassComposition clzSizeLimited                = f_container.getTemplate("collections.SizeLimited"      ).getCanonicalClass();
        ClassComposition clzStackOverflow              = f_container.getTemplate("StackOverflow"                ).getCanonicalClass();
        ClassComposition clzTimedOut                   = f_container.getTemplate("TimedOut"                     ).getCanonicalClass();
        ClassComposition clzTypeMismatch               = f_container.getTemplate("TypeMismatch"                 ).getCanonicalClass();
        ClassComposition clzUnsupported                = f_container.getTemplate("Unsupported"                  ).getCanonicalClass();
        ClassComposition clzDivisionByZero             = f_container.getTemplate("numbers.Number.DivisionByZero").getCanonicalClass();
        ClassComposition clzPathException              = f_container.getTemplate("fs.PathException"             ).getCanonicalClass();
        ClassComposition clzFileNotFoundException      = f_container.getTemplate("fs.FileNotFound"              ).getCanonicalClass();
        ClassComposition clzAccessDeniedException      = f_container.getTemplate("fs.AccessDenied"              ).getCanonicalClass();
        ClassComposition clzFileAlreadyExistsException = f_container.getTemplate("fs.FileAlreadyExists"         ).getCanonicalClass();
        ClassComposition clzIOException                = f_container.getTemplate("io.IOException"               ).getCanonicalClass();
        ClassComposition clzIOIllegalUTF               = f_container.getTemplate("io.IllegalUTF"                ).getCanonicalClass();
        MethodStructure  methodFormatException         = getStructure().findMethod("formatExceptionString", 2);

        return new ExceptionInfo(clzDeadlock, clzException, clzIllegalArgument, clzIllegalState,
                clzInvalidType, clzNotImplemented, clzOutOfBounds, clzOutOfMemory, clzReadOnly,
                clzSizeLimited, clzStackOverflow, clzTimedOut, clzTypeMismatch, clzUnsupported,
                clzDivisionByZero, clzPathException, clzFileNotFoundException,
                clzAccessDeniedException, clzFileAlreadyExistsException, clzIOException,
                clzIOIllegalUTF, methodFormatException);
    }


    // ----- fields --------------------------------------------------------------------------------

    private record ExceptionInfo(ClassComposition clzDeadlock,
                                 ClassComposition clzException,
                                 ClassComposition clzIllegalArgument,
                                 ClassComposition clzIllegalState,
                                 ClassComposition clzInvalidType,
                                 ClassComposition clzNotImplemented,
                                 ClassComposition clzOutOfBounds,
                                 ClassComposition clzOutOfMemory,
                                 ClassComposition clzReadOnly,
                                 ClassComposition clzSizeLimited,
                                 ClassComposition clzStackOverflow,
                                 ClassComposition clzTimedOut,
                                 ClassComposition clzTypeMismatch,
                                 ClassComposition clzUnsupported,
                                 ClassComposition clzDivisionByZero,
                                 ClassComposition clzPathException,
                                 ClassComposition clzFileNotFoundException,
                                 ClassComposition clzAccessDeniedException,
                                 ClassComposition clzFileAlreadyExistsException,
                                 ClassComposition clzIOException,
                                 ClassComposition clzIOIllegalUTF,
                                 MethodStructure methodFormatException) {}

    /**
     * Owner-scoped equivalent of the old static well-known exception class cache.
     */
    private final Lazy<ExceptionInfo> f_info = Lazy.of(this::createExceptionInfo);
}
