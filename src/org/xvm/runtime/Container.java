package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.template.xModule;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xModule.ModuleHandle;


/**
 * TODO: for now Container == SecureContainer
 */
public class Container
    {
    public final Runtime f_runtime;
    public final ModuleRepository f_repository;
    public final TemplateRegistry f_templates;
    public final Adapter f_adapter;
    public final ObjectHeap f_heapGlobal;

    final protected ModuleStructure f_moduleRoot;
    final protected ModuleConstant f_constModule;

    // service context map (concurrent set)
    final Map<ServiceContext, ServiceContext> f_mapServices = new ConcurrentHashMap<>();

    // the service context for the container itself
    private ServiceContext m_contextMain;

    // the module
    private final String f_sAppName;
    private ModuleHandle m_hModule;
    private ClassTemplate m_app; // replace with m_hModule

    // the values are: ObjectHandle | Supplier<ObjectHandle>
    final Map<InjectionKey, Object> f_mapResources = new HashMap<>();

    public Container(Runtime runtime, String sAppName, ModuleRepository repository)
        {
        f_runtime = runtime;
        f_sAppName = sAppName;

        f_repository = repository;
        f_moduleRoot = repository.loadModule(Constants.ECSTASY_MODULE);

        f_templates = new TemplateRegistry(f_moduleRoot);
        f_adapter = f_templates.f_adapter;
        f_heapGlobal = new ObjectHeap(f_moduleRoot.getConstantPool(), f_templates);

        if (sAppName.equals("TestApp"))
            {
            // TODO: remove -- but for now TestApp is a part of the "system"
            f_constModule = f_moduleRoot.getIdentityConstant();
            }
        else
            {
            ModuleStructure module = repository.loadModule(sAppName);
            if (module == null)
                {
                throw new IllegalStateException("Module \"" + sAppName + "\" cannot not loaded");
                }
            f_constModule = module.getIdentityConstant();
            }
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

        ModuleStructure structModule = (ModuleStructure) f_constModule.getComponent();
        if (f_sAppName.equals("TestApp"))
            {
            // TODO: remove -- but for now TestApp is a part of the "system"
            m_app = f_templates.getTemplate(f_sAppName);
            assert structModule == f_moduleRoot;
            }
        else
            {
            m_app = f_templates.getTemplate(f_constModule);
            m_hModule = ((xModule) m_app).ensureModuleHandle(f_constModule);
            }

        ConstantPool.setCurrentPool(structModule.getConstantPool());

        m_contextMain = createServiceContext(f_sAppName, structModule);
        xService.makeHandle(m_contextMain,
            xService.INSTANCE.getCanonicalClass(),
            xService.INSTANCE.getCanonicalType());

        initResources();

        ConstantPool.setCurrentPool(null);
        }

    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        // REVIEW GG
        ConstantPool poolPrev = ConstantPool.getCurrentPool();
        ConstantPool.setCurrentPool(f_moduleRoot.getConstantPool());

        try
            {
            TypeInfo infoApp = m_app.getCanonicalType().ensureTypeInfo();
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
                System.err.println("Missing: " +  sMethodName + " method for " + m_app);
                return;
                }

            FunctionHandle hFunction;

            if (m_hModule == null)
                {
                // TODO: remove along with the TestApp
                MethodBody[] aBody = infoApp.getOptimizedMethodChain(idMethod);
                hFunction = xFunction.makeHandle(aBody[0].getMethodStructure());
                }
            else
                {
                CallChain chain = m_hModule.getComposition().
                    getMethodCallChain(idMethod.getSignature());
                hFunction = xFunction.makeHandle(chain, 0);
                hFunction = hFunction.bindTarget(m_hModule);
                }
            m_contextMain.callLater(hFunction, ahArg);
            }
        catch (Exception e)
            {
            // REVIEW GG
            ConstantPool.setCurrentPool(poolPrev);
            throw new RuntimeException("failed to run: " + f_constModule, e);
            }

        // REVIEW GG
        ConstantPool.setCurrentPool(poolPrev);
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
                xService.makeHandle(createServiceContext("RuntimeClock", f_moduleRoot),
                    templateRTClock.getCanonicalClass(), typeClock);

            f_mapResources.put(new InjectionKey("runtimeClock", typeClock), supplierClock);
            }

        // +++ Console
        ClassTemplate templateConsole = f_templates.getTemplate("io.Console");
        if (templateConsole != null)
            {
            TypeConstant typeConsole = templateConsole.getCanonicalType();

            ClassTemplate templateRTConsole = f_templates.getTemplate("io.Console.TerminalConsole");

            Supplier<ObjectHandle> supplierConsole = () ->
                xService.makeHandle(createServiceContext("Console", f_moduleRoot),
                    templateRTConsole.getCanonicalClass(), typeConsole);

            f_mapResources.put(new InjectionKey("console", typeConsole), supplierConsole);
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
