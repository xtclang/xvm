package org.xvm.runtime;


import java.util.function.Consumer;
import java.util.function.Function;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;
import org.xvm.runtime.template._native.xLocalClock;
import org.xvm.runtime.template._native.xNanosTimer;
import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.runtime.template.xService;


/**
 * The core container (-1).
 */
public class CoreContainer
        extends Container
    {
    public CoreContainer(Runtime runtime, String sAppName, ModuleRepository repository,
                         TemplateRegistry templates, ObjectHeap heapGlobal)
        {
        super(runtime, sAppName, templates, heapGlobal);

        ModuleStructure moduleApp = repository.loadModule(sAppName);
        if (moduleApp == null)
            {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
            }

        FileStructure container = templates.getFileStructure();
        container.merge(moduleApp);

        m_idModule = (ModuleConstant) container.getChild(moduleApp.getName()).getIdentityConstant();
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        ConstantPool pool = m_idModule.getConstantPool();
        ConstantPool.setCurrentPool(pool);

        m_contextMain = createServiceContext(m_idModule.getName(), pool);
        xService.INSTANCE.createServiceHandle(m_contextMain,
            xService.INSTANCE.getCanonicalClass(),
            xService.INSTANCE.getCanonicalType());

        initResources();

        ConstantPool.setCurrentPool(null);
        }

    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        ConstantPool poolPrev = ConstantPool.getCurrentPool();
        ConstantPool.setCurrentPool(m_idModule.getConstantPool());

        try
            {
            TypeInfo infoApp = m_idModule.getType().ensureTypeInfo();
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
                System.err.println("Missing: " +  sMethodName + " method for " + m_idModule.getValueString());
                return;
                }

            TypeConstant     typeModule = m_idModule.getType();
            ClassComposition clzModule  = f_templates.resolveClass(typeModule);
            CallChain        chain      = clzModule.getMethodCallChain(idMethod.getSignature());
            FunctionHandle   hFunction  = xRTFunction.makeHandle(chain, 0);

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle((frame, ah, iReturn) ->
                {
                ObjectHandle hModule = frame.getConstHandle(m_idModule);

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
            throw new RuntimeException("failed to run: " + m_idModule, e);
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
                    createServiceContext("Timer", m_idModule.getConstantPool()),
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
                    createServiceContext("Console", m_idModule.getConstantPool()),
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
                    createServiceContext("LocalClock", m_idModule.getConstantPool()),
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
            MethodStructure  constructor     = templateStorage.getStructure().findConstructor();

            switch (templateStorage.construct(frame, constructor, clzStorage,
                                              null, Utils.OBJECTS_NONE, Op.A_STACK))
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
                                                frameCaller, hTargetReal, idProp, Op.A_STACK);
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
        switch (template.getPropertyValue(frame, hTarget, idProp, Op.A_STACK))
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

    // ----- data fields ---------------------------------------------------------------------------

    private ObjectHandle m_hLocalClock;
    private ObjectHandle m_hOSStorage;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;
    }
