package org.xvm.proto;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xModule;
import org.xvm.proto.template.xModule.ModuleHandle;

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

        types.ensureTemplate("x:Type"); // this must come first (see TCT constructor)
        types.ensureTemplate("x:Object");
        types.ensureTemplate("x:Exception");
        types.ensureTemplate("x:Service");
        types.ensureTemplate("x:FutureRef");
        types.ensureTemplate("x:AtomicRef");

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

            ModuleHandle hModule = (ModuleHandle) module.createConstHandle(m_constModule, f_heapGlobal);
            FunctionHandle hFunction = xFunction.makeHandle(mtRun);

            f_contextMain.start("main");

            f_contextMain.sendInvoke1Request(f_contextMain, hFunction, new ObjectHandle[]{hModule}, 0)
                .whenComplete((ah, x) ->
                    {
                    if (x != null)
                        {
                        Utils.log("Unhandled exception " + x);
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
