package org.xvm.runtime;


import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.UnionTypeConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.Child;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.reflect.xModule;
import org.xvm.runtime.template.reflect.xPackage;


/**
 * The base Container functionality.
 */
public abstract class Container
        implements LinkerContext
    {
    protected Container(Runtime runtime, Container containerParent, ModuleConstant idModule)
        {
        f_runtime  = runtime;
        f_parent   = containerParent;
        f_heap     = new ConstHeap(this);
        f_idModule = idModule;

        // don't register the native container
        if (containerParent != null)
            {
            f_runtime.registerContainer(this);
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

    /**
     * @return the ConstantPool for this container
     */
    public ConstantPool getConstantPool()
        {
        return f_idModule.getConstantPool();
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
            try (var ignore = ConstantPool.withPool(getConstantPool()))
                {
                m_contextMain = ctx = createServiceContext(getModule().getName());
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
        ServiceContext service = new ServiceContext(this, sName, f_runtime.makeUniqueId());
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
        // queue to its parent container which eventually pushes to the runtime. Thus, there is a
        // hierarchy of fairness. For now though we just skip over all of this and push the
        // processing directly to the top level runtime.

        f_pendingWorkCount.incrementAndGet();
        f_runtime.submitService(() ->
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
        }

    /**
     * Schedule an IO task.
     *
     * @param task  the task
     *
     * @return a CompletableFuture associated with the scheduled task
     */
    public <R> CompletableFuture<R> scheduleIO(Callable<R> task)
        {
        CompletableFuture<R> cf = new CompletableFuture<>();
        f_runtime.submitIO(() ->
            {
            try
                {
                cf.complete(task.call());
                }
            catch (Throwable e)
                {
                cf.completeExceptionally(e);
                }
            });
        return cf;
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
    abstract public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts);

    /**
     * A delegation method into the ConstHeap API.
     *
     * Could be overridden by Container implementations to use container-specific heaps.
     */
    public ObjectHandle ensureConstHandle(Frame frame, Constant constValue)
        {
        return f_heap.ensureConstHandle(frame, constValue);
        }

    /**
     * @return a ClassTemplate for the specified type
     */
    public ClassTemplate getTemplate(TypeConstant type)
        {
        if (f_parent != null && type.isShared(f_parent.getConstantPool()))
            {
            return f_parent.getTemplate(type);
            }

        ClassTemplate template = f_mapTemplatesByType.get(type);
        if (template == null)
            {
            if (type.isSingleUnderlyingClass(true))
                {
                // make sure we don't hold on other pool's constants
                type = (TypeConstant) getConstantPool().register(type);

                IdentityConstant idClass = type.getSingleUnderlyingClass(true);
                template = getTemplate(idClass);

                // native templates for parameterized classes may "promote" themselves based on the
                // parameter type, but we can only do it within the same container
                if (type.isShared(template.f_container.getConstantPool()))
                    {
                    template = template.getTemplate(type);
                    }
                f_mapTemplatesByType.put(type, template);
                }
            else
                {
                throw new UnsupportedOperationException();
                }
            }
        return template;
        }

    /**
     * @return a ClassTemplate for the specified class identity
     */
    public ClassTemplate getTemplate(IdentityConstant idClass)
        {
        if (f_parent != null && idClass.isShared(f_parent.getConstantPool()))
            {
            return f_parent.getTemplate(idClass);
            }

        ClassTemplate template = f_mapTemplatesByType.get(idClass.getType());
        if (template != null)
            {
            return template;
            }

        // make sure we don't hold on other pool's constants or structures
        idClass = (IdentityConstant) getConstantPool().register(idClass);

        ClassStructure structClass = (ClassStructure) idClass.getComponent();
        if (structClass == null)
            {
            throw new RuntimeException("Missing class structure: " + idClass);
            }

        return f_mapTemplatesByType.computeIfAbsent(idClass.getType(), type ->
            {
            ClassTemplate temp;
            switch (structClass.getFormat())
                {
                case ENUMVALUE:
                case ENUM:
                    temp = new xEnum(this, structClass, false);
                    temp.initNative();
                    break;

                case MIXIN:
                case CLASS:
                case INTERFACE:
                    temp = structClass.isInstanceChild()
                        ? new Child(this,   structClass, false)
                        : new xObject(this, structClass, false);
                    break;

                case SERVICE:
                    temp = new xService(this, structClass, false);
                    break;

                case CONST:
                    temp = structClass.isException()
                            ? new xException(this, structClass, false)
                            : new xConst(this,     structClass, false);
                    break;

                case MODULE:
                    temp = new xModule(this, structClass, false);
                    break;

                case PACKAGE:
                    temp = new xPackage(this, structClass, false);
                    break;

                default:
                    throw new UnsupportedOperationException(
                        "Format is not supported: " + structClass);
                }
            return temp;
            });
        }

    /**
     * @return a ClassTemplate for a type associated with the specified constant
     */
    public ClassTemplate getTemplate(Constant constValue)
        {
        return getTemplate(getType(constValue)); // the type must exist
        }

    /**
     * @return a TypeConstant associated with the specified constant
     */
    public TypeConstant getType(Constant constValue)
        {
        if (constValue.getConstantPool() == getConstantPool())
            {
            if (constValue instanceof SingletonConstant constSingleton)
                {
                return constSingleton.getType();
                }
            }
        return getNativeContainer().getConstType(constValue);
        }

    /**
     * Produce a TypeComposition based on the specified TypeConstant.
     */
    public TypeComposition resolveClass(TypeConstant type)
        {
        if (type instanceof PropertyClassTypeConstant typeProp)
            {
            ClassComposition clz = (ClassComposition) resolveClass(
                                        typeProp.getParentType().removeAccess());
            return clz.ensurePropertyComposition(typeProp.getPropertyInfo());
            }

        // make sure we don't hold on other pool's constants
        type = (TypeConstant) getConstantPool().register(type);

        return getTemplate(type).ensureClass(this, type.normalizeParameters());
        }

    /**
     * Produce a ClassComposition for the specified inception type.
     *
     * Note: the passed inception type should be normalized (all formal parameters resolved).
     */
    public ClassComposition ensureClassComposition(TypeConstant typeInception, ClassTemplate template)
        {
        ClassComposition clz = f_mapCompositions.get(typeInception);
        if (clz == null)
            {
            ConstantPool pool = getConstantPool();

            assert typeInception.isShared(pool);
            assert !typeInception.isAccessSpecified();
            assert typeInception.normalizeParameters().equals(typeInception);

            typeInception = (TypeConstant) pool.register(typeInception);

            clz = f_mapCompositions.computeIfAbsent(typeInception, (type) ->
                {
                ClassTemplate templateReal = type.isAnnotated() && type.isIntoVariableType()
                        ? type.getTemplate(this)
                        : template;

                return new ClassComposition(this, templateReal, type);
                });
            }

        // we need to make this call outside of the constructor due to a possible recursion
        // (ConcurrentHashMap.computeIfAbsent doesn't allow that)
        clz.ensureFieldLayout();
        return clz;
        }

    /**
     * @return the closest to the root container that is responsible for holding a singleton
     *         ObjectHandle for the specified constant
     */
    public Container getOriginContainer(SingletonConstant constSingle)
        {
        IdentityConstant idClz = constSingle.getClassConstant();
        return isShared(idClz.getModuleConstant())
                ? f_parent.getOriginContainer(constSingle)
                : this;
        }

    /**
     * @return a ClassTemplate for a type associated with the specified name (core classes only)
     */
    public ClassTemplate getTemplate(String sName)
        {
        return getNativeContainer().getTemplate(sName);
        }

    /**
     * @return a ClassStructure for the specified name (core classes only)
     */
    public ClassStructure getClassStructure(String sName)
        {
        return getNativeContainer().getClassStructure(sName);
        }

    /**
     * TODO
     */
    public ModuleRepository getModuleRepository()
        {
        return getNativeContainer().getModuleRepository();
        }

    /**
     * Create a new FileStructure for the specified module built on top of the system modules.
     *
     * @param moduleApp  the module to build a FileStructure for
     *
     * @return a new FileStructure
     */
    public FileStructure createFileStructure(ModuleStructure moduleApp)
        {
        return getNativeContainer().createFileStructure(moduleApp);
        }

    /**
     * @return true iff the type system of the specified module is shared with this container
     */
    public boolean isShared(ModuleConstant idModule)
        {
        return idModule.isEcstasyModule();
        }


    // ----- x:Container API helpers ---------------------------------------------------------------

    /**
     * This method is only used to determine whether the connector can be terminated (once and done
     * execution mode).
     *
     * @return true iff there are no pending requests for this container
     */
    public boolean isIdle()
        {
        return f_pendingWorkCount.get() == 0 && m_contextMain.isIdle() &&
                (f_parent == null || f_parent.isIdle());
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
                    .toList())
                {
                ObjectHandle hModule = frame.getConstHandle(idDep);
                ahModules[index] = hModule;
                ahShared [index] = xBoolean.makeHandle(isModuleShared(idDep));
                fDeferred |= Op.isDeferred(hModule);
                ++index;
                }

            ClassTemplate    templateTS  = getTemplate("reflect.TypeSystem");
            ClassComposition clzTS       = templateTS.getCanonicalClass();
            MethodStructure  constructor = templateTS.getStructure().findMethod("construct", 2);
            ObjectHandle[]   ahArg       = new ObjectHandle[constructor.getMaxVars()];

            ahArg[1] = xArray.makeArrayHandle(xArray.getBooleanArrayComposition(),
                        ahShared.length, ahShared, Mutability.Constant);

            TypeComposition clzArray = xModule.ensureArrayComposition(frame.f_context.f_container);
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
     * Mask the resource if necessary.
     *
     * @param hResource   the resource handle
     * @param typeInject  the desired injection type
     *
     * @return the injected resource of the specified type
     */
    protected ObjectHandle maskInjection(ObjectHandle hResource, TypeConstant typeInject)
        {
        if (hResource instanceof DeferredCallHandle hDeferred)
            {
            hDeferred.addContinuation(frameCaller ->
                frameCaller.pushStack(completeMasking(frameCaller.popStack(), typeInject)));
            return hResource;
            }
        return completeMasking(hResource, typeInject);
        }

    private ObjectHandle completeMasking(ObjectHandle hResource, TypeConstant typeInject)
        {
        TypeConstant typeResource = hResource.getComposition().getType();
        if (typeResource.isShared(getConstantPool()))
            {
            if (typeInject instanceof UnionTypeConstant typeUnion)
                {
                if (typeInject.isNullable())
                    {
                    if (hResource == xNullable.NULL)
                        {
                        return hResource;
                        }
                    typeInject = typeInject.removeNullable();
                    }
                else
                    {
                    // the injection's declared type is A|B; this should be extremely rare, if ever
                    // used at all; the code below is just for completeness
                    Set<TypeConstant> setMatch = typeUnion.collectMatching(typeInject, null);
                    assert setMatch.size() == 1;
                    typeInject = setMatch.iterator().next();
                    }
                }
            return typeResource.equals(typeInject)
                    ? hResource
                    : hResource.maskAs(this, typeInject);
            }
        throw new UnsupportedOperationException("Pure type injection");
        }


    // ----- LinkerContext interface ---------------------------------------------------------------

    @Override
    public boolean isSpecified(String sName)
        {
        // TODO: environment based?
        return switch (sName)
            {
            case "debug", "test" -> true;
            default              -> false;
            };
        }

    @Override
    public boolean isPresent(IdentityConstant constId)
        {
        // TODO: is this sufficient - part of the Ecstasy module?
        return constId.getModuleConstant().equals(getModule());
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


    // ----- helper methods ------------------------------------------------------------------------

    private NativeContainer getNativeContainer()
        {
        Container container = this;
        while (true)
            {
            if (container instanceof NativeContainer nativeContainer)
                {
                return nativeContainer;
                }
            container = container.f_parent;
            }
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Container: " + f_idModule.getName();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The runtime.
     */
    public final Runtime f_runtime;

    /**
     * The constant heap.
     */
    public final ConstHeap f_heap;

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
    private final AtomicLong f_pendingWorkCount = new AtomicLong();

    /**
     * Set of services that were started by this container (stored as a Map with no values).
     */
    private final Map<ServiceContext, Object> f_setServices = new WeakHashMap<>();

    /**
     * A cache of "instantiate-able" ClassCompositions keyed by the "inception type".
     *
     * Any ClassComposition in this map is defined by a {@link ClassConstant} referring to a
     * concrete natural class. It also keeps the secondary map of compositions for revealed types.
     */
    private final Map<TypeConstant, ClassComposition> f_mapCompositions = new ConcurrentHashMap<>();

    /**
     * A cache of ClassTemplates loaded by this Container keyed by type.
     */
    protected final Map<TypeConstant, ClassTemplate> f_mapTemplatesByType = new ConcurrentHashMap<>();
    }