package org.xvm.runtime.template._native.fs;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ConstantHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native OSFileStore implementation.
 */
public class xCPFileStore
        extends xConst
    {
    public xCPFileStore(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        s_clz         = ensureClass(getCanonicalType(), pool().typeFileStore());
        s_clzStruct   = s_clz.ensureAccess(Access.STRUCT);
        s_constructor = getStructure().findConstructor(pool().typeString(), pool().typeObject());

        markNativeMethod("loadNode"     , null, null);
        markNativeMethod("loadDirectory", null, null);
        markNativeMethod("loadFile"     , null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof FileStoreConstant constStore)
            {
            GenericHandle  hStruct = new GenericHandle(s_clzStruct);
            ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, s_constructor.getMaxVars());
            ahVar[0] = xString.makeHandle(constStore.getPath());
            ahVar[1] = new ConstantHandle(constStore.getValue());

            return proceedConstruction(frame, s_constructor, true, hStruct, ahVar, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            // protected immutable Byte[] loadFile(Object constNode);
            case "loadFile":
                {
                ConstantHandle hNode     = (ConstantHandle) hArg;
                FSNodeConstant constNode = (FSNodeConstant) hNode.getConstant();
                ObjectHandle   hBinary   = xArray.makeByteArrayHandle(
                        constNode.getFileBytes(), Mutability.Constant);
                return frame.assignValue(iReturn, hBinary);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
            ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            // protected (Boolean isDir, String name, String created, String modified, Int size) loadNode(Object constNode)
            case "loadNode":
                {
                ConstantHandle hNode     = (ConstantHandle) ahArg[0];
                FSNodeConstant constNode = (FSNodeConstant) hNode.getConstant();

                ObjectHandle[] ahValue = new ObjectHandle[5];
                ahValue[0] = xBoolean.makeHandle(constNode.getFormat() == Format.FSDir);
                ahValue[1] = xString.makeHandle(constNode.getName());
                ahValue[2] = frame.getConstHandle(constNode.getCreatedConstant());
                ahValue[3] = frame.getConstHandle(constNode.getModifiedConstant());
                ahValue[4] = xInt64.makeHandle(calcSize(constNode));
                return new Utils.AssignValues(aiReturn, ahValue).proceed(frame);
                }

            // protected (String[] names, Object[] cookies) loadDirectory(Object constNode);
            case "loadDirectory":
                {
                ConstantHandle hNode     = (ConstantHandle) ahArg[0];
                FSNodeConstant constNode = (FSNodeConstant) hNode.getConstant();

                FSNodeConstant[] aNodes    = constNode.getDirectoryContents();
                int              cNodes    = aNodes.length;
                StringHandle[]   ahNames   = new StringHandle[cNodes];
                ObjectHandle[]   ahCookies = new ObjectHandle[cNodes];
                for (int i = 0; i < cNodes; ++i)
                    {
                    FSNodeConstant constEach = aNodes[i];
                    ahNames  [i] = xString.makeHandle(constEach.getName());
                    ahCookies[i] = new ConstantHandle(constEach);
                    }

                return frame.assignValues(aiReturn,
                        xArray.makeStringArrayHandle(ahNames),
                        xArray.makeObjectArrayHandle(ahCookies, Mutability.Constant));
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- helpers -------------------------------------------------------------------------------

    static long calcSize(FSNodeConstant node)
        {
        switch (node.getFormat())
            {
            case FSDir:
                long lSum = 0;
                for (FSNodeConstant nodeSub : node.getDirectoryContents())
                    {
                    lSum += calcSize(nodeSub);
                    }
                return lSum;

            case FSFile:
                return node.getFileBytes().length;

            case FSLink:
                return calcSize(node.getLinkTarget());

            default:
                throw new IllegalStateException();
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition s_clz;
    private static TypeComposition s_clzStruct;
    private static MethodStructure s_constructor;
    }
