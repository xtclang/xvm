package org.xvm.runtime.template._native.fs;


import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.file.Path;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.Mutability;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.util.Handy;


/**
 * Native OSFile implementation.
 */
public class xOSFile
        extends OSFileNode
    {
    public static xOSFile INSTANCE;

    public xOSFile(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeProperty("contents");

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate    templateFile = f_templates.getTemplate("fs.File");
        ClassComposition clzOSFile    = ensureClass(templateFile.getCanonicalType());

        s_clzOSFileStruct = clzOSFile.ensureAccess(Constants.Access.STRUCT);
        s_constructorFile = getStructure().findConstructor();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName)
            {
            case "contents":
                {
                Path path = hNode.f_path;

                try
                    {
                    byte[] ab = Handy.readFileBytes(path.toFile());
                    return frame.assignValue(iReturn, xByteArray.makeHandle(ab, Mutability.Constant));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode);
                    }
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        NodeHandle hNode = (NodeHandle) hTarget;

        switch (sPropName)
            {
            case "contents":
                try
                    {
                    Path             path = hNode.f_path;
                    FileOutputStream out  = new FileOutputStream(path.toFile());
                    out.write(((xByteArray.ByteArrayHandle) hValue).m_abValue);
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode);
                    }
                return Op.R_NEXT;
            }
        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
        }

    /**
     * Construct a new {@link NodeHandle} representing the specified file.
     *
     * @param frame      the current frame
     * @param hOSStore   the "host" OSStore handle
     * @param path       the node's path
     * @param iReturn    the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, ObjectHandle hOSStore, Path path, int iReturn)
        {
        ClassComposition clzStruct   = s_clzOSFileStruct;
        MethodStructure  constructor = s_constructorFile;

        NodeHandle     hStruct = new NodeHandle(clzStruct, path.toAbsolutePath(), hOSStore);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, constructor.getMaxVars());

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition s_clzOSFileStruct;
    private static MethodStructure  s_constructorFile;
    }
