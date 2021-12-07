package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.atomic.AtomicLong;

import java.util.function.BiFunction;

import java.util.stream.Collectors;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.reflect.xModule;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * The base Container functionality.
 */
public abstract class Container
        implements LinkerContext
    {
    protected Container(Runtime runtime, Container containerParent,
                        TemplateRegistry templates, ConstHeap heap, ModuleConstant idModule)
        {
        f_runtime   = runtime;
        f_parent    = containerParent;
        f_templates = templates;
        f_heap      = heap;
        f_idModule = idModule;

        if (runtime != null)
            {
            runtime.f_containers.add(this);
            }
        }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the "main" service context for this Container.
     */
    public ServiceContext getServiceContext()
        {
        return m_contextMain;
        }

    /**
     * Obtain the module constant for the main module in this Container.
     */
    public ModuleConstant getModule()
        {
        return f_idModule;
        }


    // ----- Container API -------------------------------------------------------------------------

    /**
     * Ensure the existence of the "main" service context for this Container.
     */
    public ServiceContext ensureServiceContext()
        {
        ServiceContext ctx = m_contextMain;
        if (ctx == null)
            {
            ConstantPool pool = f_idModule.getConstantPool();

            try (var x = ConstantPool.withPool(pool))
                {
                m_contextMain = ctx = createServiceContext(f_idModule.getName());
                xService.INSTANCE.createServiceHandle(ctx,
                    xService.INSTANCE.getCanonicalClass(),
                    xService.INSTANCE.getCanonicalType());
                }
            }
        return ctx;
        }

    /**
     * Create a new service context for this Container.
     *
     * @param sName  the service name
     *
     * @return the new service context
     */
    public ServiceContext createServiceContext(String sName)
        {
        ServiceContext service = new ServiceContext(this, f_idModule.getConstantPool(),
                sName, f_runtime.f_idProducer.getAndIncrement());
        f_setServices.put(service, null);
        return service;
        }

    /**
     * @return a set of services that belong to this container
     */
    public Set<ServiceContext> getServices()
        {
        return f_setServices.keySet();
        }

    /**
     * Schedule processing of the specified ServiceContext.
     *
     * @param service the ServiceContext to schedule
     */
    public void schedule(ServiceContext service)
        {
        // TODO: add a container level fair scheduling queue and submit the service there. The
        // container should then follow a similar pattern and push processing of its fair scheduling
        // queue to its parent container which eventually pushes to the runtime. Thus there is a
        // hierarchy of fairness. For now though we just skip over all of this and push the processing
        // directly to the top level runtime.

        f_pendingWorkCount.incrementAndGet();
        f_runtime.submit(() ->
            {
            try
                {
                service.execute(true);
                }
            catch (Throwable e)
                {
                // must not happen
                e.printStackTrace();
                System.exit(-1);
                }
            finally
                {
                f_pendingWorkCount.decrementAndGet();
                }
            });
        }

    /**
     * Terminate the specified ServiceContext.
     *
     * @param service the ServiceContext to terminate
     */
    public void terminate(ServiceContext service)
        {
        f_setServices.remove(service);

        if (f_setServices.isEmpty())
            {
            f_runtime.f_containers.remove(this);
            }

        // TODO: should we do something else if nothing left?
        }

    /**
     * Find a module method to call.
     *
     * @param sMethod  the name
     * @param ahArg    the arguments to pass to the method
     *
     * @return the method constant or null if not found
     */
    public MethodConstant findModuleMethod(String sMethod, ObjectHandle[] ahArg)
        {
        TypeInfo infoModule = getModule().getType().ensureTypeInfo();

        TypeConstant[] atypeArg;
        if (ahArg.length == 0)
            {
            atypeArg = TypeConstant.NO_TYPES;
            }
        else
            {
            int cArgs = ahArg.length;

            atypeArg = new TypeConstant[cArgs];
            for (int i = 0; i < cArgs; i++)
                {
                atypeArg[i] = ahArg[i].getType(); // could be null for DEFAULT values
                }
            }

        return infoModule.findCallable(sMethod, true, false, TypeConstant.NO_TYPES, atypeArg);
        }

    /**
     * Obtain an injected handle for the specified name and type.
     *
     * @param frame  the current frame
     * @param sName  the name of the injected object
     * @param type   the type of the injected object
     * @param hOpts  an optional "opts" handle (see InjectedRef.x)
     *
     * @return the injectable handle (can be a DeferredCallHandle) or null, if the name not resolvable
     */
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts)
        {
        BiFunction<Frame, ObjectHandle, ObjectHandle> fnResource =
            f_mapResources.get(new InjectionKey(sName, type));

        return fnResource == null ? null : fnResource.apply(frame, hOpts);
        }

    /**
     * A delegation method into the ConstHeap API.
     *
     * Could be overridden by Container implementations to use container-specific heaps.
     */
    public ObjectHandle ensureConstHandle(Frame frame, Constant constValue)
        {
        return f_heap.ensureConstHandle(frame, constValue);
        }


    // ----- x:Container API helpers ---------------------------------------------------------------

    /**
     * @return true iff there are no pending requests for this service
     */
    public boolean isIdle()
        {
        return f_pendingWorkCount.get() == 0 && m_contextMain.isIdle();
        }

    /**
     * Ensure a TypeSystem handle for this container.
     *
     * @param frame    the current frame
     * @param iReturn  the register to put the handle into
     *
     * @return Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION
     */
    public int ensureTypeSystemHandle(Frame frame, int iReturn)
        {
        ObjectHandle hTS = m_hTypeSystem;
        if (hTS == null)
            {
            ModuleConstant  idModule = getModule();
            ModuleStructure module   = (ModuleStructure) idModule.getComponent();

            Map<ModuleConstant, String> mapModules = module.collectDependencies();
            int                         cModules   = mapModules.size();
            ObjectHandle[]              ahModules  = new ObjectHandle[cModules];
            BooleanHandle[]             ahShared   = new BooleanHandle[cModules];
            boolean                     fDeferred  = false;
            int                         index      = 0;
            for (ModuleConstant idDep : mapModules.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()))
                {
                ObjectHandle hModule = frame.getConstHandle(idDep);
                ahModules[index] = hModule;
                ahShared [index] = xBoolean.makeHandle(isModuleShared(idDep));
                fDeferred |= Op.isDeferred(hModule);
                ++index;
                }

            ClassTemplate    templateTS  = f_templates.getTemplate("reflect.TypeSystem");
            ClassComposition clzTS       = templateTS.getCanonicalClass();
            MethodStructure  constructor = templateTS.getStructure().findMethod("construct", 2);
            ObjectHandle[]   ahArg       = new ObjectHandle[constructor.getMaxVars()];

            ahArg[1] = xArray.makeArrayHandle(xArray.getBooleanArrayComposition(),
                        ahShared.length, ahShared, Mutability.Constant);

            TypeComposition clzArray = xModule.ensureArrayComposition();
            if (fDeferred)
                {
                Frame.Continuation stepNext = frameCaller ->
                    {
                    ahArg[0] = xArray.createImmutableArray(clzArray, ahModules);
                    return saveTypeSystemHandle(frame, iReturn,
                        templateTS.construct(frame, constructor, clzTS, null, ahArg, Op.A_STACK));
                    };

                return new Utils.GetArguments(ahModules, stepNext).doNext(frame);
                }

            ahArg[0] = xArray.createImmutableArray(clzArray, ahModules);
            return saveTypeSystemHandle(frame, iReturn,
                templateTS.construct(frame, constructor, clzTS, null, ahArg, Op.A_STACK));
            }
        return frame.assignValue(iReturn, hTS);
        }

    private int saveTypeSystemHandle(Frame frame, int iReturn, int iResult)
        {
        switch (iResult)
            {
            case Op.R_NEXT:
                return frame.assignValue(iReturn, m_hTypeSystem = frame.popStack());

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValue(iReturn, m_hTypeSystem = frameCaller.popStack()));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return true iff the specified module is shared
     */
    protected boolean isModuleShared(ModuleConstant idModule)
        {
        // TODO: this information will be supplied by the Linker;
        // for now assume that only the Ecstasy module is shared
        return idModule.getName().equals(ModuleStructure.ECSTASY_MODULE);
        }

    /**
     * Add a native resource supplier for an injection.
     *
     * @param key  the injection key
     * @param fn   the resource supplier bi-function
     */
    public void addResourceSupplier(InjectionKey key, BiFunction<Frame, ObjectHandle, ObjectHandle> fn)
        {
        f_mapResources.put(key, fn);
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
        if (hSupplier instanceof FunctionHandle)
            {
            Container      container = hService.f_context.f_container;
            FunctionHandle hProxy    = xRTFunction.makeAsyncDelegatingHandle(
                                            hService, (FunctionHandle) hSupplier);
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


    // ----- LinkerContext interface ---------------------------------------------------------------

    @Override
    public boolean isSpecified(String sName)
        {
        switch (sName)
            {
            case "debug":
            case "test":
                return true;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isPresent(IdentityConstant constId)
        {
        if (constId.getModuleConstant().equals(f_idModule))
            {
            // part of the Ecstasy module
            // TODO
            return true;
            }

        return false;
        }

    @Override
    public boolean isVersionMatch(ModuleConstant constModule, VersionConstant constVer)
        {
        // TODO
        return true;
        }

    @Override
    public boolean isVersion(VersionConstant constVer)
        {
        // TODO
        return true;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Container: " + f_idModule.getName();
        }


    // ----- data fields ---------------------------------------------------------------------------

    public final Runtime          f_runtime;
    public final TemplateRegistry f_templates;
    public final ConstHeap        f_heap;

    /**
     * The parent container.
     */
    public final Container f_parent;

    /**
     * The main module id.
     */
    protected final ModuleConstant f_idModule;

    /**
     * The service context for the container itself.
     */
    protected ServiceContext m_contextMain;

    /**
     * Cached TypeSystem handle.
     */
    protected ObjectHandle m_hTypeSystem;

    /**
     * A counter tracking both the number of services which have pending invocations to process
     * and the number of registered Alarms. While this count is above zero the container is
     * considered to still have work to do and won't auto-shutdown.
     */
    protected final AtomicLong f_pendingWorkCount = new AtomicLong();

    /**
     * Map of resources that are injectable to this container, keyed by their InjectionKey.
     * The values are bi-functions that take a current frame and "opts" object as arguments.
     *
     * (See annotations.InjectRef and mgmt.ResourceProvider.DynamicResource natural sources.)
     */
    protected final Map<InjectionKey, BiFunction<Frame, ObjectHandle, ObjectHandle>> f_mapResources = new HashMap<>();

    /**
     * Set of services that were started by this container (stored as a Map with no values).
     */
    protected final Map<ServiceContext, Object> f_setServices = new WeakHashMap<>();
    }
