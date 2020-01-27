package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.xLocalClock;
import org.xvm.runtime.template._native.xNanosTimer;
import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import static org.xvm.asm.Op.A_STACK;


/**
 * TODO: for now Container == SecureContainer
 * TODO this is currently building the container like it's the outermost container, not a nested
 *      container (e.g. one that could replace, hide, or add any number of injections)
 */
public class Container
        implements LinkerContext
    {
    public Container(Runtime runtime, String sAppName, ModuleRepository repository,
                     TemplateRegistry templates, ObjectHeap heapGlobal)
        {
        f_runtime  = runtime;
        f_sAppName = sAppName;

        f_moduleRoot = repository.loadModule(Constants.ECSTASY_MODULE);

        f_moduleApp = repository.loadModule(sAppName);
        if (f_moduleApp == null)
            {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
            }

        f_idModule   = f_moduleApp.getIdentityConstant();
        f_templates  = templates;
        f_heapGlobal = heapGlobal;
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        ModuleStructure structModule = (ModuleStructure) f_idModule.getComponent();
        ConstantPool.setCurrentPool(structModule.getConstantPool());

        m_templateModule = f_templates.getTemplate(f_idModule);

        m_contextMain = createServiceContext(f_sAppName, structModule);
        xService.INSTANCE.createServiceHandle(m_contextMain,
            xService.INSTANCE.getCanonicalClass(),
            xService.INSTANCE.getCanonicalType());

        initResources();

        ConstantPool.setCurrentPool(null);
        }

    /**
     * Schedule processing of the specified ServiceContext.
     *
     * @param service the ServiceContext to schedule
     */
    public void schedule(ServiceContext service)
        {
        // TODO: add a container level fair scheduling queue and submit the service there. The
        // container should then follow a similar pattern and push processing of its fair scheduling
        // queue to its parent container which eventually pushes to the runtime. Thus there is a
        // hierarchy of fairness. For now though we just skip over all of this and push the processing
        // directly to the top level runtime.

        f_pendingWorkCount.incrementAndGet();
        f_runtime.submit(() ->
            {
            try
                {
                service.run();
                }
            finally
                {
                f_pendingWorkCount.decrementAndGet();
                }
            });
        }

    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        ConstantPool poolPrev = ConstantPool.getCurrentPool();
        ConstantPool.setCurrentPool(m_templateModule.pool());

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
            FunctionHandle   hFunction  = xRTFunction.makeHandle(chain, 0);

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
        TypeConstant typeClock = f_templates.getTemplate("Clock").getCanonicalType();

        f_mapResources.put(new InjectionKey("clock"     , typeClock), this::ensureDefaultClock);
        f_mapResources.put(new InjectionKey("localClock", typeClock), this::ensureLocalClock);
        f_mapResources.put(new InjectionKey("utcClock"  , typeClock), this::ensureUTCClock);

        // +++ NanosTimer
        xNanosTimer templateRTTimer = (xNanosTimer) f_templates.getTemplate("_native.NanosTimer");
        if (templateRTTimer != null)
            {
            TypeConstant typeTimer = f_templates.getTemplate("Timer").getCanonicalType();

            Function<Frame, ObjectHandle> supplierTimer = (frame) ->
                templateRTTimer.createServiceHandle(
                    createServiceContext("Timer", f_moduleRoot),
                    templateRTTimer.getCanonicalClass(), typeTimer);
            f_mapResources.put(new InjectionKey("timer", typeTimer), supplierTimer);
            }

        // +++ Console
        xTerminalConsole templateRTConsole = (xTerminalConsole) f_templates.getTemplate("_native.TerminalConsole");
        if (templateRTConsole != null)
            {
            TypeConstant typeConsole = f_templates.getTemplate("io.Console").getCanonicalType();

            Function<Frame, ObjectHandle> supplierConsole = (frame) ->
                templateRTConsole.createServiceHandle(
                    createServiceContext("Console", f_moduleRoot),
                    templateRTConsole.getCanonicalClass(), typeConsole);

            f_mapResources.put(new InjectionKey("console", typeConsole), supplierConsole);
            }

        // +++ OSFileStore etc.
        TypeConstant typeFileStore = f_templates.getTemplate("fs.FileStore").getCanonicalType();
        TypeConstant typeDirectory = f_templates.getTemplate("fs.Directory").getCanonicalType();

        f_mapResources.put(new InjectionKey("storage", typeFileStore), this::ensureFileStore);
        f_mapResources.put(new InjectionKey("rootDir", typeDirectory), this::ensureRootDir);
        f_mapResources.put(new InjectionKey("homeDir", typeDirectory), this::ensureHomeDir);
        f_mapResources.put(new InjectionKey("curDir" , typeDirectory), this::ensureCurDir);
        f_mapResources.put(new InjectionKey("tmpDir" , typeDirectory), this::ensureTmpDir);
        }

    protected ObjectHandle ensureDefaultClock(Frame frame)
        {
        // TODO
        return ensureLocalClock(frame);
        }

    protected ObjectHandle ensureLocalClock(Frame frame)
        {
        ObjectHandle hClock = m_hLocalClock;
        if (hClock == null)
            {
            xLocalClock templateRTClock = (xLocalClock) f_templates.getTemplate("_native.LocalClock");
            if (templateRTClock != null)
                {
                TypeConstant typeClock = f_templates.getTemplate("Clock").getCanonicalType();
                m_hLocalClock = hClock = templateRTClock.createServiceHandle(
                    createServiceContext("LocalClock", f_moduleRoot),
                    templateRTClock.getCanonicalClass(), typeClock);
                }
            }

        return hClock;
        }

    protected ObjectHandle ensureUTCClock(Frame frame)
        {
        // TODO
        return ensureDefaultClock(frame);
        }

    protected ObjectHandle ensureOSStorage(Frame frame)
        {
        ObjectHandle hStorage = m_hOSStorage;
        if (hStorage == null)
            {
            ClassTemplate    templateStorage = f_templates.getTemplate("_native.fs.OSStorage");
            ClassComposition clzStorage      = templateStorage.getCanonicalClass();
            MethodStructure  constructor     = templateStorage.f_struct.findConstructor();

            switch (templateStorage.construct(frame, constructor, clzStorage,
                                              null, Utils.OBJECTS_NONE, A_STACK))
                {
                case Op.R_NEXT:
                    hStorage = frame.popStack();
                    break;

                case Op.R_EXCEPTION:
                    break;

                case Op.R_CALL:
                    {
                    Frame frameNext = frame.m_frameNext;
                    frameNext.addContinuation(frameCaller ->
                        {
                        m_hOSStorage = frameCaller.peekStack();
                        return Op.R_NEXT;
                        });
                    return new DeferredCallHandle(frameNext);
                    }

                default:
                    throw new IllegalStateException();
                }
            m_hOSStorage = hStorage;
            }

        return hStorage;
        }

    protected ObjectHandle ensureFileStore(Frame frame)
        {
        ObjectHandle hStore = m_hFileStore;
        if (hStore == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("fileStore").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hFileStore = h);
                }
            }

        return hStore;
        }

    protected ObjectHandle ensureRootDir(Frame frame)
        {
        ObjectHandle hDir = m_hRootDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("rootDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hRootDir = h);
                }
            }

        return hDir;
        }

    protected ObjectHandle ensureHomeDir(Frame frame)
        {
        ObjectHandle hDir = m_hHomeDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("homeDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hHomeDir = h);
                }
            }

        return hDir;
        }

    protected ObjectHandle ensureCurDir(Frame frame)
        {
        ObjectHandle hDir = m_hCurDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("curDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hCurDir = h);
                }
            }

        return hDir;
        }

    protected ObjectHandle ensureTmpDir(Frame frame)
        {
        ObjectHandle hDir = m_hTmpDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame);
            if (hOSStorage != null)
                {
                ClassTemplate    template = f_templates.getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("tmpDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hTmpDir = h);
                }
            }

        return hDir;
        }

    /**
     * Helper method to get a property on the specified target.
     */
    private ObjectHandle getProperty(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                     Consumer<ObjectHandle> consumer)
        {
        if (hTarget instanceof DeferredCallHandle)
            {
            ((DeferredCallHandle) hTarget).addContinuation(frameCaller ->
                {
                ObjectHandle hTargetReal = frameCaller.peekStack();
                int          iResult     = hTargetReal.getTemplate().getPropertyValue(
                                                frameCaller, hTargetReal, idProp, A_STACK);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        consumer.accept(frameCaller.peekStack());
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(frameCaller1 ->
                            {
                            consumer.accept(frameCaller1.peekStack());
                            return Op.R_NEXT;
                            });
                        break;
                    }
                return iResult;
                });
            return hTarget;
            }

        ClassTemplate template = hTarget.getTemplate();
        switch (template.getPropertyValue(frame, hTarget, idProp, A_STACK))
            {
            case Op.R_NEXT:
                {
                ObjectHandle h = frame.popStack();
                consumer.accept(h);
                return h;
                }

            case Op.R_CALL:
                Frame frameNext = frame.m_frameNext;
                frameNext.addContinuation(frameCaller ->
                    {
                    consumer.accept(frameCaller.peekStack());
                    return Op.R_NEXT;
                    });
                return new DeferredCallHandle(frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    public ServiceContext createServiceContext(String sName, ModuleStructure module)
        {
        return new ServiceContext(this, module, sName,
            f_runtime.f_idProducer.getAndIncrement());
        }

    /**
     * Obtain an injected handle for the specified name and type.
     *
     * TODO: need to be able to provide "injectionAttributes"
     *
     * @param frame  the current frame
     * @param sName  the name of the injected object
     * @param type   the type of the injected object
     *
     * @return the injectable handle or null, if the name not resolvable
     *
     */
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type)
        {
        Function<Frame, ObjectHandle> fnResource =
            f_mapResources.get(new InjectionKey(sName, type));

        return fnResource == null ? null : fnResource.apply(frame);
        }

    public ServiceContext getMainContext()
        {
        return m_contextMain;
        }

    public boolean isIdle()
        {
        return f_pendingWorkCount.get() == 0;
        }

    @Override
    public String toString()
        {
        return "Container: " + f_idModule.getName();
        }


    // ----- LinkerContext interface ---------------------------------------------------------------

    @Override
    public boolean isSpecified(String sName)
        {
        switch (sName)
            {
            case "debug":
            case "test":
                return true;
            }

        // TODO
        return false;
        }

    @Override
    public boolean isPresent(IdentityConstant constId)
        {
        if (constId.getModuleConstant().equals(f_moduleRoot.getIdentityConstant()))
            {
            // part of the Ecstasy module
            // TODO
            return true;
            }

        if (constId.getModuleConstant().equals(f_idModule))
            {
            // part of the app module
            // TODO
            return true;
            }

        return false;
        }

    @Override
    public boolean isVersionMatch(ModuleConstant constModule, VersionConstant constVer)
        {
        // TODO
        return true;
        }

    @Override
    public boolean isVersion(VersionConstant constVer)
        {
        // TODO
        return true;
        }


    // ----- inner class: InjectionKey -------------------------------------------------------------
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
    public final TemplateRegistry f_templates;
    public final ObjectHeap       f_heapGlobal;

    protected final ModuleStructure f_moduleRoot;
    protected final ModuleStructure f_moduleApp;
    protected final ModuleConstant  f_idModule;

    // the service context for the container itself
    private ServiceContext m_contextMain;

    // the module
    private final String f_sAppName;
    private ClassTemplate m_templateModule;

    private ObjectHandle m_hLocalClock;
    private ObjectHandle m_hOSStorage;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;

    /**
     * A counter tracking both the number of services which have pending invocations to process
     * and the number of registered Alarms. While this count is above zero the container is
     * considered to still have work to do and won't auto-shutdown.
     */
    public final AtomicLong f_pendingWorkCount = new AtomicLong();

    final Map<InjectionKey, Function<Frame, ObjectHandle>> f_mapResources = new HashMap<>();
    }
