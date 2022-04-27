package org.xvm.runtime;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.function.BiFunction;

import org.xvm.asm.InjectionKey;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xException;
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
     * @param context   the parent container's context
     * @param idModule  the module id
     */
    public NestedContainer(Container containerParent, ServiceContext context, ModuleConstant idModule)
        {
        super(containerParent.f_runtime, containerParent, idModule);
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
        TypeConstant typeResource = key.f_type;
        if (hSupplier instanceof FunctionHandle hFunction)
            {
            Container      container = hService.f_context.f_container;
            FunctionHandle hProxy    = xRTFunction.makeAsyncDelegatingHandle(hService, hFunction);
            f_mapResources.put(key, (frame, hOpts) ->
                {
                ObjectHandle[] ahArg = new ObjectHandle[hProxy.getParamCount()];
                ahArg[0] = hOpts;

                switch (hProxy.call1(frame, null, ahArg, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        // mask the injection type (and ensure the ownership)
                        return frame.popStack().maskAs(container, typeResource);

                    case Op.R_CALL:
                        {
                        DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                        hDeferred.addContinuation(frameCaller ->
                            {
                            frameCaller.pushStack(frameCaller.popStack().maskAs(container, typeResource));
                            return Op.R_NEXT;
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
            }
        else if (hSupplier.isPassThrough(this))
            {
            f_mapResources.put(key, (frame, hOpts) -> hSupplier);
            }
        }


    // ----- Container API -------------------------------------------------------------------------

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts)
        {
        BiFunction<Frame, ObjectHandle, ObjectHandle> fnResource =
            f_mapResources.get(new InjectionKey(sName, type));

        return fnResource == null ? null : fnResource.apply(frame, hOpts);
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * Map of resources that are injectable to this container, keyed by their InjectionKey.
     * The values are bi-functions that take a current frame and "opts" object as arguments.
     *
     * (See annotations.InjectRef and mgmt.ResourceProvider.DynamicResource natural sources.)
     */
    private final Map<InjectionKey, BiFunction<Frame, ObjectHandle, ObjectHandle>>
            f_mapResources = new HashMap<>();
    }