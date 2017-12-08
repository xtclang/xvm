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

import org.xvm.runtime.template.Clock.xRuntimeClock;
import org.xvm.runtime.template.Service;
import org.xvm.runtime.template.Function;
import org.xvm.runtime.template.xModule.ModuleHandle;

import org.xvm.runtime.template.io.Console.xTerminalConsole;


/**
 * TODO: for now Container == SecureContainer
 */
public class Container
    {
    final public Runtime f_runtime;
    final public TypeSet f_types;
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
        f_types = new TypeSet(this);
        f_heapGlobal = new ObjectHeap(f_pool, f_types);
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }
        Utils.registerGlobalSignatures(f_pool);

        f_types.getTemplate("Object");

        // the native interfaces are pseudo-classes (also with INSTANCE static variable)
        f_types.initNativeInterfaces();

        // every native class that has an INSTANCE static variable may need to be here
        f_types.getTemplate("String");
        f_types.getTemplate("Boolean");
        f_types.getTemplate("Module");
        f_types.getTemplate("Class");
        f_types.getTemplate("Type");
        f_types.getTemplate("Ordered");
        f_types.getTemplate("types.Method");
        f_types.getTemplate("types.Property");

        m_contextMain = createServiceContext("main");
        Service.makeHandle(m_contextMain,
                Service.INSTANCE.f_clazzCanonical,
                Service.INSTANCE.f_clazzCanonical.ensurePublicType());

        initResources();

        try
            {
            // xModule module = (xModule) f_types.getTemplate(sModule);
            ClassTemplate app = f_types.getTemplate(f_sAppName);

            MethodStructure mtRun = app.getDeclaredMethod("run", TypeSet.VOID, TypeSet.VOID);
            if (mtRun == null)
                {
                System.err.println("Missing run() method for " + f_sAppName);
                return;
                }

            // m_hModule = (ModuleHandle) app.createConstHandle(f_constModule, f_heapGlobal);
            m_hApp = app.createConstHandle(null, app.f_struct.getIdentityConstant());

            m_contextMain.callLater(Function.makeHandle(mtRun), Utils.OBJECTS_NONE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_constModule, e);
            }
        }

    protected void initResources()
        {
        // +++ RuntimeClock
        ClassTemplate templateClock = f_types.getTemplate("Clock");
        if (templateClock != null)
            {
            f_types.getTemplate("Clock.RuntimeClock"); // to init xRuntimeClock.INSTANCE

            Supplier<ObjectHandle> supplierClock = () ->
                {
                ServiceContext ctxClock = createServiceContext("RuntimeClock");
                return Service.makeHandle(ctxClock,
                        xRuntimeClock.INSTANCE.f_clazzCanonical,
                        templateClock.f_clazzCanonical.ensurePublicType());
                };

            f_mapResources.put(
                    new InjectionKey("runtimeClock", templateClock.f_clazzCanonical), supplierClock);
            }

        // +++ Console
        ClassTemplate templateConsole = f_types.getTemplate("io.Console");
        if (templateConsole != null)
            {
            f_types.getTemplate("io.Console.TerminalConsole"); // to init xTerminalConsole.INSTANCE

            Supplier<ObjectHandle> supplierConsole = () ->
                {
                ServiceContext ctxConsole = createServiceContext("Console");
                return Service.makeHandle(ctxConsole,
                        xTerminalConsole.INSTANCE.f_clazzCanonical,
                        templateConsole.f_clazzCanonical.ensurePublicType());
                };

            f_mapResources.put(
                    new InjectionKey("console", templateConsole.f_clazzCanonical), supplierConsole);
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
    public ObjectHandle getInjectable(String sName, TypeComposition clz)
        {
        InjectionKey key = new InjectionKey(sName, clz);
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

    public boolean isIdle()
        {
        for (ServiceContext context : f_mapServices.keySet())
            {
            if (context.isContended())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public String toString()
        {
        return "Container: " + f_constModule.getName();
        }

    public static class InjectionKey
        {
        public final String f_sName;
        public final TypeComposition f_clazz;

        public InjectionKey(String sName, TypeComposition clazz)
            {
            f_sName = sName;
            f_clazz = clazz;
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
                   Objects.equals(this.f_clazz, that.f_clazz);
            }

        @Override
        public int hashCode()
            {
            return f_sName.hashCode() + f_clazz.hashCode();
            }

        @Override
        public String toString()
            {
            return "Key: " + f_sName + ", " + f_clazz;
            }
        }
    }
