package org.xvm.runtime;


import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xFunction.NativeFunctionHandle;


/**
 * TODO: for now Container == SecureContainer
 * TODO this is currently building the container like it's the outermost container, not a nested
 *      container (e.g. one that could replace, hide, or add any number of injections)
 */
public class Container
    {
    public Container(Runtime runtime, String sAppName, ModuleRepository repository)
        {
        f_runtime = runtime;
        f_sAppName = sAppName;

        f_repository = repository;
        f_moduleRoot = repository.loadModule(Constants.ECSTASY_MODULE);

        f_templates = new TemplateRegistry(f_moduleRoot);
        f_adapter = f_templates.f_adapter;
        f_heapGlobal = new ObjectHeap(f_moduleRoot.getConstantPool(), f_templates);

        ModuleStructure module = repository.loadModule(sAppName);
        if (module == null)
            {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
            }
        f_idModule = module.getIdentityConstant();
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        ConstantPool.setCurrentPool(f_moduleRoot.getConstantPool());

        f_templates.loadNativeTemplates(f_moduleRoot);

        ConstantPool.setCurrentPool(null);

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
        ConstantPool.setCurrentPool(f_moduleRoot.getConstantPool());

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

            f_mapResources.put(new InjectionKey("clock"     , typeClock), (Supplier<ObjectHandle>) this::ensureDefaultClock);
            f_mapResources.put(new InjectionKey("localClock", typeClock), (Supplier<ObjectHandle>) this::ensureLocalClock);
            f_mapResources.put(new InjectionKey("utcClock"  , typeClock), (Supplier<ObjectHandle>) this::ensureUTCClock);
            }

        // +++ NanosTimer
        ClassTemplate templateTimer = f_templates.getTemplate("Timer");
        if (templateTimer != null)
            {
            TypeConstant typeTimer = templateTimer.getCanonicalType();

            ClassTemplate templateRealTimeTimer = f_templates.getTemplate("_native.NanosTimer");
            Supplier<ObjectHandle> supplierTimer = () ->
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

            Supplier<ObjectHandle> supplierConsole = () ->
                xService.makeHandle(createServiceContext("Console", f_moduleRoot),
                    templateRTConsole.getCanonicalClass(), typeConsole);

            f_mapResources.put(new InjectionKey("console", typeConsole), supplierConsole);
            }

        // +++ OSFileStore
        ClassTemplate templateFileStore = f_templates.getTemplate("fs.FileStore");
        ClassTemplate templateDirectory = f_templates.getTemplate("fs.FileStore");
        if (templateFileStore != null && templateDirectory != null)
            {
            TypeConstant typeFileStore = templateFileStore.getCanonicalType();
            TypeConstant typeDirectory = templateDirectory.getCanonicalType();

            f_mapResources.put(new InjectionKey("storage", typeFileStore), (Supplier<ObjectHandle>) this::ensureFileStore);
            f_mapResources.put(new InjectionKey("rootDir", typeDirectory), (Supplier<ObjectHandle>) this::ensureRootDir);
            f_mapResources.put(new InjectionKey("homeDir", typeDirectory), (Supplier<ObjectHandle>) this::ensureHomeDir);
            f_mapResources.put(new InjectionKey("curDir" , typeDirectory), (Supplier<ObjectHandle>) this::ensureCurDir);
            f_mapResources.put(new InjectionKey("tmpDir" , typeDirectory), (Supplier<ObjectHandle>) this::ensureTmpDir);
            }
        }

    protected ObjectHandle ensureDefaultClock()
        {
        // TODO
        return ensureLocalClock();
        }

    protected ObjectHandle ensureLocalClock()
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

    protected ObjectHandle ensureUTCClock()
        {
        // TODO
        return ensureDefaultClock();
        }

    protected ObjectHandle ensureFileStore()
        {
        ObjectHandle hStore = m_hFileStore;
        if (hStore == null)
            {
            ClassTemplate templateFileStore   = f_templates.getTemplate("fs.FileStore");
            ClassTemplate templateRTFileStore = f_templates.getTemplate("_native.fs.OSFileStore");
            TypeConstant  typeFileStore       = templateFileStore.getCanonicalType();
            m_hFileStore = hStore = xService.makeHandle(createServiceContext("Storage",
                    f_moduleRoot), templateRTFileStore.getCanonicalClass(), typeFileStore);
            }

        return hStore;
        }

    Path ensurePath(String sPath)
        {
        if (sPath == null)
            {
            sPath = "/";
            }

        }
    protected ObjectHandle ensureRootDir()
        {
        ObjectHandle hDir = m_hRootDir;
        if (hDir == null)
            {
            ClassTemplate templateDirectory   = f_templates.getTemplate("fs.FileStore");
            ClassTemplate templateRTDirectory = f_templates.getTemplate("_native.fs.OSFileStore");
            TypeConstant  typeDirectory       = templateDirectory.getCanonicalType();
            Path          path = ensurePath(null);
            // TODO
            throw new UnsupportedOperationException();
            }

        return hDir;
        }

    protected ObjectHandle ensureHomeDir()
        {
        ObjectHandle hDir = m_hHomeDir;
        if (hDir == null)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        return hDir;
        }

    protected ObjectHandle ensureCurDir()
        {
        ObjectHandle hDir = m_hCurDir;
        if (hDir == null)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        return hDir;
        }

    protected ObjectHandle ensureTmpDir()
        {
        ObjectHandle hDir = m_hTmpDir;
        if (hDir == null)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        return hDir;
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

    // return the injectable handle or null, if not resolvable
    // TODO: need an "override" name or better yet "injectionAttributes"
    public ObjectHandle getInjectable(String sName, TypeConstant type)
        {
        InjectionKey key = new InjectionKey(sName, type);
        Object oResource = f_mapResources.get(key);

        if (oResource instanceof ObjectHandle)
            {
            return (ObjectHandle) oResource;
            }

        if (oResource == null)
            {
            return null;
            }

        // TODO: concurrently
        ObjectHandle hResource = ((Supplier<ObjectHandle>) oResource).get();
        f_mapResources.put(key, hResource);
        return hResource;
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
    public final ModuleRepository f_repository;
    public final TemplateRegistry f_templates;
    public final Adapter          f_adapter;
    public final ObjectHeap       f_heapGlobal;

    final protected ModuleStructure f_moduleRoot;
    final protected ModuleConstant  f_idModule;

    // service context map (concurrent set)
    final Map<ServiceContext, ServiceContext> f_mapServices = new ConcurrentHashMap<>();

    // the service context for the container itself
    private ServiceContext m_contextMain;

    // the module
    private final String f_sAppName;
    private ClassTemplate m_templateModule;

    private ObjectHandle m_hLocalClock;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;

    // the values are: ObjectHandle | Supplier<ObjectHandle>
    final Map<InjectionKey, Object> f_mapResources = new HashMap<>();
    }
