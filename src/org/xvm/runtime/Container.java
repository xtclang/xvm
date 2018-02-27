package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xModule.ModuleHandle;


/**
 * TODO: for now Container == SecureContainer
 */
public class Container
    {
    final public Runtime f_runtime;
    final public TemplateRegistry f_templates;
    final public ConstantPool f_pool;
    final public Adapter f_adapter;
    final public ObjectHeap f_heapGlobal;

    final protected ModuleStructure f_module;
    final protected ModuleConstant f_constModule;

    // service id producer
    final AtomicInteger f_idProducer = new AtomicInteger();

    // service context map (concurrent set)
    final Map<ServiceContext, ServiceContext> f_mapServices = new ConcurrentHashMap<>();

    // the service context for the container itself
    private ServiceContext m_contextMain;

    // the module
    private ModuleHandle m_hModule;
    private ObjectHandle m_hApp;
    private String f_sAppName;

    // the values are: ObjectHandle | Supplier<ObjectHandle>
    final Map<InjectionKey, Object> f_mapResources = new HashMap<>();

    public Container(Runtime runtime, String sAppName, ModuleRepository repository)
        {
        f_runtime = runtime;
        f_module = repository.loadModule(Constants.ECSTASY_MODULE);
        // f_module.getFileStructure().dump(new PrintWriter(System.out, true));
        f_sAppName = sAppName;
        f_constModule = (ModuleConstant) f_module.getIdentityConstant();

        f_pool = f_module.getConstantPool();
        f_adapter = new Adapter(this);
        f_templates = new TemplateRegistry(this);
        f_heapGlobal = new ObjectHeap(f_pool, f_templates);
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }
        Utils.registerGlobalSignatures(f_pool);

        f_templates.loadNativeTemplates();

        m_contextMain = createServiceContext("main");
        xService.makeHandle(m_contextMain,
            xService.INSTANCE.ensureCanonicalClass(),
            xService.INSTANCE.getCanonicalType());

        initResources();

        try
            {
            // xModule module = (xModule) f_templates.getTemplate(sModule);
            ClassTemplate app = f_templates.getTemplate(f_sAppName);

            // should the application always be a service?
            MethodStructure mtRun = app.getDeclaredMethod("run", TemplateRegistry.VOID, TemplateRegistry.VOID);
            if (mtRun == null)
                {
                System.err.println("Missing run() method for " + f_sAppName);
                return;
                }

            // m_hModule = (ModuleHandle) app.createConstHandle(f_constModule, f_heapGlobal);
            m_hApp = app.createConstHandle(null, app.f_struct.getIdentityConstant());

            m_contextMain.callLater(xFunction.makeHandle(mtRun), Utils.OBJECTS_NONE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_constModule, e);
            }
        }

    protected void initResources()
        {
        // +++ RuntimeClock
        ClassTemplate templateClock = f_templates.getTemplate("Clock");
        if (templateClock != null)
            {
            TypeConstant typeClock = templateClock.getCanonicalType();

            ClassTemplate templateRTClock = f_templates.getTemplate("Clock.RuntimeClock");

            Supplier<ObjectHandle> supplierClock = () ->
                xService.makeHandle(createServiceContext("RuntimeClock"),
                    templateRTClock.ensureCanonicalClass(), typeClock);

            f_mapResources.put(new InjectionKey("runtimeClock", typeClock), supplierClock);
            }

        // +++ Console
        ClassTemplate templateConsole = f_templates.getTemplate("io.Console");
        if (templateConsole != null)
            {
            TypeConstant typeConsole = templateConsole.getCanonicalType();

            ClassTemplate templateRTConsole = f_templates.getTemplate("io.Console.TerminalConsole");

            Supplier<ObjectHandle> supplierConsole = () ->
                xService.makeHandle(createServiceContext("Console"),
                    templateRTConsole.ensureCanonicalClass(), typeConsole);

            f_mapResources.put(new InjectionKey("console", typeConsole), supplierConsole);
            }
        }

    public ServiceContext createServiceContext(String sName)
        {
        ServiceContext context = new ServiceContext(this, sName, f_idProducer.getAndIncrement());

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

    public ModuleHandle getModule()
        {
        return m_hModule;
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
        return "Container: " + f_constModule.getName();
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
    }
