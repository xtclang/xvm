package org.xvm.runtime.template._native.mgmt;


import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.FileStructure;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SignatureConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.SimpleContainer;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;
import org.xvm.runtime.template._native.reflect.xRTFileTemplate;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import org.xvm.runtime.template.collections.xByteArray.ByteArrayHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native Container functionality.
 */
public class xContainerLinker
        extends xService
    {
    public xContainerLinker(TemplateRegistry registry, ClassStructure structure, boolean fInstance)
        {
        super(registry, structure, false);
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
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "loadFileTemplate":
                {
                ByteArrayHandle hBytes = (ByteArrayHandle) hArg;
                try
                    {
                    byte[]        abFile  = hBytes.m_abValue;
                    FileStructure struct  = new FileStructure(new ByteArrayInputStream(abFile));

                    return frame.assignValue(iReturn, xRTFileTemplate.makeHandle(struct));
                    }
                catch (IOException e)
                    {
                    return frame.raiseException(xException.ioException(frame, e.getMessage()));
                    }

                }

            case "validate":
                {
                ByteArrayHandle hBytes = (ByteArrayHandle) hArg;
                try
                    {
                    byte[]        abFile  = hBytes.m_abValue;
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
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "resolveAndLink":
                {
                ComponentTemplateHandle hTemplate   = (ComponentTemplateHandle) ahArg[0];
                ObjectHandle            hModel      = ahArg[1];
                ObjectHandle            hRepo       = ahArg[2];
                ObjectHandle            hInjector   = ahArg[3];
                ArrayHandle             hShared     = (ArrayHandle) ahArg[4];
                ArrayHandle             hAdditional = (ArrayHandle) ahArg[5];
                ArrayHandle             hConditions = (ArrayHandle) ahArg[6];

                ModuleStructure moduleApp = (ModuleStructure) hTemplate.getComponent();
                FileStructure   structApp = f_templates.createFileStructure(moduleApp);

                // TODO GG: this needs to be replaced with linking to the passed in repo
                String sMissing = structApp.linkModules(f_templates.f_repository, true);
                if (sMissing != null)
                    {
                    return frame.raiseException("Unable to load module \"" + sMissing + "\"");
                    }

                ModuleConstant idModule = (ModuleConstant)
                        structApp.getChild(moduleApp.getName()).getIdentityConstant();

                SimpleContainer container = new SimpleContainer(frame.f_context, idModule);

                return new CollectResources(container, hInjector, aiReturn).doNext(frame);
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    public static class CollectResources
                implements Frame.Continuation
        {
        public CollectResources(SimpleContainer container, ObjectHandle hProvider, int[] aiReturn)
            {
            this.container = container;
            this.aKeys     = container.collectInjections().toArray(InjectionKey.NO_INJECTIONS);
            this.hProvider = hProvider;
            this.aiReturn  = aiReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            // the resource for the current key is on the frame's stack
            InjectionKey key       = aKeys[index];
            ObjectHandle hResource = frameCaller.popStack();

            // mask the injection type (and ensure the ownership)
            hResource = hResource.maskAs(frameCaller.f_context.f_container, key.f_type);

            container.addStaticResource(key, hResource);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < aKeys.length)
                {
                InjectionKey key   = aKeys[index];
                TypeHandle   hType = key.f_type.getTypeHandle();
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

            switch (container.ensureTypeSystemHandle(frameCaller, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return frameCaller.assignValues(aiReturn,
                        frameCaller.popStack(), xContainerControl.INSTANCE.makeHandle(container));

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(frameCaller1 ->
                        frameCaller1.assignValues(aiReturn,
                            frameCaller1.popStack(), xContainerControl.INSTANCE.makeHandle(container)));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        private final SimpleContainer container;
        private final InjectionKey[]  aKeys;
        private final ObjectHandle    hProvider;
        private final int[] aiReturn;

        private int index = -1;
        }

    static SignatureConstant GET_RESOURCE;
    }
