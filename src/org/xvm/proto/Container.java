package org.xvm.proto;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xModule;
import org.xvm.proto.template.xModule.ModuleHandle;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    // context id producer
    final AtomicInteger f_idProducer = new AtomicInteger();
    // service context -> id
    final Map<ServiceContext, ServiceContext> f_mapServices = new ConcurrentHashMap<>();

    private ServiceContext m_contextMain; // the service context for the container itself

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

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        m_contextMain = createServiceContext("main");

        try
            {
            String sModule = f_constModule.getName();
            xModule module = (xModule) f_types.ensureTemplate(sModule);

            InvocationTemplate mtRun = module.getMethodTemplate("run", xModule.VOID, xModule.VOID);
            if (mtRun == null)
                {
                throw new IllegalArgumentException("Missing run() method for " + f_constModule);
                }

            FunctionHandle hFunction = xFunction.makeHandle(mtRun);
            ModuleHandle hModule = (ModuleHandle) module.createConstHandle(f_constModule, f_heapGlobal);

            m_contextMain.callLater(hFunction, new ObjectHandle[]{hModule});
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_constModule, e);
            }
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
    }
