package org.xvm.runtime.template._native.mgmt;


import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;
import org.xvm.runtime.template._native.reflect.xRTFileTemplate;
import org.xvm.runtime.template._native.reflect.xRTType;
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

        markNativeMethod("collectInjectionsImpl", null, null);
        markNativeMethod("loadFileTemplate", BYTES, null);
        markNativeMethod("resolveAndLink", null, null);

        invalidateTypeInfo();
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
                    ArrayHandle   hContents  = (ArrayHandle) hArg;
                    byte[]        abContents = xRTUInt8Delegate.getBytes((ByteArrayHandle) hContents.m_hDelegate);
                    FileStructure struct = new FileStructure(new ByteArrayInputStream(abContents));

                    return frame.assignValue(iReturn, xRTFileTemplate.makeHandle(frame.f_context.f_container, struct));
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
                return invokeResolveAndLink(frame, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "collectInjectionsImpl":
                return invokeCollectInjections(frame, ahArg, aiReturn);
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

        /**
     * Native implementation of <code><pre>
     *   (String[], Type[]) collectInjectionsImpl(
     *      ModuleTemplate template,
     *      String[]       definedNames = [])
     * </pre></code>
     */
    private int invokeCollectInjections(Frame frame, ObjectHandle[] ahArg, int[] aiReturn)
        {
        ComponentTemplateHandle hModule    = (ComponentTemplateHandle) ahArg[0];
        ObjectHandle            hCondNames = ahArg[1];

        ModuleStructure   module        = (ModuleStructure) hModule.getComponent();
        Set<InjectionKey> setInjections = new HashSet<>();
        module.getFileStructure().visitChildren(
            component -> component.collectInjections(setInjections), false, true);

        Container      container = frame.f_context.f_container;
        int            cInjects  = setInjections.size();
        StringHandle[] ahName    = new StringHandle[cInjects];
        TypeHandle[]   ahType    = new TypeHandle[cInjects];
        int            ix        = 0;
        for (InjectionKey key : setInjections)
            {
            ahName[ix  ] = xString.makeHandle(key.f_sName);
            ahType[ix++] = key.f_type.ensureTypeHandle(container);
            }
        ArrayHandle haNames = xArray.makeStringArrayHandle(ahName);
        ArrayHandle haTypes = xArray.makeArrayHandle(xRTType.ensureTypeArrayComposition(container),
                                    cInjects, ahType, xArray.Mutability.Constant);
        return frame.assignValues(aiReturn, haNames, haTypes);
        }

    /**
     * Native implementation of <code><pre>
     *   (TypeSystem, Control) resolveAndLink(
     *      ModuleTemplate    primaryModule, Model             model,
     *      ModuleRepository? repository,    ResourceProvider? provider,
     *      Module[]          sharedModules, ModuleTemplate[]  additionalModules,
     *      String[]          definedNames)
     * </pre></code>
     */
    private int invokeResolveAndLink(Frame frame, ObjectHandle[] ahArg, int iReturn)
        {
        ComponentTemplateHandle hModule     = (ComponentTemplateHandle) ahArg[0];
        ObjectHandle            hModel      = ahArg[1]; // mgmt.Container.Model
        ObjectHandle            hRepo       = ahArg[2]; // mgmt.ModuleRepository
        ObjectHandle            hProvider   = ahArg[3]; // mgmt.ResourceProvider
        ObjectHandle            hShared     = ahArg[4];
        ObjectHandle            hAdditional = ahArg[5];
        ObjectHandle            hCondNames  = ahArg[6];

        if (!hProvider.isService())
            {
            return frame.raiseException("ResourceProvider must be a service");
            }

        Container     container = frame.f_fiber.getCallingContainer();
        FileStructure file      = hModule.getComponent().getFileStructure();

        switch (xRTFileTemplate.INSTANCE.invokeResolve(frame, file, hRepo, Op.A_STACK))
            {
            case Op.R_NEXT:
                return completeResolveAndLink(frame, container, popModule(frame),
                        hProvider, iReturn);

            case Op.R_CALL:
                Frame.Continuation stepNext = frameCaller ->
                    completeResolveAndLink(frameCaller, container, popModule(frameCaller),
                        hProvider, iReturn);
                frame.m_frameNext.addContinuation(stepNext);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    private ModuleStructure popModule(Frame frame)
        {
        ComponentTemplateHandle hFile = (ComponentTemplateHandle) frame.popStack();
        return ((FileStructure) hFile.getComponent()).getModule();
        }

    private int completeResolveAndLink(Frame frame, Container container,
                                       ModuleStructure moduleApp, ObjectHandle hProvider, int iReturn)
        {
        NestedContainer containerNested = new NestedContainer(container,
                moduleApp.getIdentityConstant(), Collections.emptyList());
        return new CollectResources(containerNested, hProvider, iReturn).doNext(frame);
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
                    f_container.createServiceContext("Linker"),
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
            ServiceHandle hService  = hProvider.getService();
            ObjectHandle  hSupplier = frameCaller.popStack();

            container.addResourceSupplier(aKeys[index], hService, hSupplier);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < aKeys.length)
                {
                InjectionKey key   = aKeys[index];
                TypeHandle   hType = key.f_type.ensureTypeHandle(container);
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