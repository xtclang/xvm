package org.xvm.proto;

import com.sun.org.apache.xpath.internal.operations.Mod;
import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

import org.xvm.proto.template.xClock;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xModule;
import org.xvm.proto.template.xModule.ModuleHandle;
import org.xvm.proto.template.xRuntimeClock;
import org.xvm.proto.template.xService;

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
    final public ConstantPoolAdapter f_constantPoolAdapter;
    final public ObjectHeap f_heapGlobal;

    final protected ModuleConstant f_constModule;

    // service id producer
    final AtomicInteger f_idProducer = new AtomicInteger();

    // service context map (concurrent set)
    final Map<ServiceContext, ServiceContext> f_mapServices = new ConcurrentHashMap<>();

    // the service context for the container itself
    private ServiceContext m_contextMain;

    // the module
    private ModuleHandle m_hModule;

    // the values are: ObjectHandle | Supplier<ObjectHandle>
    final Map<InjectionKey, Object> f_mapResources = new HashMap<>();

    public Container(Runtime runtime, String sName)
        {
        f_runtime = runtime;
        f_constantPoolAdapter = new ConstantPoolAdapter();
        f_types = new TypeSet(f_constantPoolAdapter);
        f_heapGlobal = new ObjectHeap(f_constantPoolAdapter, f_types);
        f_constModule = f_constantPoolAdapter.ensureModuleConstant(sName);

        initTypes();
        }

    protected void initTypes()
        {
        TypeSet types = f_types;

        // depth first traversal starting with Object

        // typedef Int64   Int;
        // typedef UInt64  UInt;

        types.addAlias("x:Int", "x:Int64");
        types.addAlias("x:UInt", "x:UInt64");

        types.ensureTemplate("x:Type"); // this must come first (see TCT constructor)
        types.ensureTemplate("x:Object");
        types.ensureTemplate("x:Exception");
        types.ensureTemplate("x:Service");
        types.ensureTemplate("x:FutureRef");
        types.ensureTemplate("x:AtomicRef");

        // container.m_typeSet.dumpTemplates();
        }

    protected void initResources()
        {
        f_types.ensureTemplate("x:RuntimeClock");

        Supplier<ObjectHandle> supplierClock = () ->
            {
            ServiceContext ctxClock = createServiceContext("RuntimeClock");
            return xService.makeHandle(ctxClock,
                    xRuntimeClock.INSTANCE.f_clazzCanonical,
                    xClock.INSTANCE.f_clazzCanonical.ensurePublicType());
            };
        f_mapResources.put(new InjectionKey("runtimeClock", TypeName.parseName("x:Clock")), supplierClock);
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
    public ObjectHandle getInjectable(TypeComposition clzParent, PropertyTemplate property)
        {
        InjectionKey key = new InjectionKey(property.f_sName, property.f_typeName);
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

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        m_contextMain = createServiceContext("main");
        xService.makeHandle(m_contextMain,
                xService.INSTANCE.f_clazzCanonical,
                xService.INSTANCE.f_clazzCanonical.ensurePublicType());

        initResources();

        try
            {
            String sModule = f_constModule.getName();
            xModule module = (xModule) f_types.ensureTemplate(sModule);

            InvocationTemplate mtRun = module.getMethodTemplate("run", xModule.VOID, xModule.VOID);
            if (mtRun == null)
                {
                throw new IllegalArgumentException("Missing run() method for " + f_constModule);
                }

            m_hModule = (ModuleHandle) module.createConstHandle(f_constModule, f_heapGlobal);

            m_contextMain.callLater(xFunction.makeHandle(mtRun), new ObjectHandle[]{m_hModule});
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_constModule, e);
            }
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
        public final TypeName f_typeName;

        public InjectionKey(String sName, TypeName typeName)
            {
            f_sName = sName;
            f_typeName = typeName;
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
                   Objects.equals(this.f_typeName, that.f_typeName);
            }

        @Override
        public int hashCode()
            {
            return f_sName.hashCode() + f_typeName.hashCode();
            }

        @Override
        public String toString()
            {
            return "Key: " + f_sName + ", " + f_typeName;
            }
        }
    }
