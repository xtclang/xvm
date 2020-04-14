package org.xvm.runtime.template._native.mgmt;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.SimpleContainer;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import org.xvm.runtime.template.collections.xByteArray.ByteArrayHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * Native Container functionality.
 */
public class xLinker
        extends xService
    {
    public static xLinker INSTANCE;

    public xLinker(TemplateRegistry registry, ClassStructure structure, boolean fInstance)
        {
        super(registry, structure, false);

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
        markNativeMethod("resolveAndLink", null, null);

        getCanonicalType().invalidateTypeInfo();
        }


    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
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
                ByteArrayHandle hBytes      = (ByteArrayHandle) ahArg[0];
                ObjectHandle    hRepository = ahArg[1];
                ObjectHandle    hInjector   = ahArg[2];

                try
                    {
                    InputStream     stream    = new ByteArrayInputStream(hBytes.m_abValue);
                    FileStructure   struct    = new FileStructure(stream);
                    ModuleStructure moduleApp = struct.getModule();

                    ServiceContext context    = frame.f_context;
                    FileStructure  structApp  = context.f_templates.createFileStructure(moduleApp);
                    ModuleConstant idModule   = (ModuleConstant)
                            structApp.getChild(moduleApp.getName()).getIdentityConstant();

                    SimpleContainer container = new SimpleContainer(context, idModule);

                    return new GetResources(container, hInjector, aiReturn).doNext(frame);
                    }
                catch (IOException e)
                    {
                    return frame.raiseException(xException.ioException(frame, e.getMessage()));
                    }
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    public static class GetResources
                implements Frame.Continuation
        {
        public GetResources(SimpleContainer container, ObjectHandle hProvider, int[] aiReturn)
            {
            this.container = container;
            this.aKeys     = container.collectInjections().toArray(new InjectionKey[0]);
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
            ObjectHandle hResource = frameCaller.popStack();
            container.addStaticResource(aKeys[index], hResource);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < aKeys.length)
                {
                InjectionKey key   = aKeys[index];
                TypeHandle   hType = key.f_type.getTypeHandle();
                StringHandle hName = xString.makeHandle(key.f_sName);

                ObjectHandle[] ahArg = new ObjectHandle[3];
                ahArg[0] = hType;
                ahArg[1] = hName;

                CallChain chain   = hProvider.getComposition().getMethodCallChain(GET_RESOURCE);
                int       iResult = hProvider.getTemplate().invoke1(
                                        frameCaller, chain, hProvider, ahArg, Op.A_STACK);
                switch (iResult)
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

            return frameCaller.assignValues(aiReturn,
                xNullable.NULL, xAppControl.INSTANCE.makeHandle(container));
            }

        private final SimpleContainer container;
        private final InjectionKey[]  aKeys;
        private final ObjectHandle    hProvider;
        private final int[] aiReturn;

        private int index = -1;
        }

    static SignatureConstant GET_RESOURCE;
    }
