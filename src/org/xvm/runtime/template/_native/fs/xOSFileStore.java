package org.xvm.runtime.template._native.fs;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xInt64;


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
        markNativeProperty("bytesUsed");
        markNativeProperty("bytesFree");

        final String[] FUNCTION    = new String[] {"Function"};
        final String[] PATH        = new String[] {"fs.Path"};
        final String[] FILENODE    = new String[] {"fs.FileNode"};
        final String[] FILE        = new String[] {"fs.File"};
        final String[] DIRECTORY   = new String[] {"fs.Directory"};
        final String[] PATHPATH    = new String[] {"fs.Path", "fs.Path"};
        final String[] PATHWATCHER = new String[] {"fs.Path", "fs.FileWatcher"};

        markNativeMethod("find"            , PATH       , FILENODE );
        markNativeMethod("dirFor"          , PATH       , DIRECTORY);
        markNativeMethod("fileFor"         , PATH       , FILE     );
        markNativeMethod("copy"            , PATHPATH   , FILENODE );
        markNativeMethod("move"            , PATHPATH   , FILENODE );
        markNativeMethod("watch"           , PATHWATCHER, FUNCTION );
        markNativeMethod("watchRecursively", PATHWATCHER, FUNCTION );
        }

    @Override
    protected int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "capacity":
                return frame.assignValue(iReturn, xInt64.makeHandle(128*1024*1024));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        GenericHandle hStore = (GenericHandle) hTarget;

        switch (method.getName())
            {
            case "find":
                {
                return frame.assignValue(iReturn, hArg);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------



    // ----- data members --------------------------------------------------------------------------

    }
