package org.xvm.proto;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xModule;
import org.xvm.proto.template.xModule.ModuleHandle;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xService;
import org.xvm.proto.template.xService.ServiceHandle;

import java.util.HashSet;
import java.util.Set;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
 */
public class Container
    {
    final public TypeSet f_types;
    final public ConstantPoolAdapter f_constantPoolAdapter;
    final public ObjectHeap f_heapGlobal;
    final public ServiceContext f_contextMain; // the service context for the container itself

    protected ModuleConstant m_constModule;

    Set<ServiceContext> m_setServices = new HashSet<>();

    public Container()
        {
        f_constantPoolAdapter = new ConstantPoolAdapter();
        f_types = new TypeSet(f_constantPoolAdapter);
        f_heapGlobal = new ObjectHeap(f_constantPoolAdapter, f_types);
        f_contextMain = new ServiceContext(this);

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

        types.addTemplate(new xObject(types));
        types.addTemplate(new xException(types));
        types.addTemplate(new xService(types));

        // container.m_typeSet.dumpTemplates();
        }

    public ServiceContext createContext()
        {
        ServiceContext ctx = new ServiceContext(this);
        m_setServices.add(ctx);
        return ctx;
        }

    public void start(ModuleConstant constModule)
        {
        if (m_constModule != null)
            {
            throw new IllegalStateException("Already started");
            }

        m_constModule = constModule;

        try
            {
            String sModule = m_constModule.getQualifiedName();
            xModule module = (xModule) f_types.ensureTemplate(sModule);

            InvocationTemplate mtRun = module.getMethodTemplate("run", xModule.VOID, xModule.VOID);
            if (mtRun == null)
                {
                throw new IllegalArgumentException("Missing run() method for " + m_constModule);
                }

            ModuleHandle hModule = (ModuleHandle) module.createConstHandle(m_constModule);
            FunctionHandle hFunction = xFunction.makeHandle(mtRun);

            TypeComposition clzService = xService.INSTANCE.f_clazzCanonical;
            ServiceHandle hService = new ServiceHandle(
                    clzService, clzService.ensurePublicType(), f_contextMain);

            f_contextMain.start(hService, "main");

            f_contextMain.sendInvokeRequest(f_contextMain, hFunction, new ObjectHandle[]{hModule}, 0)
                .whenComplete((ah, x) ->
                {
                if (x != null)
                    {
                    System.out.println("Unhandled exception " + x);
                    System.out.println(((ObjectHandle.ExceptionHandle.WrapperException) x).getExceptionHandle().toString());
                    }
                });
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + m_constModule, e);
            }
        }

    public boolean isRunning()
        {
        // TODO: consider all services
        return f_contextMain.isContended();
        }
    }
