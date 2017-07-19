package org.xvm.proto;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xModule.ModuleHandle;
import org.xvm.proto.template.xRuntimeClock;
import org.xvm.proto.template.xService;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.function.Supplier;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
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

    protected void initResources()
        {
        ClassTemplate templateClock = f_types.getTemplate("Clock");

        Supplier<ObjectHandle> supplierClock = () ->
            {
            ServiceContext ctxClock = createServiceContext("RuntimeClock");
            return xService.makeHandle(ctxClock,
                    xRuntimeClock.INSTANCE.f_clazzCanonical,
                    templateClock.f_clazzCanonical.ensurePublicType());
            };

        ClassTypeConstant typeClock = f_pool.ensureClassTypeConstant(
                f_pool.ensureClassConstant(f_constModule, "Clock"), null);
        f_mapResources.put(new InjectionKey("runtimeClock", typeClock), supplierClock);
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        // evert native class that has an INSTANCE static variable needs to be here
        f_types.getTemplate("Object");
        f_types.getTemplate("String");
        f_types.getTemplate("Service");
        f_types.getTemplate("Function");
        f_types.getTemplate("Exception");
        f_types.getTemplate("Ref");
        f_types.getTemplate("Class");
        f_types.getTemplate("Module");
        f_types.getTemplate("annotations.FutureRef");
        f_types.getTemplate("collections.Array");
        f_types.getTemplate("collections.Tuple");
        f_types.getTemplate("types.Method");
        f_types.getTemplate("Clock.RuntimeClock");

        m_contextMain = createServiceContext("main");
        xService.makeHandle(m_contextMain,
                xService.INSTANCE.f_clazzCanonical,
                xService.INSTANCE.f_clazzCanonical.ensurePublicType());

        initResources();

        try
            {
            // xModule module = (xModule) f_types.getTemplate(sModule);
            ClassTemplate app = f_types.getTemplate(f_sAppName);

            MethodStructure mtRun = app.getMethod("run", ClassTemplate.VOID, ClassTemplate.VOID);
            if (mtRun == null)
                {
                throw new IllegalArgumentException("Missing run() method for " + f_sAppName);
                }

            // m_hModule = (ModuleHandle) app.createConstHandle(f_constModule, f_heapGlobal);
            m_hApp = app.createConstHandle(app.f_struct.getIdentityConstant(), f_heapGlobal);

            m_contextMain.callLater(xFunction.makeHandle(mtRun), new ObjectHandle[]{m_hApp});
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_constModule, e);
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
    // clzParent - the class of the object for which the property needs to be injected
    //             (may need to be used to resolve a composite type)
    // TODO: need an "override" name or better yet "injectionAttributes"
    public ObjectHandle getInjectable(TypeComposition clzParent, PropertyStructure property)
        {
        InjectionKey key = new InjectionKey(property.getName(), f_adapter.resolveType(property));
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
        public final ClassTypeConstant f_type;

        public InjectionKey(String sName, ClassTypeConstant typeName)
            {
            f_sName = sName;
            f_type = typeName;
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
