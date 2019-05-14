package org.xvm.runtime.template._native.fs;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * Native OSStorage implementation.
 */
public class xOSStorage
        extends xService
    {
    public xOSStorage(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("homeDir");
        markNativeProperty("curDir");
        markNativeProperty("tmpDir");

        markNativeMethod("find", STRING, null);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        switch (sPropName)
            {
            case "homeDir":
                // REVIEW: should we cache those handles?
                return OSFileNode.createHandle(frame, hStorage,
                    Paths.get(System.getProperty("user.home")), true, iReturn);

            case "curDir":
                return OSFileNode.createHandle(frame, hStorage,
                    Paths.get(System.getProperty("user.dir")), true, iReturn);

            case "tmpDir":
                return OSFileNode.createHandle(frame, hStorage,
                    Paths.get(System.getProperty("java.io.tmpdir")), true, iReturn);
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        switch (method.getName())
            {

            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.m_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xFunction.makeAsyncNativeHandle(method).callN(frame, hTarget, ahArg, aiReturn);
            }

        switch (method.getName())
            {
            case "find":  // ahArg[0] == pathString
                {
                StringHandle hPathString = (StringHandle) ahArg[0];

                Path path = Paths.get(hPathString.getStringValue());

                if (Files.exists(path))
                    {
                    switch (OSFileNode.createHandle(frame, hStorage, path, Files.isDirectory(path), Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return frame.assignValues(aiReturn, xBoolean.TRUE, frame.popStack());

                        case Op.R_CALL:
                            frame.m_frameNext.setContinuation(frameCaller ->
                                frameCaller.assignValues(aiReturn, xBoolean.TRUE, frame.popStack()));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }



    // ----- constants -----------------------------------------------------------------------------



    // ----- data members --------------------------------------------------------------------------

    }
