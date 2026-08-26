package org.xvm.runtime;


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.InjectionKey;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * A nested container ( > 0).
 */
public class NestedContainer
        extends Container {
    /**
     * Instantiate a nested container.
     *
     * @param containerParent  the parent container
     * @param idModule         the module id
     * @param hProvider        a resource provider supplied by the parent container
     * @param listShared       a list ids for shared modules
     */
    public static NestedContainer create(Container containerParent, ModuleConstant idModule,
                                         ObjectHandle hProvider, List<ModuleConstant> listShared) {
        return containerParent.f_runtime.registerContainer(
                new NestedContainer(containerParent, idModule, hProvider, listShared));
    }

    /**
     * Create a nested container owned by a TRUSTED JAVA HOST (an embedder such as an LSP runner
     * or the tooling engine), rather than by guest code with a sandbox {@code ResourceProvider}.
     * A host container has no guest provider ({@code f_hProvider == null}) and therefore falls
     * back to its parent (the native plane) for standard injections it does not explicitly
     * override with {@link #registerHostResource} - exactly the trusted-kernel model the
     * platform host uses, expressed from Java. Guest nested containers keep the strict
     * sandbox (no parent fallback); the two are distinguished by whether a provider is present.
     *
     * @param containerParent  the parent container (e.g. the NativeContainer plane)
     * @param idModule         the module to run
     * @param listShared       shared module ids
     *
     * @return the host-owned nested container
     */
    public static NestedContainer createForHost(Container containerParent, ModuleConstant idModule,
                                                List<ModuleConstant> listShared) {
        return containerParent.f_runtime.registerContainer(
                new NestedContainer(containerParent, idModule, null, listShared));
    }

    /**
     * Register a Java-supplied injection for a host container (e.g. a capturing Console). Takes
     * precedence over the native-plane fallback.
     */
    public void registerHostResource(InjectionKey key, InjectionSupplier supplier) {
        if (f_hProvider != null) {
            throw new IllegalStateException("cannot register host resources on a guest container");
        }
        f_mapResources.put(key, supplier);
    }

    private NestedContainer(Container containerParent, ModuleConstant idModule,
                            ObjectHandle hProvider, List<ModuleConstant> listShared) {
        super(containerParent.f_runtime, containerParent, idModule);

        f_hProvider  = hProvider;
        f_listShared = listShared;
    }


    // ----- NestedContainer API -------------------------------------------------------------------

    /**
     * @return a set of injections for this container's module
     */
    public Set<InjectionKey> collectInjections() {
        ModuleStructure module = (ModuleStructure) getModule().getComponent();

        Set<InjectionKey> setInjections = new HashSet<>();
        module.getFileStructure().visitChildren(
            component -> component.collectInjections(setInjections), false, true);
        return setInjections;
    }

    /**
     * Add a natural resource supplier for an injection.
     *
     * @param key        the injection key
     * @param hService   the resource provider's service
     * @param hSupplier  the resource supplier handle (the resource itself or a function)
     */
    public void addResourceSupplier(InjectionKey key, ServiceHandle hService, ObjectHandle hSupplier) {
        if (hSupplier instanceof FunctionHandle hFunction) {
            FunctionHandle hProxy = xRTFunction.makeAsyncDelegatingHandle(hService, hFunction);
            f_mapResources.put(key, (frame, hOpts) -> {
                ObjectHandle[] ahArg = new ObjectHandle[hProxy.getParamCount()];
                ahArg[0] = hOpts == ObjectHandle.DEFAULT ? xNullable.makeHandle(frame) : hOpts;

                switch (hProxy.call1(frame, null, ahArg, Op.A_STACK)) {
                case Op.R_NEXT:
                    return validateResource(frame, frame.popStack());

                case Op.R_CALL: {
                    DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                    hDeferred.addContinuation(frameCaller -> {
                        ObjectHandle hR = validateResource(frameCaller, frameCaller.popStack());
                        return Op.isDeferred(hR)
                                ? hR.proceed(frameCaller, null) // must be an exception
                                : frameCaller.pushStack(hR);
                    });
                    return hDeferred;
                }

                case Op.R_EXCEPTION:
                    return new DeferredCallHandle(xException.makeHandle(frame,
                        "Invalid resource: " + key, frame.m_hException));

                default:
                    throw new IllegalStateException();
                }
            });
        } else {
            f_mapResources.put(key, (frame, hOpts) -> validateResource(frame, hSupplier));
        }
    }

    /**
     * Validate that the injected resource is a pass-through type that belongs to this container's
     * type system.
     */
    private ObjectHandle validateResource(Frame frame, ObjectHandle hResource) {
        TypeConstant typeResource = hResource.getComposition().getType();

        if (!hResource.isPassThrough(this)) {
            return new DeferredCallHandle(xException.mutableObject(frame, typeResource, false));
        }

        if (!typeResource.isShared(getConstantPool())) {
            return new DeferredCallHandle(xException.makeHandle(frame,
                "Injection type is not a shared: \"" + typeResource.getValueString() + '"'));
        }
        return hResource;
    }


    // ----- Container API -------------------------------------------------------------------------

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts) {
        InjectionSupplier supplier = f_mapResources.get(new InjectionKey(sName, type));
        if (supplier != null) {
            return supplier.supply(frame, hOpts);
        }
        if (f_hProvider == null) {
            // trusted host container (no guest sandbox provider): fall back to the parent/native
            // plane for standard resources, like MainContainer does. Guest containers below keep
            // the strict sandbox - they receive ONLY what their provider explicitly granted.
            ObjectHandle hResource = f_parent.getInjectable(frame, sName, type, hOpts);
            if (hResource != null) {
                return hResource;
            }
        }
        return type.isNullable() ? xNullable.makeHandle(frame) : null;
    }

    @Override
    public boolean isShared(ModuleConstant idModule) {
        return super.isShared(idModule) || f_listShared.contains(idModule);
    }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The resource provider object (it's known to be "Closeable").
     */
    public final ObjectHandle f_hProvider;

    /**
     * List of shared modules.
     */
    private final List<ModuleConstant> f_listShared;

    /**
     * Map of resources that are injectable to this container, keyed by their InjectionKey.
     * The values are bi-functions that take a current frame and "opts" object as arguments.
     *
     * (See annotations.InjectRef and mgmt.ResourceProvider.DynamicResource natural sources.)
     */
    private final Map<InjectionKey, InjectionSupplier> f_mapResources = new HashMap<>();
}
