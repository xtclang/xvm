package org.xvm.runtime.template._native.fs;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO
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

        markNativeMethod("find"            , PATH       , FILENODE );
        markNativeMethod("dirFor"          , PATH       , DIRECTORY);
        markNativeMethod("fileFor"         , PATH       , FILE     );
        markNativeMethod("copy"            , PATHPATH   , FILENODE );
        markNativeMethod("move"            , PATHPATH   , FILENODE );
        markNativeMethod("watch"           , PATHWATCHER, FUNCTION );
        markNativeMethod("watchRecursively", PATHWATCHER, FUNCTION );
        }


    // ----- constants -----------------------------------------------------------------------------

    public static final String[] FUNCTION    = new String[] {"Function"};
    public static final String[] PATH        = new String[] {"fs.Path"};
    public static final String[] FILEWATCHER = new String[] {"fs.FileWatcher"};
    public static final String[] FILENODE    = new String[] {"fs.FileNode"};
    public static final String[] FILE        = new String[] {"fs.File"};
    public static final String[] DIRECTORY   = new String[] {"fs.Directory"};

    public static final String[] PATHPATH    = new String[] {"fs.Path", "fs.Path"};
    public static final String[] PATHWATCHER = new String[] {"fs.Path", "fs.FileWatcher"};


    // ----- data members --------------------------------------------------------------------------
    System
    }
