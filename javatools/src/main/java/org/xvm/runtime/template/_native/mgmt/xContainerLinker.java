package org.xvm.runtime.template._native.mgmt;


import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.FileStructure;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.NestedContainer;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;
import org.xvm.runtime.template._native.reflect.xRTFileTemplate;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native Container functionality.
 */
public class xContainerLinker
        extends xService
    {
    public static xContainerLinker INSTANCE;

    public xContainerLinker(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ClassStructure clz = (ClassStructure)
                pool().ensureEcstasyClassConstant("mgmt.ResourceProvider").getComponent();
        GET_RESOURCE = clz.findMethod("getResource", 2).getIdentityConstant().getSignature();

        markNativeMethod("validate", null, null);
        markNativeMethod("loadFileTemplate", null, null);
        markNativeMethod("resolveAndLink", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().ensureEcstasyTypeConstant("mgmt.Container.Linker");
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "loadFileTemplate":
                {
                try
                    {
                    byte[]        abFile = xByteArray.getBytes((ArrayHandle) hArg);
                    FileStructure struct = new FileStructure(new ByteArrayInputStream(abFile));

                    return frame.assignValue(iReturn, xRTFileTemplate.makeHandle(struct));
                    }
                catch (IOException e)
                    {
                    return frame.raiseException(xException.ioException(frame, e.getMessage()));
                    }

                }

            case "validate":
                {
                try
                    {
                    byte[]        abFile  = xByteArray.getBytes((ArrayHandle) hTarget);
                    FileStructure struct  = new FileStructure(new ByteArrayInputStream(abFile));
                    String        sModule = struct.getModuleName();

                    return frame.assignValue(iReturn, xString.makeHandle(sModule));
                    }
                catch (IOException e)
                    {
                    return frame.raiseException(xException.ioException(frame, e.getMessage()));
                    }
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "resolveAndLink":
                {
                ComponentTemplateHandle hTemplate   = (ComponentTemplateHandle) ahArg[0];
                ObjectHandle            hModel      = ahArg[1]; // mgmt.Container.Model
                ObjectHandle            hRepo       = ahArg[2]; // mgmt.ModuleRepository
                ObjectHandle            hProvider   = ahArg[3]; // mgmt.ResourceProvider
                ObjectHandle            hShared     = ahArg[4];
                ObjectHandle            hAdditional = ahArg[5];
                ObjectHandle            hConditions = ahArg[6];

                Container       container = frame.f_context.f_container;
                ModuleStructure moduleApp = (ModuleStructure) hTemplate.getComponent();
                if (!moduleApp.getFileStructure().isLinked())
                    {
                    FileStructure fileApp = container.createFileStructure(moduleApp);

                    // TODO GG: this needs to be replaced with linking to the passed in repo
                    String sMissing = fileApp.linkModules(container.getModuleRepository(), true);
                    if (sMissing != null)
                        {
                        return frame.raiseException("Unable to load module \"" + sMissing + "\"");
                        }
                    moduleApp = fileApp.getModule(moduleApp.getName());
                    }

                if (!hProvider.isService())
                    {
                    return frame.raiseException("ResourceProvider must be a service");
                    }

                NestedContainer containerNested = new NestedContainer(
                        container, frame.f_context, moduleApp.getIdentityConstant());

                return new CollectResources(containerNested, hProvider, iReturn).doNext(frame);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Injection support.
     */
    public ObjectHandle ensureLinker(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hLinker = m_hLinker;
        if (hLinker == null)
            {
            m_hLinker = hLinker = createServiceHandle(
                    frame.f_context.f_container.createServiceContext("Linker"),
                        getCanonicalClass(), getCanonicalType());
            }

        return hLinker;
        }

    public static class CollectResources
                implements Frame.Continuation
        {
        public CollectResources(NestedContainer container, ObjectHandle hProvider, int iReturn)
            {
            this.container = container;
            this.aKeys     = container.collectInjections().toArray(InjectionKey.NO_INJECTIONS);
            this.hProvider = hProvider;
            this.iReturn   = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            // the resource supplier for the current key is on the frame's stack
            ServiceHandle hService  = this.hProvider.getService();
            ObjectHandle  hSupplier = frameCaller.popStack();

            container.addResourceSupplier(aKeys[index], hService, hSupplier);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < aKeys.length)
                {
                InjectionKey key   = aKeys[index];
                TypeHandle   hType = key.f_type.ensureTypeHandle(frameCaller.f_context.f_container);
                StringHandle hName = xString.makeHandle(key.f_sName);
                CallChain    chain = hProvider.getComposition().getMethodCallChain(GET_RESOURCE);

                ObjectHandle[] ahArg = new ObjectHandle[chain.getMaxVars()];
                ahArg[0] = hType;
                ahArg[1] = hName;

                switch (hProvider.getTemplate().
                            invoke1(frameCaller, chain, hProvider, ahArg, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            return frameCaller.assignValue(iReturn,
                xContainerControl.INSTANCE.makeHandle(container));
            }

        private final NestedContainer container;
        private final InjectionKey[]  aKeys;
        private final ObjectHandle    hProvider;
        private final int             iReturn;

        private int index = -1;
        }

    static SignatureConstant GET_RESOURCE;

    /**
     * Cached Linker handle.
     */
    private ObjectHandle m_hLinker;
    }