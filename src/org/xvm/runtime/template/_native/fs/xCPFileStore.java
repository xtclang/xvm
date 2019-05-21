package org.xvm.runtime.template._native.fs;


import java.io.File;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ConstantHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native OSFileStore implementation.
 */
public class xCPFileStore
        extends ClassTemplate
    {
    public xCPFileStore(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        }

    @Override
    public void initDeclared()
        {
        s_clz         = ensureClass(getCanonicalType(), pool().typeFileStore());
        s_clzStruct   = s_clz.ensureAccess(Access.STRUCT);
        s_constructor = f_struct.findConstructor(pool().typeObject());

        markNativeMethod("loadNode"     , null, null);
        markNativeMethod("loadDirectory", null, null);
        markNativeMethod("loadFile"     , null, null);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof FileStoreConstant)
            {
            FileStoreConstant constStore = (FileStoreConstant) constant;
            FSNodeConstant    constRoot  = constStore.getValue();

            GenericHandle hStruct = new GenericHandle(s_clzStruct);
            return callConstructor(frame, s_constructor, s_clz.ensureAutoInitializer(), hStruct,
                    new ObjectHandle[] {new ConstantHandle(constRoot)}, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ObjectHandle hStore = hTarget;

        switch (method.getName())
            {
            // protected immutable Byte[] loadFile(Object constNode);
            case "loadFile":
                {
                // TODO
                int x = 0;
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
            ObjectHandle[] ahArg, int[] aiReturn)
        {
        ObjectHandle hStore = hTarget;

        switch (method.getName())
            {
            // protected (Boolean isdir, String name, UInt128 created, UInt128 modified, Int size) loadNode(Object constNode)
            case "loadNode":
                {
                // TODO
                int x = 0;
                }

            // protected (String[] names, Object[] cookies) loadDirectory(Object constNode);
            case "loadDirectory":
                {
                // TODO
                int x = 0;
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected boolean isConstructImmutable()
        {
        return true;
        }

    // ----- constants -----------------------------------------------------------------------------

    private static final File ROOT = new File("/");

    static private ClassComposition s_clz;
    static private ClassComposition s_clzStruct;
    static private MethodStructure  s_constructor;

    // ----- data members --------------------------------------------------------------------------
    }
