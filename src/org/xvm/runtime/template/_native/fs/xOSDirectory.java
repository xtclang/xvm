package org.xvm.runtime.template._native.fs;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native OSDirectory implementation.
 */
public class xOSDirectory
        extends OSFileNode
    {
    public static xOSDirectory INSTANCE;

    public xOSDirectory(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName)
            {
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }
    }
