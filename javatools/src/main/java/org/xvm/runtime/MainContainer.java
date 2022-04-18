package org.xvm.runtime;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.runtime.template._native.lang.src.xRTCompiler;

import org.xvm.runtime.template._native.mgmt.xContainerLinker;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;

import org.xvm.runtime.template._native.numbers.xRTRandom;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;

import org.xvm.runtime.template._native.web.xRTServer;


/**
 * The main container (zero) associated with the main module.
 */
public class MainContainer
        extends Container
    {
    public MainContainer(Runtime runtime, TemplateRegistry templates, CoreConstHeap heapCore,
                         ModuleConstant idModule)
        {
        super(runtime, null, templates, heapCore, idModule);
        }


    // ----- Container methods ---------------------------------------------------------------------

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts)
        {
        InjectionKey key = f_mapResourceNames.get(sName);

        return key != null && key.f_type.isA(type)
                ? f_mapResources.get(key).apply(frame, hOpts)
                : null;
        }


    // ----- MainContainer specific functionality --------------------------------------------------

    public void start()
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        ensureServiceContext();

        ConstantPool pool = f_idModule.getConstantPool();
        try (var ignore = ConstantPool.withPool(pool))
            {
            initResources(pool);
            }
        }

    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        try (var ignore = ConstantPool.withPool(f_idModule.getConstantPool()))
            {
            MethodConstant idMethod = findModuleMethod(sMethodName, ahArg);
            if (idMethod == null)
                {
                System.err.println("Missing: " +  sMethodName + " method for " + f_idModule.getValueString());
                return;
                }

            TypeConstant    typeModule = f_idModule.getType();
            TypeComposition clzModule  = f_templates.resolveClass(typeModule);
            CallChain       chain      = clzModule.getMethodCallChain(idMethod.getSignature());

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle((frame, ah, iReturn) ->
                {
                SingletonConstant idModule = frame.poolContext().ensureSingletonConstConstant(f_idModule);
                ObjectHandle      hModule  = frame.getConstHandle(idModule);

                return Op.isDeferred(hModule)
                        ? hModule.proceed(frame, frameCaller ->
                            chain.invoke(frameCaller, frameCaller.popStack(), ahArg, Op.A_IGNORE))
                        : chain.invoke(frame, hModule, ahArg, Op.A_IGNORE);
                });

            m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_idModule, e);
            }
        }

    protected void initResources(ConstantPool pool)
        {
        // +++ LocalClock
        xLocalClock  templateClock = xLocalClock.INSTANCE;
        TypeConstant typeClock     = templateClock.getCanonicalType();
        addResourceSupplier(new InjectionKey("clock"     , typeClock), templateClock::ensureDefaultClock);
        addResourceSupplier(new InjectionKey("localClock", typeClock), templateClock::ensureLocalClock);
        addResourceSupplier(new InjectionKey("utcClock"  , typeClock), templateClock::ensureUTCClock);

        // +++ NanosTimer
        xNanosTimer  templateTimer = xNanosTimer.INSTANCE;
        TypeConstant typeTimer     = templateTimer.getCanonicalType();
        addResourceSupplier(new InjectionKey("timer", typeTimer), templateTimer::ensureTimer);

        // +++ Console
        xTerminalConsole templateConsole = xTerminalConsole.INSTANCE;
        TypeConstant     typeConsole     = templateConsole.getCanonicalType();
        addResourceSupplier(new InjectionKey("console", typeConsole), templateConsole::ensureConsole);

        // +++ Random
        xRTRandom    templateRandom = xRTRandom.INSTANCE;
        TypeConstant typeRandom     = templateRandom.getCanonicalType();
        addResourceSupplier(new InjectionKey("rnd"   , typeRandom), templateRandom::ensureDefaultRandom);
        addResourceSupplier(new InjectionKey("random", typeRandom), templateRandom::ensureDefaultRandom);

        // +++ OSFileStore etc.
        TypeConstant typeFileStore = pool.ensureEcstasyTypeConstant("fs.FileStore");
        TypeConstant typeDirectory = pool.ensureEcstasyTypeConstant("fs.Directory");
        addResourceSupplier(new InjectionKey("storage", typeFileStore), this::ensureFileStore);
        addResourceSupplier(new InjectionKey("rootDir", typeDirectory), this::ensureRootDir);
        addResourceSupplier(new InjectionKey("homeDir", typeDirectory), this::ensureHomeDir);
        addResourceSupplier(new InjectionKey("curDir" , typeDirectory), this::ensureCurDir);
        addResourceSupplier(new InjectionKey("tmpDir" , typeDirectory), this::ensureTmpDir);

        // +++ WebServer
        xRTServer    templateServer = xRTServer.INSTANCE;
        TypeConstant typeServer     = templateServer.getCanonicalType();
        addResourceSupplier(new InjectionKey("server", typeServer), templateServer::ensureServer);

        // +++ Linker
        xContainerLinker templateLinker = xContainerLinker.INSTANCE;
        TypeConstant     typeLinker     = templateLinker.getCanonicalType();
        addResourceSupplier(new InjectionKey("linker", typeLinker), templateLinker::ensureLinker);

        // +++ ModuleRepository
        xCoreRepository templateRepo = xCoreRepository.INSTANCE;
        TypeConstant    typeRepo     = templateRepo.getCanonicalType();
        addResourceSupplier(new InjectionKey("repository", typeRepo), templateRepo::ensureModuleRepository);

        // +++ Compiler
        xRTCompiler  templateCompiler = xRTCompiler.INSTANCE;
        TypeConstant typeCompiler     = templateCompiler.getCanonicalType();
        addResourceSupplier(new InjectionKey("compiler", typeCompiler), templateCompiler::ensureCompiler);

        // ++ xvmProperties
        TypeConstant typeProps = pool.ensureParameterizedTypeConstant(pool.typeMap(), pool.typeString(), pool.typeString());
        addResourceSupplier(new InjectionKey("properties", typeProps), this::ensureProperties);
        }

    /**
     * Add a native resource supplier for an injection.
     *
     * @param key  the injection key
     * @param fn   the resource supplier bi-function
     */
    protected void addResourceSupplier(InjectionKey key, BiFunction<Frame, ObjectHandle, ObjectHandle> fn)
        {
        assert !f_mapResources.containsKey(key);

        f_mapResources.put(key, fn);
        f_mapResourceNames.put(key.f_sName, key);
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
                            typeMap.ensureClass(frame), haKeys, haValues, Op.A_STACK))
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
        if (hTarget instanceof DeferredCallHandle hDeferred)
            {
            hDeferred.addContinuation(frameCaller ->
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
    static Container NATIVE_CONTAINER = new Container(null, null, null, null, null)
        {
        @Override
        public String toString()
            {
            return "Primordial container";
            }
        };


    // ----- data fields ---------------------------------------------------------------------------

    private ObjectHandle m_hOSStorage;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;
    private ObjectHandle m_hProperties;

    /**
     * Map of resource names for a name based lookup.
     */
    protected final Map<String, InjectionKey> f_mapResourceNames = new HashMap<>();
    }