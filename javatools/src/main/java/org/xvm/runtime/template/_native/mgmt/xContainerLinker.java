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
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.NestedContainer;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum.EnumHandle;
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

import org.xvm.util.Lazy;


/**
 * Native Container functionality.
 */
public class xContainerLinker
        extends xService {

    public static xContainerLinker getInstance(Frame frame) {
        return NativeTemplates.get(frame).containerLinker();
    }

    public static xContainerLinker getInstance(Container container) {
        return NativeTemplates.get(container).containerLinker();
    }

    public xContainerLinker(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        f_sigGetResource.get(this);

        markNativeMethod("collectInjectionsImpl", null, null);
        markNativeMethod("loadFileTemplate", BYTES, null);
        markNativeMethod("resolveAndLink", null, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("mgmt.Container.Linker");
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureLinker(Frame frame, ObjectHandle hOpts) {
        return f_hLinker.get(this);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "loadFileTemplate":
            try {
                ArrayHandle   hContents  = (ArrayHandle) hArg;
                byte[]        abContents = xRTUInt8Delegate.getBytes((ByteArrayHandle) hContents.getDelegate());
                FileStructure struct = new FileStructure(new ByteArrayInputStream(abContents));

                return frame.assignValue(iReturn, xRTFileTemplate.makeHandle(frame.f_context.f_container, struct));
            } catch (IOException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "resolveAndLink":
            return invokeResolveAndLink(frame, ahArg, iReturn);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
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
    private int invokeCollectInjections(Frame frame, ObjectHandle[] ahArg, int[] aiReturn) {
        ComponentTemplateHandle hModule     = (ComponentTemplateHandle) ahArg[0];
        ArrayHandle             haCondNames = (ArrayHandle) ahArg[1];

        if (haCondNames.getDelegate().m_cSize > 0) {
            return frame.raiseException(
                xException.unsupported(frame, "Condition names are not currently supported"));
        }

        ModuleStructure   module        = (ModuleStructure) hModule.getComponent();
        Set<InjectionKey> setInjections = new HashSet<>();
        module.getFileStructure().visitChildren(
            component -> component.collectInjections(setInjections), false, true);

        Container      container = frame.f_context.f_container;
        int            cInjects  = setInjections.size();
        StringHandle[] ahName    = new StringHandle[cInjects];
        TypeHandle[]   ahType    = new TypeHandle[cInjects];
        int            ix        = 0;
        for (InjectionKey key : setInjections) {
            ahName[ix  ] = xString.makeHandle(container, key.f_sName);
            ahType[ix++] = key.f_type.ensureTypeHandle(container);
        }
        ArrayHandle haNames = xArray.makeStringArrayHandle(container, ahName);
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
    private int invokeResolveAndLink(Frame frame, ObjectHandle[] ahArg, int iReturn) {
        ComponentTemplateHandle hModule      = (ComponentTemplateHandle) ahArg[0];
        ObjectHandle            hModel       = ahArg[1]; // mgmt.Container.Model
        ObjectHandle            hRepo        = ahArg[2]; // mgmt.ModuleRepository
        ObjectHandle            hProvider    = ahArg[3]; // mgmt.ResourceProvider
        ArrayHandle             haShared     = (ArrayHandle) ahArg[4];
        ArrayHandle             haAdditional = (ArrayHandle) ahArg[5];
        ArrayHandle haCondNames = (ArrayHandle) ahArg[6];

        if (((EnumHandle) hModel).getOrdinal() != 0) {
            return frame.raiseException(
                xException.unsupported(frame, "Only Lightweight model is currently supported"));
        }
        if (!hProvider.isService()) {
            return frame.raiseException(
                xException.illegalArgument(frame, "ResourceProvider must be a service"));
        }
        if (haCondNames.getDelegate().m_cSize > 0) {
            return frame.raiseException(
                xException.unsupported(frame, "Condition names are not currently supported"));
        }

        Container      container = frame.f_fiber.getCallingContainer();
        FileStructure  file      = hModule.getComponent().getFileStructure();
        ObjectHandle[] ahShared;
        ObjectHandle[] ahAdditional;
        try {
            ahShared = haShared.getDelegate().m_cSize == 0
                                ? Utils.OBJECTS_NONE
                                : haShared.getTemplate().toArray(frame, haShared);
            ahAdditional = haAdditional.getDelegate().m_cSize == 0
                                ? Utils.OBJECTS_NONE
                                : haAdditional.getTemplate().toArray(frame, haAdditional);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }

        xRTFileTemplate templateFile = frame.container().getTemplate("_native.reflect.RTFileTemplate",
                xRTFileTemplate.class);
        switch (templateFile.invokeResolve(frame, file, hRepo,
                    ahShared, ahAdditional, Op.A_STACK)) {
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


    // ----- helpers -------------------------------------------------------------------------------

    private ModuleStructure popModule(Frame frame) {
        ComponentTemplateHandle hFile = (ComponentTemplateHandle) frame.popStack();
        return ((FileStructure) hFile.getComponent()).getModule();
    }

    private int completeResolveAndLink(Frame frame, Container container,
                                       ModuleStructure moduleApp, ObjectHandle hProvider, int iReturn) {
        NestedContainer containerNested = NestedContainer.create(container,
                moduleApp.getIdentityConstant(), hProvider, Collections.emptyList());
        return new CollectResources(containerNested, iReturn).doNext(frame);
    }

    private SignatureConstant getResourceSignature() {
        return f_sigGetResource.get(this);
    }

    public static class CollectResources
                implements Frame.Continuation {
        public CollectResources(NestedContainer container, int iReturn) {
            this.container = container;
            this.aKeys     = container.collectInjections().toArray(InjectionKey.NO_INJECTIONS);
            this.hProvider = container.f_hProvider;
            this.iReturn   = iReturn;
        }

        @Override
        public int proceed(Frame frameCaller) {
            updateResult(frameCaller);

            return doNext(frameCaller);
        }

        protected void updateResult(Frame frameCaller) {
            // the resource supplier for the current key is on the frame's stack
            ServiceHandle hService  = hProvider.getService();
            ObjectHandle  hSupplier = frameCaller.popStack();

            container.addResourceSupplier(aKeys[index], hService, hSupplier);
        }

        public int doNext(Frame frameCaller) {
            while (++index < aKeys.length) {
                InjectionKey key   = aKeys[index];
                TypeHandle   hType = key.f_type.ensureTypeHandle(container);
                StringHandle hName = xString.makeHandle(container, key.f_sName);
                CallChain    chain = hProvider.getComposition().getMethodCallChain(
                        xContainerLinker.getInstance(frameCaller).getResourceSignature());

                ObjectHandle[] ahArg = new ObjectHandle[chain.getMaxVars()];
                ahArg[0] = hType;
                ahArg[1] = hName;

                switch (hProvider.getTemplate().
                            invoke1(frameCaller, chain, hProvider, ahArg, Op.A_STACK)) {
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
                xContainerControl.getInstance(frameCaller).makeHandle(container));
        }

        private final NestedContainer container;
        private final InjectionKey[]  aKeys;
        private final ObjectHandle    hProvider;
        private final int             iReturn;

        private int index = -1;
    }

    /**
     * Lazily resolved ResourceProvider.getResource signature owned by this template's container.
     */
    private final Lazy.Owner<xContainerLinker, SignatureConstant> f_sigGetResource = Lazy.ofOwner(owner -> {
        ClassStructure clz = (ClassStructure)
                owner.pool().ensureEcstasyClassConstant("mgmt.ResourceProvider").getComponent();
        return clz.findMethod("getResource", 2).getIdentityConstant().getSignature();
    });

    /**
     * Cached Linker handle.
     */
    private final Lazy.Owner<xContainerLinker, ObjectHandle> f_hLinker = Lazy.ofOwner(owner ->
            owner.createServiceHandle(owner.container().createServiceContext("Linker"),
                    owner.getCanonicalClass(), owner.getCanonicalType()));
}
