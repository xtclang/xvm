package org.xvm.proto;

import org.xvm.asm.ConstantPool;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction;
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
    final public ServiceContext f_contextZero;
    Set<ServiceContext> m_setServices = new HashSet<>();

    public Container()
        {
        f_constantPoolAdapter = new ConstantPoolAdapter();
        f_types = new TypeSet(f_constantPoolAdapter);
        f_heapGlobal = new ObjectHeap(f_constantPoolAdapter, f_types);

        f_contextZero = new ContextZero(this);
        f_contextZero.start(null, "main");

        init();
        }

    protected void init()
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

    public ServiceHandle startService(xService service, String sName)
        {
        ContextZero.StartService msg = new ContextZero.StartService();
        msg.service = service;
        msg.sName = sName;

        f_contextZero.m_daemon.add(msg);
        synchronized (msg)
            {
            try
                {
                while (msg.hService == null)
                    {
                    msg.wait(1000);
                    }
                }
            catch (InterruptedException e)
                {
                throw new RuntimeException(e);
                }

            }
        return msg.hService;
        }

    public void runMethod(ServiceHandle hService, String sMethodSig, ObjectHandle[] ahArg)
        {
        ContextZero.RunMethod msg = new ContextZero.RunMethod();
        msg.hService = hService;
        msg.sMethodSig = sMethodSig;
        msg.ahArg = ahArg;

        f_contextZero.m_daemon.add(msg);
        }

    public static class ContextZero
            extends ServiceContext
        {
        protected ContextZero(Container container)
            {
            super(container);
            }

        protected static class StartService
                implements Message
            {
            protected xService service;
            protected String sName;
            protected volatile ServiceHandle hService;

            @Override
            public void process(ServiceContext context)
                {
                ServiceHandle handle = service.createService(sName);
                service.start(handle);

                synchronized (this)
                    {
                    hService = handle;
                    notify();
                    }
                }
            }

        protected static class RunMethod
                implements Message
            {
            protected ServiceHandle hService;
            protected String sMethodSig;
            protected ObjectHandle[] ahArg;

            @Override
            public void process(ServiceContext context)
                {
                TypeCompositionTemplate template = hService.f_clazz.f_template;

                int nMethodId = context.f_constantPool.getMethodConstId(template.f_sName, sMethodSig);

                ConstantPool.MethodConstant constMethod = context.f_constantPool.getMethodConstant(nMethodId);

                TypeCompositionTemplate.MethodTemplate method = template.getMethodTemplate(constMethod);

                try
                    {
                    System.out.println("\n### Calling " + method + " ###");

                    Frame frame = context.createFrame(null, null, hService, new ObjectHandle[method.m_cVars]);

                    ObjectHandle.ExceptionHandle hException = xFunction.makeAsyncHandle(method).
                            call(frame, new ObjectHandle[]{hService}, Utils.OBJECTS_NONE);

                    if (hException != null)
                        {
                        System.out.println("Function " + method + " threw unhandled " + hException);
                        }
                    }
                catch (Exception e)
                    {
                    System.out.println("Failed to execute " + method);
                    e.printStackTrace(System.out);
                    }

                }
            }
        }
    }
