package org.xvm.runtime.template._native.fs;


import java.io.File;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * Native OSFileStore implementation.
 */
public class xOSFileStore
        extends ClassTemplate
    {
    public xOSFileStore(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("capacity");
        markNativeProperty("bytesFree");
        markNativeProperty("bytesUsed");

        markNativeMethod("dirFor", STRING, null);
        markNativeMethod("fileFor", STRING, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    protected int postValidate(Frame frame, ObjectHandle hStruct)
        {
        return makeImmutable(frame, hStruct);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "capacity":
                return frame.assignValue(iReturn, xInt64.makeHandle(ROOT.getTotalSpace()));

            case "bytesFree":
                return frame.assignValue(iReturn, xInt64.makeHandle(ROOT.getFreeSpace()));

            case "bytesUsed":
                return frame.assignValue(iReturn, xInt64.makeHandle(ROOT.getTotalSpace() - ROOT.getFreeSpace()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ObjectHandle hStore = hTarget;

        switch (method.getName())
            {
            case "dirFor":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                return OSFileNode.createHandle(frame, hStore, path, true, iReturn);
                }

            case "fileFor":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                return OSFileNode.createHandle(frame, hStore, path, false, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static final File ROOT = new File("/");
    }
