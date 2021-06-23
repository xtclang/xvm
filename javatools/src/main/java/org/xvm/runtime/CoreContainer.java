package org.xvm.runtime;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.runtime.template._native.lang.src.xRTCompiler;

import org.xvm.runtime.template._native.mgmt.xContainerLinker;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;

import org.xvm.runtime.template._native.numbers.xRTRandom;

import org.xvm.runtime.template._native.proxy.xRTWebServerProxy;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;


/**
 * The core container (0).
 */
public class CoreContainer
        extends Container
    {
    public CoreContainer(Runtime runtime, TemplateRegistry templates, CoreConstHeap heapCore,
                         ModuleConstant idModule)
        {
        super(runtime, templates, heapCore, idModule);
        }

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        ensureServiceContext();

        ConstantPool pool = m_idModule.getConstantPool();
        try (var x = ConstantPool.withPool(pool))
            {
            initResources(pool);
            }
        }

    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        try (var x = ConstantPool.withPool(m_idModule.getConstantPool()))
            {
            MethodConstant idMethod = findModuleMethod(sMethodName, ahArg);
            if (idMethod == null)
                {
                System.err.println("Missing: " +  sMethodName + " method for " + m_idModule.getValueString());
                return;
                }

            TypeConstant    typeModule = m_idModule.getType();
            TypeComposition clzModule  = f_templates.resolveClass(typeModule);
            CallChain       chain      = clzModule.getMethodCallChain(idMethod.getSignature());

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle((frame, ah, iReturn) ->
                {
                ObjectHandle hModule = frame.getConstHandle(m_idModule);

                return Op.isDeferred(hModule)
                        ? hModule.proceed(frame, frameCaller ->
                            chain.invoke(frameCaller, frameCaller.popStack(), ahArg, Op.A_IGNORE))
                        : chain.invoke(frame, hModule, ahArg, Op.A_IGNORE);
                });

            m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + m_idModule, e);
            }
        }

    protected void initResources(ConstantPool pool)
        {
        // +++ LocalClock
        TypeConstant typeClock = pool.ensureEcstasyTypeConstant("temporal.Clock");

        addResourceSupplier(new InjectionKey("clock"     , typeClock), this::ensureDefaultClock);
        addResourceSupplier(new InjectionKey("localClock", typeClock), this::ensureLocalClock);
        addResourceSupplier(new InjectionKey("utcClock"  , typeClock), this::ensureUTCClock);

        // +++ NanosTimer
        xNanosTimer templateRTTimer = (xNanosTimer)
                f_templates.getTemplate("_native.temporal.NanosTimer");
        if (templateRTTimer != null)
            {
            TypeConstant typeTimer = pool.ensureEcstasyTypeConstant("temporal.Timer");

            BiFunction<Frame, ObjectHandle, ObjectHandle> supplierTimer = (frame, hOpts) ->
                templateRTTimer.createServiceHandle(
                    createServiceContext("Timer"),
                    templateRTTimer.getCanonicalClass(), typeTimer);
            addResourceSupplier(new InjectionKey("timer", typeTimer), supplierTimer);
            }

        // +++ Console
        xTerminalConsole templateRTConsole = (xTerminalConsole)
                f_templates.getTemplate("_native.TerminalConsole");
        if (templateRTConsole != null)
            {
            TypeConstant typeConsole = pool.ensureEcstasyTypeConstant("io.Console");

            BiFunction<Frame, ObjectHandle, ObjectHandle> supplierConsole = (frame, hOpts) ->
                templateRTConsole.createServiceHandle(
                    createServiceContext("Console"),
                    templateRTConsole.getCanonicalClass(), typeConsole);

            addResourceSupplier(new InjectionKey("console", typeConsole), supplierConsole);
            }

        // +++ Random
        TypeConstant typeRandom = pool.ensureEcstasyTypeConstant("numbers.Random");

        addResourceSupplier(new InjectionKey("rnd"   , typeRandom), this::ensureDefaultRandom);
        addResourceSupplier(new InjectionKey("random", typeRandom), this::ensureDefaultRandom);

        // +++ WebServer
        TypeConstant typeWebServer = pool.ensureEcstasyTypeConstant("proxy.WebServerProxy");

        f_mapResources.put(new InjectionKey("webServer" , typeWebServer), this::ensureWebServerProxy);
        f_mapResources.put(new InjectionKey("proxy" , typeWebServer), this::ensureWebServerProxy);

        // +++ OSFileStore etc.
        TypeConstant typeFileStore = pool.ensureEcstasyTypeConstant("fs.FileStore");
        TypeConstant typeDirectory = pool.ensureEcstasyTypeConstant("fs.Directory");

        addResourceSupplier(new InjectionKey("storage", typeFileStore), this::ensureFileStore);
        addResourceSupplier(new InjectionKey("rootDir", typeDirectory), this::ensureRootDir);
        addResourceSupplier(new InjectionKey("homeDir", typeDirectory), this::ensureHomeDir);
        addResourceSupplier(new InjectionKey("curDir" , typeDirectory), this::ensureCurDir);
        addResourceSupplier(new InjectionKey("tmpDir" , typeDirectory), this::ensureTmpDir);

        // +++ Linker
        TypeConstant typeLinker = pool.ensureEcstasyTypeConstant("mgmt.Container.Linker");
        addResourceSupplier(new InjectionKey("linker" , typeLinker), this::ensureLinker);

        // +++ ModuleRepository
        TypeConstant typeRepo = pool.ensureEcstasyTypeConstant("mgmt.ModuleRepository");
        addResourceSupplier(new InjectionKey("repository" , typeRepo), this::ensureModuleRepository);

        // +++ Compiler
        TypeConstant typeCompiler = pool.ensureEcstasyTypeConstant("lang.src.Compiler");
        addResourceSupplier(new InjectionKey("compiler" , typeCompiler), this::ensureCompiler);

        // ++ xvmProperties
        TypeConstant typeProps = pool.ensureParameterizedTypeConstant(pool.typeMap(),
                                    pool.typeString(), pool.typeString());
        addResourceSupplier(new InjectionKey("properties" , typeProps), this::ensureProperties);
        }

    protected ObjectHandle ensureDefaultClock(Frame frame, ObjectHandle hOpts)
        {
        // TODO
        return ensureLocalClock(frame, hOpts);
        }

    protected ObjectHandle ensureLocalClock(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hClock = m_hLocalClock;
        if (hClock == null)
            {
            xLocalClock templateRTClock = (xLocalClock)
                    f_templates.getTemplate("_native.temporal.LocalClock");
            if (templateRTClock != null)
                {
                TypeConstant typeClock =
                        frame.poolContext().ensureEcstasyTypeConstant("temporal.Clock");
                m_hLocalClock = hClock = templateRTClock.createServiceHandle(
                    createServiceContext("LocalClock"),
                    templateRTClock.getCanonicalClass(), typeClock);
                }
            }

        return hClock;
        }

    protected ObjectHandle ensureUTCClock(Frame frame, ObjectHandle hOpts)
        {
        // TODO
        return ensureDefaultClock(frame, hOpts);
        }

    protected ObjectHandle ensureOSStorage(Frame frame, ObjectHandle hOpts)
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

    protected ObjectHandle ensureDefaultRandom(Frame frame, ObjectHandle hOpts)
        {
        long lSeed = hOpts instanceof JavaLong   ? ((JavaLong) hOpts).getValue() :
                     hOpts instanceof IntNHandle ? ((IntNHandle) hOpts).getValue().getLong() :
                     0;
        if (lSeed != 0)
            {
            xRTRandom templateRTRandom = (xRTRandom) f_templates.getTemplate("_native.numbers.RTRandom");
            if (templateRTRandom != null)
                {
                TypeConstant  typeRandom = frame.poolContext().ensureEcstasyTypeConstant("numbers.Random");
                ServiceHandle hRnd       = templateRTRandom.createRandomHandle(
                    createServiceContext("Random"),
                    templateRTRandom.getCanonicalClass(), typeRandom, lSeed);
                return hRnd;
                }
            }

        ObjectHandle hRnd = m_hRandom;
        if (hRnd == null)
            {
            xRTRandom templateRTRandom = (xRTRandom)
                    f_templates.getTemplate("_native.numbers.RTRandom");
            if (templateRTRandom != null)
                {
                TypeConstant typeRandom =
                        frame.poolContext().ensureEcstasyTypeConstant("numbers.Random");
                m_hRandom = hRnd = templateRTRandom.createRandomHandle(
                    createServiceContext("Random"),
                    templateRTRandom.getCanonicalClass(), typeRandom, 0l);
                }
            }

        return hRnd;
        }

    protected ObjectHandle ensureWebServerProxy(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hServer = m_hWebServer;
        if (hServer == null)
            {
            xRTWebServerProxy template = (xRTWebServerProxy) f_templates.getTemplate("_native.proxy.RTWebServerProxy");
            if (template != null)
                {
                TypeConstant typeWebServer = frame.poolContext().ensureEcstasyTypeConstant("proxy.WebServerProxy");
                m_hWebServer = hServer = template.createServiceHandle(
                    createServiceContext("WebServerProxy"),
                    template.getCanonicalClass(), typeWebServer);
                }
            }

        return hServer;
        }

    protected ObjectHandle ensureFileStore(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hStore = m_hFileStore;
        if (hStore == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
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

    protected ObjectHandle ensureRootDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hRootDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
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

    protected ObjectHandle ensureHomeDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hHomeDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
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

    protected ObjectHandle ensureCurDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hCurDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
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

    protected ObjectHandle ensureTmpDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hTmpDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
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

    protected ObjectHandle ensureModuleRepository(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hRepository = m_hRepository;
        if (hRepository == null)
            {
            xCoreRepository templateRTRepository =
                    (xCoreRepository) f_templates.getTemplate("_native.mgmt.CoreRepository");
            if (templateRTRepository != null)
                {
                m_hRepository = hRepository = templateRTRepository.makeHandle();
                }
            }

        return hRepository;
        }

    protected ObjectHandle ensureLinker(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hLinker = m_hLinker;
        if (hLinker == null)
            {
            xContainerLinker templateRTLinker = (xContainerLinker)
                    f_templates.getTemplate("_native.mgmt.ContainerLinker");
            if (templateRTLinker != null)
                {
                ConstantPool pool       = frame.poolContext();
                TypeConstant typeLinker = pool.ensureUnionTypeConstant(
                        pool.ensureEcstasyTypeConstant("mgmt.Container.Linker"),
                        pool.typeService());
                m_hLinker = hLinker = templateRTLinker.createServiceHandle(
                    createServiceContext("Linker"),
                    templateRTLinker.getCanonicalClass(), typeLinker);
                }
            }

        return hLinker;
        }

    protected ObjectHandle ensureCompiler(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hCompiler = m_hCompiler;
        if (hCompiler == null)
            {
            xRTCompiler templateRTCompiler =
                    (xRTCompiler) f_templates.getTemplate("_native.lang.src.RTCompiler");
            if (templateRTCompiler != null)
                {
                TypeConstant typeCompiler =
                        frame.poolContext().ensureEcstasyTypeConstant("lang.src.Compiler");
                m_hCompiler = hCompiler = templateRTCompiler.createServiceHandle(
                    createServiceContext("Compiler"),
                    templateRTCompiler.getCanonicalClass(), typeCompiler);
                }
            }

        return hCompiler;
        }

    protected ObjectHandle ensureProperties(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hProps = m_hProperties;
        if (hProps == null)
            {
            List<StringHandle> listKeys = new ArrayList<>();
            List<StringHandle> listVals = new ArrayList<>();
            for (String sKey : (Set<String>) (Set) System.getProperties().keySet())
                {
                if (sKey.startsWith("xvm."))
                    {
                    String sVal = System.getProperty(sKey);
                    if (sVal != null)
                        {
                        listKeys.add(xString.makeHandle(sKey.substring(4)));
                        listVals.add(xString.makeHandle(sVal));
                        }
                    }
                }
            ObjectHandle haKeys   = xArray.makeStringArrayHandle(listKeys.toArray(Utils.STRINGS_NONE));
            ObjectHandle haValues = xArray.makeStringArrayHandle(listVals.toArray(Utils.STRINGS_NONE));

            ConstantPool pool    = frame.poolContext();
            TypeConstant typeMap = pool.ensureParameterizedTypeConstant(
                                    pool.ensureEcstasyTypeConstant("collections.ListMap"),
                                    pool.typeString(),
                                    pool.typeString());

            switch (Utils.constructListMap(frame,
                            f_templates.resolveClass(typeMap), haKeys, haValues, Op.A_STACK))
                {
                case Op.R_NEXT:
                    hProps = frame.popStack();
                    break;

                case Op.R_EXCEPTION:
                    break;

                case Op.R_CALL:
                    {
                    Frame frameNext = frame.m_frameNext;
                    frameNext.addContinuation(frameCaller ->
                        {
                        m_hProperties = frameCaller.peekStack();
                        return Op.R_NEXT;
                        });
                    return new DeferredCallHandle(frameNext);
                    }

                default:
                    throw new IllegalStateException();
                }
            m_hProperties = hProps;
            }

        return hProps;
        }

    /**
     * Helper method to get a property on the specified target.
     */
    private ObjectHandle getProperty(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                     Consumer<ObjectHandle> consumer)
        {
        TypeConstant typeRevealed = idProp.getType();
        if (hTarget instanceof DeferredCallHandle)
            {
            ((DeferredCallHandle) hTarget).addContinuation(frameCaller ->
                {
                ObjectHandle hTargetReal = frameCaller.popStack();
                int          iResult     = hTargetReal.getTemplate().getPropertyValue(
                                                frameCaller, hTargetReal, idProp, Op.A_STACK);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        {
                        ObjectHandle h = frameCaller.popStack().
                                maskAs(NATIVE_CONTAINER, typeRevealed);
                        frameCaller.pushStack(h);
                        consumer.accept(h);
                        break;
                        }

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(frameCaller1 ->
                            {
                            ObjectHandle h = frameCaller1.popStack().
                                    maskAs(NATIVE_CONTAINER, typeRevealed);
                            consumer.accept(h);
                            return frameCaller1.pushStack(h);
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
                ObjectHandle h = frame.popStack().maskAs(NATIVE_CONTAINER, typeRevealed);
                consumer.accept(h);
                return h;
                }

            case Op.R_CALL:
                Frame frameNext = frame.m_frameNext;
                frameNext.addContinuation(frameCaller ->
                    {
                    ObjectHandle h = frameCaller.popStack().maskAs(NATIVE_CONTAINER, typeRevealed);
                    consumer.accept(h);
                    return frameCaller.pushStack(h);
                    });
                return new DeferredCallHandle(frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Fictional container (-1), whose only purpose is to serve as an owner of native injections.
     */
    static Container NATIVE_CONTAINER = new Container(null, null, null, null)
        {
        @Override
        public String toString()
            {
            return "Primordial container";
            }
        };


    // ----- data fields ---------------------------------------------------------------------------

    private ObjectHandle m_hLocalClock;
    private ObjectHandle m_hRandom;
    private ObjectHandle m_hWebServer;
    private ObjectHandle m_hOSStorage;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;
    private ObjectHandle m_hLinker;
    private ObjectHandle m_hRepository;
    private ObjectHandle m_hCompiler;
    private ObjectHandle m_hProperties;
    }
