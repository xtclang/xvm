package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Consumer;
import java.util.function.Function;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xFunction.NativeFunctionHandle;

import static org.xvm.asm.Op.A_STACK;


/**
 * TODO: for now Container == SecureContainer
 * TODO this is currently building the container like it's the outermost container, not a nested
 *      container (e.g. one that could replace, hide, or add any number of injections)
 */
public class Container
    {
    public Container(Runtime runtime, String sAppName, ModuleRepository repository,
                     TemplateRegistry templates, ObjectHeap heapGlobal)
        {
        f_runtime  = runtime;
        f_sAppName = sAppName;

        f_moduleRoot = repository.loadModule(Constants.ECSTASY_MODULE);

        ModuleStructure module = repository.loadModule(sAppName);
        if (module == null)
            {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
            }

        f_idModule   = module.getIdentityConstant();
        f_templates  = templates;
        f_heapGlobal = heapGlobal;
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        ModuleStructure structModule = (ModuleStructure) f_idModule.getComponent();
        ConstantPool.setCurrentPool(structModule.getConstantPool());

        m_templateModule = f_templates.getTemplate(f_idModule);

        m_contextMain = createServiceContext(f_sAppName, structModule);
        xService.makeHandle(m_contextMain,
            xService.INSTANCE.getCanonicalClass(),
            xService.INSTANCE.getCanonicalType());

        initResources();

        ConstantPool.setCurrentPool(null);
        }

    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        ConstantPool poolPrev = ConstantPool.getCurrentPool();
        ConstantPool.setCurrentPool(m_templateModule.pool());

        try
            {
            TypeInfo infoApp = m_templateModule.getCanonicalType().ensureTypeInfo();
            int cArgs = ahArg == null ? 0 : ahArg.length;

            TypeConstant[] atypeArg;
            if (cArgs == 0)
                {
                atypeArg = TypeConstant.NO_TYPES;
                }
            else
                {
                atypeArg = new TypeConstant[cArgs];
                for (int i = 0; i < cArgs; i++)
                    {
                    atypeArg[i] = ahArg[i].getType();
                    }
                }

            MethodConstant idMethod = infoApp.findCallable(sMethodName, true, false,
                TypeConstant.NO_TYPES, atypeArg, null);

            if (idMethod == null)
                {
                System.err.println("Missing: " +  sMethodName + " method for " + m_templateModule);
                return;
                }

            TypeConstant     typeModule = f_idModule.getType();
            ClassComposition clzModule  = m_templateModule.ensureClass(typeModule, typeModule);
            CallChain        chain      = clzModule.getMethodCallChain(idMethod.getSignature());
            FunctionHandle   hFunction  = xFunction.makeHandle(chain, 0);

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle((frame, ah, iReturn) ->
                {
                ObjectHandle hModule = frame.getConstHandle(f_idModule);

                if (Op.isDeferred(hModule))
                    {
                    ObjectHandle[] ahModule = new ObjectHandle[] {hModule};

                    Frame.Continuation stepNext = frameCaller ->
                        hFunction.call1(frameCaller, ahModule[0], ahArg, Op.A_IGNORE);

                    return new Utils.GetArguments(ahModule, stepNext).doNext(frame);
                    }

                return hFunction.call1(frame, hModule, ahArg, Op.A_IGNORE);
                });

            m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_idModule, e);
            }
        finally
            {
            ConstantPool.setCurrentPool(poolPrev);
            }
        }

    protected void initResources()
        {
        // +++ LocalClock
        ClassTemplate templateClock = f_templates.getTemplate("Clock");
        if (templateClock != null)
            {
            TypeConstant typeClock = templateClock.getCanonicalType();

            f_mapResources.put(new InjectionKey("clock"     , typeClock), this::ensureDefaultClock);
            f_mapResources.put(new InjectionKey("localClock", typeClock), this::ensureLocalClock);
            f_mapResources.put(new InjectionKey("utcClock"  , typeClock), this::ensureUTCClock);
            }

        // +++ NanosTimer
        ClassTemplate templateTimer = f_templates.getTemplate("Timer");
        if (templateTimer != null)
            {
            TypeConstant typeTimer = templateTimer.getCanonicalType();

            ClassTemplate templateRealTimeTimer = f_templates.getTemplate("_native.NanosTimer");
            Function<Frame, ObjectHandle> supplierTimer = (frame) ->
                xService.makeHandle(createServiceContext("Timer", f_moduleRoot),
                        templateRealTimeTimer.getCanonicalClass(), typeTimer);
            f_mapResources.put(new InjectionKey("timer", typeTimer), supplierTimer);
            }

        // +++ Console
        ClassTemplate templateConsole = f_templates.getTemplate("io.Console");
        if (templateConsole != null)
            {
            TypeConstant typeConsole = templateConsole.getCanonicalType();

            ClassTemplate templateRTConsole = f_templates.getTemplate("_native.TerminalConsole");

            Function<Frame, ObjectHandle> supplierConsole = (frame) ->
                xService.makeHandle(createServiceContext("Console", f_moduleRoot),
                    templateRTConsole.getCanonicalClass(), typeConsole);

            f_mapResources.put(new InjectionKey("console", typeConsole), supplierConsole);
            }

        // +++ OSFileStore
        ClassTemplate templateFileStore = f_templates.getTemplate("fs.FileStore");
        ClassTemplate templateDirectory = f_templates.getTemplate("fs.Directory");
        if (templateFileStore != null && templateDirectory != null)
            {
            TypeConstant typeFileStore = templateFileStore.getCanonicalType();
            TypeConstant typeDirectory = templateDirectory.getCanonicalType();

            f_mapResources.put(new InjectionKey("storage", typeFileStore), this::ensureFileStore);
            f_mapResources.put(new InjectionKey("rootDir", typeDirectory), this::ensureRootDir);
            f_mapResources.put(new InjectionKey("homeDir", typeDirectory), this::ensureHomeDir);
            f_mapResources.put(new InjectionKey("curDir" , typeDirectory), this::ensureCurDir);
            f_mapResources.put(new InjectionKey("tmpDir" , typeDirectory), this::ensureTmpDir);
            }
        }

    protected ObjectHandle ensureDefaultClock(Frame frame)
        {
        // TODO
        return ensureLocalClock(frame);
        }

    protected ObjectHandle ensureLocalClock(Frame frame)
        {
        ObjectHandle hClock = m_hLocalClock;
        if (hClock == null)
            {
            ClassTemplate templateClock = f_templates.getTemplate("Clock");
            if (templateClock != null)
                {
                TypeConstant typeClock = templateClock.getCanonicalType();
                ClassTemplate templateRealTimeClock = f_templates.getTemplate("_native.LocalClock");
                m_hLocalClock = hClock = xService.makeHandle(createServiceContext("LocalClock",
                        f_moduleRoot), templateRealTimeClock.getCanonicalClass(), typeClock);

                }
            }

        return hClock;
        }

    protected ObjectHandle ensureUTCClock(Frame frame)
        {
        // TODO
        return ensureDefaultClock(frame);
        }

    protected ObjectHandle ensureOSStorage(Frame frame)
        {
        ObjectHandle hStorage = m_hOSStorage;
        if (hStorage == null)
            {
            ClassTemplate    templateStorage = f_templates.getTemplate("_native.fs.OSStorage");
            ClassComposition clzStorage      = templateStorage.getCanonicalClass();
            MethodStructure  constructor     = templateStorage.f_struct.findConstructor();

            switch (templateStorage.construct(frame, constructor, clzStorage,
                                              null, Utils.OBJECTS_NONE, A_STACK))
                {
                case Op.R_NEXT:
                    hStorage = frame.popStack();
                    break;

                case Op.R_EXCEPTION:
                    break;

                default:
                    throw new IllegalStateException();
                }
            m_hOSStorage = hStorage;
            }
        else if (hStorage instanceof FutureHandle &&
                ((FutureHandle) hStorage).isCompletedNormally())
            {
            m_hOSStorage = hStorage = ((FutureHandle) hStorage).getValue();
            }

        return hStorage;
        }

    protected ObjectHandle ensureFileStore(Frame frame)
        {
        ObjectHandle hStore = m_hFileStore;
        if (hStore == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage instanceof FutureHandle)
                {
                DeferredCallHandle hDeferred = ((FutureHandle) hOSStorage).
                    makeDeferredGetField(frame, "fileStore");
                hDeferred.addContinuation(frameCaller ->
                    {
                    m_hFileStore = frameCaller.peekStack();
                    return Op.R_NEXT;
                    });
                hStore = hDeferred;
                }
            else if (hOSStorage != null)
                {
                m_hFileStore = hStore = ((GenericHandle) hOSStorage).getField("fileStore");
                }
            }

        return hStore;
        }

    protected ObjectHandle ensureRootDir(Frame frame)
        {
        ObjectHandle hDir = m_hRootDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("rootDir").getIdentity();

                m_hRootDir = hDir =
                    getProperty(frame, hOSStorage, idProp, h -> m_hRootDir = h);
                }
            }

        return hDir;
        }

    protected ObjectHandle ensureHomeDir(Frame frame)
        {
        ObjectHandle hDir = m_hHomeDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("homeDir").getIdentity();

                m_hHomeDir = hDir =
                    getProperty(frame, hOSStorage, idProp, h -> m_hHomeDir = h);
                }
            }

        return hDir;
        }

    protected ObjectHandle ensureCurDir(Frame frame)
        {
        ObjectHandle hDir = m_hCurDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("curDir").getIdentity();

                m_hCurDir = hDir =
                    getProperty(frame, hOSStorage, idProp, h -> m_hCurDir = h);
                }
            }

        return hDir;
        }

    protected ObjectHandle ensureTmpDir(Frame frame)
        {
        ObjectHandle hDir = m_hTmpDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("tmpDir").getIdentity();

                m_hTmpDir = hDir =
                    getProperty(frame, hOSStorage, idProp, h -> m_hTmpDir = h);
                }
            }

        return hDir;
        }

    /**
     * Helper method to get a property.
     */
    private ObjectHandle getProperty(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                     Consumer<ObjectHandle> consumer)
        {
        if (hTarget instanceof FutureHandle)
            {
            DeferredCallHandle hDeferred =
                ((FutureHandle) hTarget).makeDeferredGetProperty(frame, idProp);
            hDeferred.addContinuation(frameCaller ->
                {
                consumer.accept(frameCaller.peekStack());
                return Op.R_NEXT;
                });
            return hDeferred;
            }

        if (hTarget instanceof DeferredCallHandle)
            {
            DeferredCallHandle hDeferred =
                ((DeferredCallHandle) hTarget).makeDeferredGetProperty(frame, idProp);
            hDeferred.addContinuation(frameCaller ->
                {
                consumer.accept(frameCaller.peekStack());
                return Op.R_NEXT;
                });
            return hDeferred;
            }

        ClassTemplate template = hTarget.getTemplate();
        switch (template.getPropertyValue(frame, hTarget, idProp, A_STACK))
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                Frame frameNext = frame.m_frameNext;
                frameNext.setContinuation(frameCaller ->
                    {
                    consumer.accept(frameCaller.peekStack());
                    return Op.R_NEXT;
                    });
                return new DeferredCallHandle(frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    public ServiceContext createServiceContext(String sName, ModuleStructure module)
        {
        ServiceContext context = new ServiceContext(this, module, sName,
            f_runtime.f_idProducer.getAndIncrement());

        f_mapServices.put(context, context);
        f_runtime.f_daemons.addService(context);

        return context;
        }

    public void removeServiceContext(ServiceContext context)
        {
        f_runtime.f_daemons.removeService(context);
        f_mapServices.remove(context);
        }

    /**
     * Obtain an injected handle for the specified name and type.
     *
     * TODO: need to be able to provide "injectionAttributes"
     *
     * @param frame  the current frame
     * @param sName  the name of the injected object
     * @param type   the type of the injected object
     *
     * @return the injectable handle or null, if the name not resolvable
     *
     */
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type)
        {
        InjectionKey key = new InjectionKey(sName, type);
        Function<Frame, ObjectHandle> fnResource = f_mapResources.get(key);

        ObjectHandle hValue = fnResource == null ? null : fnResource.apply(frame);
        return hValue instanceof FutureHandle
            ? ((FutureHandle) hValue).makeDeferredHandle(frame)
            : hValue;
        }

    public ServiceContext getMainContext()
        {
        return m_contextMain;
        }

    public boolean isIdle()
        {
        for (ServiceContext context : f_mapServices.keySet())
            {
            if (context.isContended())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public String toString()
        {
        return "Container: " + f_idModule.getName();
        }

    public static class InjectionKey
        {
        public final String f_sName;
        public final TypeConstant f_type;

        public InjectionKey(String sName, TypeConstant type)
            {
            f_sName = sName;
            f_type = type;
            }

        @Override
        public boolean equals(Object o)
            {
            if (this == o)
                {
                return true;
                }

            if (!(o instanceof InjectionKey))
                {
                return false;
                }

            InjectionKey that = (InjectionKey) o;

            return Objects.equals(this.f_sName, that.f_sName) &&
                   Objects.equals(this.f_type, that.f_type);
            }

        @Override
        public int hashCode()
            {
            return f_sName.hashCode() + f_type.hashCode();
            }

        @Override
        public String toString()
            {
            return "Key: " + f_sName + ", " + f_type;
            }
        }

    public final Runtime          f_runtime;
    public final TemplateRegistry f_templates;
    public final ObjectHeap       f_heapGlobal;

    protected final ModuleStructure f_moduleRoot;
    protected final ModuleConstant  f_idModule;

    // service context map (concurrent set)
    final Map<ServiceContext, ServiceContext> f_mapServices = new ConcurrentHashMap<>();

    // the service context for the container itself
    private ServiceContext m_contextMain;

    // the module
    private final String f_sAppName;
    private ClassTemplate m_templateModule;

    private ObjectHandle m_hLocalClock;
    private ObjectHandle m_hOSStorage;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;

    final Map<InjectionKey, Function<Frame, ObjectHandle>> f_mapResources = new HashMap<>();
    }
