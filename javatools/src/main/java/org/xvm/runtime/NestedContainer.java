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
        extends Container
    {
    /**
     * Instantiate a nested container.
     *
     * @param containerParent  the parent container
     * @param idModule         the module id
     * @param hProvider        a resource provider supplied by the parent container
     * @param listShared       a list ids for shared modules
     */
    public NestedContainer(Container containerParent, ModuleConstant idModule,
                           ObjectHandle hProvider, List<ModuleConstant> listShared)
        {
        super(containerParent.f_runtime, containerParent, idModule);

        f_hProvider  = hProvider;
        f_listShared = listShared;
        }


    // ----- NestedContainer API -------------------------------------------------------------------

    /**
     * @return a set of injections for this container's module
     */
    public Set<InjectionKey> collectInjections()
        {
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
    public void addResourceSupplier(InjectionKey key, ServiceHandle hService, ObjectHandle hSupplier)
        {
        if (hSupplier instanceof FunctionHandle hFunction)
            {
            Container      container = hService.f_context.f_container;
            FunctionHandle hProxy    = xRTFunction.makeAsyncDelegatingHandle(hService, hFunction);
            f_mapResources.put(key, (frame, hOpts) ->
                {
                ObjectHandle[] ahArg = new ObjectHandle[hProxy.getParamCount()];
                ahArg[0] = hOpts == ObjectHandle.DEFAULT ? xNullable.NULL : hOpts;

                switch (hProxy.call1(frame, null, ahArg, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        return validateResource(frame, container, key);

                    case Op.R_CALL:
                        {
                        DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                        hDeferred.addContinuation(frameCaller ->
                            frameCaller.pushStack(validateResource(frameCaller, container, key)));
                        return hDeferred;
                        }

                    case Op.R_EXCEPTION:
                        return new DeferredCallHandle(xException.makeHandle(frame,
                            "Invalid resource: " + key, frame.m_hException));

                    default:
                        throw new IllegalStateException();
                    }
                });
            }
        else if (hSupplier.isPassThrough(this))
            {
            f_mapResources.put(key, (frame, hOpts) -> hSupplier);
            }
        else
            {
            f_mapResources.put(key, (frame, hOpts) -> new DeferredCallHandle(
                    xException.makeHandle(frame, "Non-shareable resource: " + key)));
            }
        }

    /**
     * Validate, mask the injection type and ensure the ownership if necessary
     */
    private ObjectHandle validateResource(Frame frame, Container container, InjectionKey key)
        {
        ObjectHandle hResource = frame.popStack();
        if (hResource.isService())
            {
            hResource = hResource.maskAs(container, key.f_type);
            }
        else if (hResource.isMutable())
            {
            // the resource must be a service or a constant
            hResource = null;
            }

        return hResource == null
            ? new DeferredCallHandle(xException.makeHandle(frame, "Invalid resource: " + key))
            : hResource;
        }


    // ----- Container API -------------------------------------------------------------------------

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts)
        {
        InjectionSupplier supplier = f_mapResources.get(new InjectionKey(sName, type));
        return supplier == null
                ? null
                : maskInjection(frame, supplier.supply(frame, hOpts), type);
        }

    @Override
    public boolean isShared(ModuleConstant idModule)
        {
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