package org.xvm.runtime.template._native.fs;


import java.nio.file.Path;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;


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
    public void initNative()
        {
        super.initNative();

        markNativeMethod("deleteRecursively", null, null);
        markNativeMethod("filesRecursively", null, null);   // TODO as a natural implementation?
        markNativeMethod("watchRecursively", null, null);

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate    templateDir = f_templates.getTemplate("fs.Directory");
        ClassComposition clzOSDir    = ensureClass(templateDir.getCanonicalType());

        s_clzOSDirStruct = clzOSDir.ensureAccess(Constants.Access.STRUCT);
        s_constructorDir = getStructure().findConstructor();
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

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        xService.ServiceHandle hStorage = (xService.ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context)
            {
            return xRTFunction.makeAsyncNativeHandle(method).
                call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
            }

        switch (method.getName())
            {
            case "watchRecursively":  // (FileWatcher)
                // TODO GG
                throw new UnsupportedOperationException("watchRecursively");
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        xService.ServiceHandle hStorage = (xService.ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
            }

        switch (method.getName())
            {
            case "deleteRecursively":
                // TODO GG
                throw new UnsupportedOperationException("deleteRecursively");
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Construct a new {@link NodeHandle} representing the specified directory.
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
        ClassComposition clzStruct   = s_clzOSDirStruct;
        MethodStructure  constructor = s_constructorDir;

        NodeHandle     hStruct = new NodeHandle(clzStruct, path.toAbsolutePath(), hOSStore);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, constructor.getMaxVars());

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition s_clzOSDirStruct;
    private static MethodStructure  s_constructorDir;
    }
