package org.xvm.runtime.template._native.fs;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeType;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native OSFileStore implementation.
 */
public class xOSFileStore
        extends ClassTemplate {
    public xOSFileStore(Container container, ClassStructure structure) {
        super(container, structure);
    }

    /** The receiver type; this template does not narrow it - createHandle takes an ObjectHandle. */
    private static final NativeType<ObjectHandle> SELF =
            NativeType.of("_native.fs.OSFileStore", ObjectHandle.class);

    /** The {@code text.String} parameter type, carrying the handle class that represents it. */
    private static final NativeType<StringHandle> STRING_TYPE =
            NativeType.of("text.String", StringHandle.class);

    @Override
    public void initNative() {
        markNativeProperty("capacity");
        markNativeProperty("bytesFree");
        markNativeProperty("bytesUsed");

        markNativeMethod1("dirFor", SELF, STRING_TYPE, null,
                (frame, hStore, hPath, iReturn) -> nodeFor(frame, hStore, hPath, true, iReturn));
        markNativeMethod1("fileFor", SELF, STRING_TYPE, null,
                (frame, hStore, hPath, iReturn) -> nodeFor(frame, hStore, hPath, false, iReturn));
        markNativeMethod("linkAsFile", STRING, null);
        markNativeMethod("copyOrMove", null, null);

        invalidateTypeInfo();
    }

    @Override
    protected int postValidate(Frame frame, ObjectHandle hStruct) {
        // we need to make the OSFileStore handle immutable, so it can go across the service
        // boundary, but it holds a reference to a OSStorage service handle, so a call to
        //   makeImmutable(frame, hStruct);
        // would result in a natural exception
        // TODO: consider an option for ClassTemplate.makeImmutable() to exclude service handles

        hStruct.makeImmutable();
        return Op.R_NEXT;
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        switch (sPropName) {
        case "capacity":
            return frame.assignValue(iReturn, xInt64.makeHandle(frame, ROOT.getTotalSpace()));

        case "bytesFree":
            return frame.assignValue(iReturn, xInt64.makeHandle(frame, ROOT.getFreeSpace()));

        case "bytesUsed":
            return frame.assignValue(iReturn, xInt64.makeHandle(frame, ROOT.getTotalSpace() - ROOT.getFreeSpace()));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    /**
     * The shared body of the native {@code dirFor} and {@code fileFor}, bound with typed handles.
     *
     * <p>This template is not a service and has no async guard, so with both natives bound its
     * {@code invokeNative1} override held nothing but a dispatch switch and was removed entirely.</p>
     */
    private static int nodeFor(Frame frame, ObjectHandle hStore, StringHandle hPathString,
                               boolean fDir, int iReturn) {
        try {
            Path path = Paths.get(hPathString.getStringValue());
            return xOSFileNode.createHandle(frame, hStore, path, fDir, iReturn);
        } catch (InvalidPathException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "copyOrMove": {
            ObjectHandle hSrc  = ahArg[0];
            String       sSrc  = ((StringHandle) ahArg[1]).getStringValue();
            ObjectHandle hDest = ahArg[2];
            String       sDest = ((StringHandle) ahArg[3]).getStringValue();
            boolean      fMove = ((xBoolean.BooleanHandle) ahArg[4]).get();

            Path pathResult;
            try {
                Path    pathSrc = Paths.get(sSrc);
                boolean fDir    = Files.isDirectory(pathSrc);
                if (Files.notExists(pathSrc)) {
                    return frame.raiseException(xException.fileNotFoundException(
                            frame, "Could not find file or directory: " + sSrc, hSrc));
                }

                Path pathDest = Paths.get(sDest);
                if (Files.exists(pathDest) && !Files.isDirectory(pathDest)) {
                    return frame.raiseException(xException.fileAlreadyExistsException(
                            frame, "Could not overwrite file or directory: " + sDest, hDest));
                }

                pathResult = fMove
                        ? Files.move(pathSrc, pathDest)
                        : Files.copy(pathSrc, pathDest);
                return xOSFileNode.createHandle(frame, hTarget, pathResult, fDir, iReturn);
            } catch (NoSuchFileException | FileNotFoundException e) {
                return frame.raiseException(xException.fileNotFoundException(frame, e.getMessage(), hSrc));
            } catch (FileAlreadyExistsException e) {
                return frame.raiseException(xException.fileAlreadyExistsException(frame, e.getMessage(), hDest));
            } catch (SecurityException | AccessDeniedException e) {
                return frame.raiseException(xException.accessDeniedException(frame, e.getMessage(), hDest));
            } catch (IOException|InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "linkAsFile": { // pathString
            StringHandle hPathString = (StringHandle) ahArg[0];
            try {
                Path path  = Paths.get(hPathString.getStringValue());

                if (Files.isSymbolicLink(path)) {
                    // TODO: implement native support for link files
                    System.err.println("*** File is a link: " + path);
                }
                return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
            } catch (InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }
        }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }


    // ----- constants -----------------------------------------------------------------------------

    private static final File ROOT = new File("/");
}
