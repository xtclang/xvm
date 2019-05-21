package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xNullable;
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

        markNativeMethod("find", new String[] {"_native.fs.OSFileStore", "String"}, null);
        markNativeMethod("names", STRING, null);
        markNativeMethod("createDir", STRING, BOOLEAN);
        markNativeMethod("createFile", STRING, BOOLEAN);
        markNativeMethod("delete", STRING, BOOLEAN);
        }

    @Override
    protected ExceptionHandle makeImmutable(ObjectHandle hTarget)
        {
        return null;
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;
        ObjectHandle  hStore   = hStorage.getField("fileStore");

        switch (sPropName)
            {
            case "homeDir":
                // REVIEW: should we cache those handles?
                return OSFileNode.createHandle(frame, hStore,
                    Paths.get(System.getProperty("user.home")), true, iReturn);

            case "curDir":
                return OSFileNode.createHandle(frame, hStore,
                    Paths.get(System.getProperty("user.dir")), true, iReturn);

            case "tmpDir":
                return OSFileNode.createHandle(frame, hStore,
                    Paths.get(System.getProperty("java.io.tmpdir")), true, iReturn);
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.m_context)
            {
            return xFunction.makeAsyncNativeHandle(method).
                call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
            }

        switch (method.getName())
            {
            case "names":
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path     path   = Paths.get(hPathString.getStringValue());
                String[] asName = path.toFile().list();
                int      cNames = asName == null ? 0 : asName.length;

                StringHandle[] ahName = new StringHandle[cNames];
                int i = 0;
                for (String sName : asName)
                    {
                    ahName[i++] = xString.makeHandle(sName);
                    }

                return frame.assignValue(iReturn, xArray.makeStringArrayHandle(ahName));
                }

            case "createFile":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path) && !Files.isDirectory(path))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                try
                    {
                    return frame.assignValue(iReturn,
                        xBoolean.makeHandle(path.toFile().createNewFile()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, path.toString());
                    }
                }

            case "createDir":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path) && Files.isDirectory(path))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                try
                    {
                    return frame.assignValue(iReturn,
                        xBoolean.makeHandle(path.toFile().createNewFile()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, path.toString());
                    }
                }

            case "delete":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                if (!Files.exists(path))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(path.toFile().delete()));
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.m_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
            }

        switch (method.getName())
            {
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
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
            case "find":  // (store, pathString)
                {
                ObjectHandle hStore      = ahArg[0];
                StringHandle hPathString = (StringHandle) ahArg[1];

                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path))
                    {
                    return Utils.assignConditionalResult(
                        frame,
                        OSFileNode.createHandle(frame, hStore, path, Files.isDirectory(path), Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- helper methods ------------------------------------------------------------------------

    protected int raisePathException(Frame frame, IOException e, String sPath)
        {
        // TODO: how to get the natural Path efficiently from sPath?
        return frame.raiseException(xException.pathException(e.getMessage(), xNullable.NULL));
        }



    // ----- constants -----------------------------------------------------------------------------



    // ----- data members --------------------------------------------------------------------------

    }
