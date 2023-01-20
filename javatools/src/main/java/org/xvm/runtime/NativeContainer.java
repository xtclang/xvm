package org.xvm.runtime;


import java.io.File;
import java.io.IOException;

import java.lang.reflect.Modifier;

import java.net.URLDecoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Consumer;

import java.util.jar.JarFile;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.runtime.template._native.crypto.xRTKeyStore;

import org.xvm.runtime.template._native.lang.src.xRTCompiler;

import org.xvm.runtime.template._native.mgmt.xContainerLinker;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;

import org.xvm.runtime.template._native.net.xRTNetwork;

import org.xvm.runtime.template._native.numbers.xRTRandom;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTType;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;

import org.xvm.runtime.template._native.web.xRTServer;

import org.xvm.util.Handy;


/**
 * The main container (zero) associated with the main module.
 */
public class NativeContainer
        extends Container
    {
    public NativeContainer(Runtime runtime, ModuleRepository repository)
        {
        super(runtime, null, null);

        f_repository = repository;

        ConstantPool pool = loadNativeTemplates();
        try (var ignore = ConstantPool.withPool(pool))
            {
            initResources(pool);
            }
        }


    // ----- initialization ------------------------------------------------------------------------

    private ConstantPool loadNativeTemplates()
        {
        ModuleStructure moduleRoot   = f_repository.loadModule(ECSTASY_MODULE);
        ModuleStructure moduleTurtle = f_repository.loadModule(TURTLE_MODULE);
        ModuleStructure moduleNative = f_repository.loadModule(NATIVE_MODULE);

        if (moduleRoot == null || moduleTurtle == null || moduleNative == null)
            {
            throw new IllegalStateException("Native libraries are missing");
            }

        // "root" is a merge of "native" module into the "system"
        FileStructure fileRoot = new FileStructure(moduleRoot, true);
        fileRoot.merge(moduleTurtle, true, false);
        fileRoot.merge(moduleNative, true, false);

        fileRoot.linkModules(f_repository, true);

        // obtain the cloned modules that belong to the merged container
        m_moduleSystem = (ModuleStructure) fileRoot.getChild(ECSTASY_MODULE);
        m_moduleTurtle = (ModuleStructure) fileRoot.getChild(TURTLE_MODULE);
        m_moduleNative = (ModuleStructure) fileRoot.getChild(NATIVE_MODULE);

        ConstantPool pool = fileRoot.getConstantPool();
        ConstantPool.setCurrentPool(pool);

        if (pool.getNakedRefType() == null)
            {
            ClassStructure clzNakedRef = (ClassStructure) m_moduleTurtle.getChild("NakedRef");
            pool.setNakedRefType(clzNakedRef.getFormalType());
            }

        String sRoot = xObject.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        sRoot = URLDecoder.decode(sRoot, StandardCharsets.UTF_8);

        Map<String, Class> mapTemplateClasses = new HashMap<>();
        if (sRoot.endsWith(".jar"))
            {
            scanNativeJarDirectory(sRoot, "org/xvm/runtime/template", mapTemplateClasses);
            }
        else
            {
            File dirTemplates = new File(sRoot, "org/xvm/runtime/template");
            scanNativeDirectory(dirTemplates, "", mapTemplateClasses);
            }

        // we need a number of INSTANCE static variables to be set up right away
        // (they are used by the ClassTemplate constructor)
        storeNativeTemplate(new xObject (this, getClassStructure("Object"),  true));
        storeNativeTemplate(new xEnum   (this, getClassStructure("Enum"),    true));
        storeNativeTemplate(new xConst  (this, getClassStructure("Const"),   true));
        storeNativeTemplate(new xService(this, getClassStructure("Service"), true));

        for (Map.Entry<String, Class> entry : mapTemplateClasses.entrySet())
            {
            ClassStructure structClass = getClassStructure(entry.getKey());
            if (structClass == null)
                {
                // this is a native class for a composite type;
                // it will be declared by the corresponding "primitive"
                // (see xArray.initNative() for an example)
                continue;
                }

            if (f_mapTemplatesByType.containsKey(
                    structClass.getIdentityConstant().getType()))
                {
                // already loaded - one of the "base" classes
                continue;
                }

            Class<ClassTemplate> clz = entry.getValue();
            if (!Modifier.isAbstract(clz.getModifiers()))
                {
                try
                    {
                    storeNativeTemplate(clz.getConstructor(
                        Container.class, ClassStructure.class, Boolean.TYPE).
                        newInstance(this, structClass, Boolean.TRUE));
                    }
                catch (Exception e)
                    {
                    throw new RuntimeException("Constructor failed for " + clz.getName(), e);
                    }
                }
            }

        // add run-time templates
        f_mapTemplatesByType.put(pool.typeFunction(), xRTFunction.INSTANCE);
        f_mapTemplatesByType.put(pool.typeType()    , xRTType.INSTANCE);

        // clone the map since the loop below can add to it
        Set<ClassTemplate> setTemplates = new HashSet<>(f_mapTemplatesByType.values());

        for (ClassTemplate template : setTemplates)
            {
            template.registerNativeTemplates();
            }

        Utils.initNative(this);

        for (ClassTemplate template : f_mapTemplatesByType.values())
            {
            template.initNative();
            }

        ensureServiceContext();

        ConstantPool.setCurrentPool(null);
        return pool;
        }

    private void scanNativeJarDirectory(String sJarFile, String sPackage, Map<String, Class> mapTemplateClasses)
        {
        try (JarFile jf = new JarFile(sJarFile))
            {
            jf.stream().filter(entry  -> isNativeClass(sPackage, entry.getName()))
                       .forEach(entry -> mapTemplateClasses.put(componentName(entry.getName()),
                                                                classForName(entry.getName())));
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        }

    private static boolean isNativeClass(String sPackage, String sFile)
        {
        return sFile.startsWith(sPackage)
            && sFile.endsWith(".class")
            && sFile.indexOf('$') < 0
            && sFile.charAt(sFile.lastIndexOf('/') + 1) == 'x';
        }

    private static String componentName(String sFile)
        {
        // input : org/xvm/runtime/template/numbers/xFloat64.class
        // output: numbers.Float64
        String[]      parts = Handy.parseDelimitedString(sFile, '/');
        StringBuilder sb    = new StringBuilder();
        for (int i = 4, c = parts.length - 1; i < c; ++i)
            {
            sb.append(parts[i])
              .append('.');
            }
        String sClass = parts[parts.length-1];
        assert sClass.charAt(0) == 'x';
        assert sClass.endsWith(".class");
        sb.append(sClass, 1, sClass.indexOf('.'));
        return sb.toString();
        }

    private static Class classForName(String sFile)
        {
        assert sFile.endsWith(".class");
        String sClz = sFile.substring(0, sFile.length() - ".class".length()).replace('/', '.');
        try
            {
            return Class.forName(sClz);
            }
        catch (ClassNotFoundException e)
            {
            throw new RuntimeException(e);
            }
        }

    // sPackage is either empty or ends with a dot
    private void scanNativeDirectory(File dirNative, String sPackage, Map<String, Class> mapTemplateClasses)
        {
        for (String sName : dirNative.list())
            {
            if (sName.endsWith(".class"))
                {
                if (sName.startsWith("x") && !sName.contains("$"))
                    {
                    String sSimpleName = sName.substring(1, sName.length() - 6);
                    String sQualifiedName = sPackage + sSimpleName;
                    String sClass = "org.xvm.runtime.template." + sPackage + "x" + sSimpleName;

                    try
                        {
                        mapTemplateClasses.put(sQualifiedName, Class.forName(sClass));
                        }
                    catch (ClassNotFoundException e)
                        {
                        throw new IllegalStateException("Cannot load " + sClass, e);
                        }
                    }
                }
            else
                {
                File dir = new File(dirNative, sName);
                if (dir.isDirectory())
                    {
                    scanNativeDirectory(dir, sPackage.isEmpty() ? sName + '.' : sPackage + sName + '.',
                        mapTemplateClasses);
                    }
                }
            }
        }

    private void storeNativeTemplate(ClassTemplate template)
        {
        // register just a naked underlying type
        TypeConstant typeBase = template.getClassConstant().getType();

        registerNativeTemplate(typeBase, template);
        }

    private void initResources(ConstantPool pool)
        {
        // +++ temporal.LocalClock
        xLocalClock  templateClock = xLocalClock.INSTANCE;
        TypeConstant typeClock     = templateClock.getCanonicalType();
        addResourceSupplier(new InjectionKey("clock"     , typeClock), templateClock::ensureDefaultClock);
        addResourceSupplier(new InjectionKey("localClock", typeClock), templateClock::ensureLocalClock);
        addResourceSupplier(new InjectionKey("utcClock"  , typeClock), templateClock::ensureUTCClock);

        // +++ temporal.NanosTimer
        xNanosTimer  templateTimer = xNanosTimer.INSTANCE;
        TypeConstant typeTimer     = templateTimer.getCanonicalType();
        addResourceSupplier(new InjectionKey("timer", typeTimer), templateTimer::ensureTimer);

        // +++ io.Console
        xTerminalConsole templateConsole = xTerminalConsole.INSTANCE;
        TypeConstant     typeConsole     = templateConsole.getCanonicalType();
        addResourceSupplier(new InjectionKey("console", typeConsole), templateConsole::ensureConsole);

        // +++ numbers.Random
        xRTRandom    templateRandom = xRTRandom.INSTANCE;
        TypeConstant typeRandom     = templateRandom.getCanonicalType();
        addResourceSupplier(new InjectionKey("rnd"   , typeRandom), templateRandom::ensureDefaultRandom);
        addResourceSupplier(new InjectionKey("random", typeRandom), templateRandom::ensureDefaultRandom);

        // +++ fs.OSFileStore etc.
        TypeConstant typeFileStore = pool.ensureEcstasyTypeConstant("fs.FileStore");
        TypeConstant typeDirectory = pool.ensureEcstasyTypeConstant("fs.Directory");
        addResourceSupplier(new InjectionKey("storage", typeFileStore), this::ensureFileStore);
        addResourceSupplier(new InjectionKey("rootDir", typeDirectory), this::ensureRootDir);
        addResourceSupplier(new InjectionKey("homeDir", typeDirectory), this::ensureHomeDir);
        addResourceSupplier(new InjectionKey("curDir" , typeDirectory), this::ensureCurDir);
        addResourceSupplier(new InjectionKey("tmpDir" , typeDirectory), this::ensureTmpDir);

        // +++ net:Network
        xRTNetwork   templateNetwork = xRTNetwork.INSTANCE;
        TypeConstant typeNetwork     = templateNetwork.getCanonicalType();
        addResourceSupplier(new InjectionKey("network"        , typeNetwork), this::ensureInsecureNetwork);
        addResourceSupplier(new InjectionKey("insecureNetwork", typeNetwork), this::ensureInsecureNetwork);
        addResourceSupplier(new InjectionKey("secureNetwork"  , typeNetwork), this::ensureSecureNetwork);

        // +++ crypto:KeyStore
        xRTKeyStore  templateKeyStore = xRTKeyStore.INSTANCE;
        TypeConstant typeKeyStore     = templateKeyStore.getCanonicalType();
        addResourceSupplier(new InjectionKey("keystore", typeKeyStore), templateKeyStore::ensureKeyStore);

        // +++ crypto:Algorithms

        TypeConstant typeAlgorithms = pool.ensureTerminalTypeConstant(
                pool.ensureClassConstant(pool.ensureModuleConstant("crypto.xtclang.org"), "Algorithms"));
        addResourceSupplier(new InjectionKey("algorithms"  , typeAlgorithms), this::ensureAlgorithms);

        // +++ web:WebServer
        xRTServer templateServer = xRTServer.INSTANCE;
        TypeConstant typeServer  = templateServer.getCanonicalType();
        addResourceSupplier(new InjectionKey("server", typeServer), templateServer::ensureServer);

        // +++ mgmt.Linker
        xContainerLinker templateLinker = xContainerLinker.INSTANCE;
        TypeConstant     typeLinker     = templateLinker.getCanonicalType();
        addResourceSupplier(new InjectionKey("linker", typeLinker), templateLinker::ensureLinker);

        // +++ mgmt.ModuleRepository
        xCoreRepository templateRepo = xCoreRepository.INSTANCE;
        TypeConstant    typeRepo     = templateRepo.getCanonicalType();
        addResourceSupplier(new InjectionKey("repository", typeRepo), templateRepo::ensureModuleRepository);

        // +++ lang.src.Compiler
        xRTCompiler  templateCompiler = xRTCompiler.INSTANCE;
        TypeConstant typeCompiler     = templateCompiler.getCanonicalType();
        addResourceSupplier(new InjectionKey("compiler", typeCompiler), templateCompiler::ensureCompiler);

        // +++ xvmProperties
        TypeConstant typeProps = pool.ensureMapType(pool.typeString(), pool.typeString());
        addResourceSupplier(new InjectionKey("properties", typeProps), this::ensureProperties);
        }

    /**
     * Add a native resource supplier for an injection.
     *
     * @param key       the injection key
     * @param supplier  the resource supplier
     */
    private void addResourceSupplier(InjectionKey key, InjectionSupplier supplier)
        {
        assert !f_mapResources.containsKey(key);

        f_mapResources.put(key, supplier);
        f_mapResourceNames.put(key.f_sName, key);
        }

    protected ObjectHandle ensureOSStorage(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hStorage = m_hOSStorage;
        if (hStorage == null)
            {
            ClassTemplate    template    = getTemplate("_native.fs.OSStorage");
            ClassComposition clzStorage  = template.getCanonicalClass();
            MethodStructure  constructor = template.getStructure().findConstructor();
            ServiceContext   contextNew  = createServiceContext("OSStorage");

            switch (contextNew.sendConstructRequest(frame, clzStorage, constructor,
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

    private ObjectHandle ensureFileStore(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hStore = m_hFileStore;
        if (hStore == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
            if (hOSStorage != null)
                {
                ClassTemplate    template = getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("store").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hFileStore = h);
                }
            }

        return hStore;
        }

    private ObjectHandle ensureRootDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hRootDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
            if (hOSStorage != null)
                {
                ClassTemplate    template = getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("rootDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hRootDir = h);
                }
            }

        return hDir;
        }

    private ObjectHandle ensureHomeDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hHomeDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
            if (hOSStorage != null)
                {
                ClassTemplate    template = getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("homeDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hHomeDir = h);
                }
            }

        return hDir;
        }

    private ObjectHandle ensureCurDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hCurDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
            if (hOSStorage != null)
                {
                ClassTemplate    template = getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("curDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hCurDir = h);
                }
            }

        return hDir;
        }

    private ObjectHandle ensureTmpDir(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hDir = m_hTmpDir;
        if (hDir == null)
            {
            ObjectHandle hOSStorage = ensureOSStorage(frame, hOpts);
            if (hOSStorage != null)
                {
                ClassTemplate    template = getTemplate("_native.fs.OSStorage");
                PropertyConstant idProp   = template.getCanonicalType().
                        ensureTypeInfo().findProperty("tmpDir").getIdentity();

                return getProperty(frame, hOSStorage, idProp, h -> m_hTmpDir = h);
                }
            }

        return hDir;
        }

    private ObjectHandle ensureProperties(Frame frame, ObjectHandle hOpts)
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

            ConstantPool pool       = getConstantPool();
            TypeConstant typeReveal = pool.ensureMapType(pool.typeString(), pool.typeString());
            TypeConstant typeActual = pool.ensureParameterizedTypeConstant(
                                        pool.ensureEcstasyTypeConstant("collections.ListMap"),
                                        pool.typeString(), pool.typeString());

            switch (Utils.constructListMap(frame,
                            resolveClass(typeActual), haKeys, haValues, Op.A_STACK))
                {
                case Op.R_NEXT:
                    hProps = frame.popStack().maskAs(this, typeReveal);
                    break;

                case Op.R_EXCEPTION:
                    break;

                case Op.R_CALL:
                    {
                    Frame frameNext = frame.m_frameNext;
                    frameNext.addContinuation(frameCaller ->
                        frameCaller.pushStack(
                            m_hProperties = frameCaller.peekStack().maskAs(this, typeReveal)));
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
                        ObjectHandle h = frameCaller.popStack().maskAs(this, typeRevealed);
                        frameCaller.pushStack(h);
                        consumer.accept(h);
                        break;
                        }

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(frameCaller1 ->
                            {
                            ObjectHandle h = frameCaller1.popStack().maskAs(this, typeRevealed);
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
                ObjectHandle h = frame.popStack().maskAs(this, typeRevealed);
                consumer.accept(h);
                return h;
                }

            case Op.R_CALL:
                Frame frameNext = frame.m_frameNext;
                frameNext.addContinuation(frameCaller ->
                    {
                    ObjectHandle h = frameCaller.popStack().maskAs(this, typeRevealed);
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
     * Injection support method.
     */
    public ObjectHandle ensureSecureNetwork(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hNetwork = m_hSecureNetwork;
        if (hNetwork == null)
            {
            m_hSecureNetwork = hNetwork = instantiateNetwork(frame, hOpts, true);
            }

        return hNetwork;
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureInsecureNetwork(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hNetwork = m_hInsecureNetwork;
        if (hNetwork == null)
            {
            m_hInsecureNetwork = hNetwork = instantiateNetwork(frame, hOpts, false);
            }

        return hNetwork;
        }

    protected ObjectHandle instantiateNetwork(Frame frame, ObjectHandle hOpts, boolean fSecure)
        {
        ObjectHandle     hNetwork        = null;
        ClassTemplate    templateNetwork = getTemplate(getIdentityConstant("_native.net.RTNetwork"));
        ClassComposition clzMask         = templateNetwork.getCanonicalClass();
        ConstantPool     pool            = getConstantPool();
        MethodStructure  constructor     = templateNetwork.getStructure().findConstructor(pool.typeBoolean());
        ObjectHandle[]   ahParams        = new ObjectHandle[] {xBoolean.makeHandle(fSecure)};

        switch (templateNetwork.construct(frame, constructor, clzMask, null, ahParams, Op.A_STACK))
            {
            case Op.R_NEXT:
                hNetwork = frame.popStack();
                break;

            case Op.R_EXCEPTION:
                break;

            case Op.R_CALL:
                {
                Frame frameNext = frame.m_frameNext;
                frameNext.addContinuation(frameCaller ->
                        {
                        if (fSecure)
                            {
                            m_hSecureNetwork = frameCaller.peekStack();
                            }
                        else
                            {
                            m_hInsecureNetwork = frameCaller.peekStack();
                            }
                        return Op.R_NEXT;
                        });
                return new ObjectHandle.DeferredCallHandle(frameNext);
                }

            default:
                throw new IllegalStateException();
            }

        return hNetwork;
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureAlgorithms(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hAlgorithms = m_hAlgorithms;
        if (hAlgorithms == null)
            {
            m_hAlgorithms = hAlgorithms = instantiateAlgorithms(frame, hOpts);
            }

        return hAlgorithms;
        }

    protected ObjectHandle instantiateAlgorithms(Frame frame, ObjectHandle hOpts)
        {
        ClassStructure  clz = getClassStructure("_native.crypto.RTAlgorithms");
        MethodStructure fn  = clz.findMethod("createAlgorithms", 1);

        String[] asNames = new String[]
            {
            "AES/CBC/NoPadding",
            "RSA/ECB/PKCS1Padding"
            };

        ObjectHandle[] ahArg = new ObjectHandle[fn.getMaxVars()];
        ahArg[0] = xString.makeArrayHandle(asNames);

        switch (frame.call1(fn, null, ahArg, Op.A_STACK))
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                return new DeferredCallHandle(frame.m_frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }


    // ----- Container methods ---------------------------------------------------------------------

    @Override
    public ModuleConstant getModule()
        {
        return m_moduleSystem.getIdentityConstant();
        }

    @Override
    public ConstantPool getConstantPool()
        {
        return m_moduleNative.getConstantPool();
        }

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts)
        {
        InjectionKey key = f_mapResourceNames.get(sName);
        if (key == null)
            {
            return null;
            }

        // check for equality first, but allow "congruency" or "duck type" equality as well
        TypeConstant typeResource = key.f_type;
        return typeResource.equals(type) ||
                    (typeResource.isA(type) && type.isA(typeResource))
                ? f_mapResources.get(key).supply(frame, hOpts)
                : null;
        }

    @Override
    public Container getOriginContainer(SingletonConstant constSingle)
        {
        return this;
        }

    @Override
    public ClassTemplate getTemplate(String sName)
        {
        return getTemplate(getIdentityConstant(sName));
        }

    @Override
    public ClassStructure getClassStructure(String sName)
        {
        Component component = sName.startsWith(NATIVE_PREFIX)
                ? m_moduleNative.getChildByPath(sName.substring(NATIVE_LENGTH))
                : sName.startsWith(TURTLE_PREFIX)
                    ? m_moduleTurtle.getChildByPath(sName.substring(TURTLE_LENGTH))
                    : m_moduleSystem.getChildByPath(sName);

        while (component instanceof TypedefStructure typedef)
            {
            component = typedef.getType().getSingleUnderlyingClass(true).getComponent();
            }

        return (ClassStructure) component;
        }

    @Override
    public ModuleRepository getModuleRepository()
        {
        return f_repository;
        }

    @Override
    public FileStructure createFileStructure(ModuleStructure moduleApp)
        {
        // Note: we don't need to re-synthesize structures for shared modules
        FileStructure fileApp = new FileStructure(m_moduleSystem, false);

        // TODO CP/GG: that needs to be reworked (for now the order is critical)
        fileApp.merge(m_moduleTurtle, false, false);
        fileApp.merge(f_repository.loadModule("crypto.xtclang.org"), true, false);
        fileApp.merge(f_repository.loadModule("net.xtclang.org"), true, false);
        fileApp.merge(f_repository.loadModule("web.xtclang.org"), true, false);
        fileApp.merge(m_moduleNative, false, false);

        fileApp.merge(moduleApp, true, true);

        assert fileApp.validateConstants();
        return fileApp;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Register the specified native template.
     */
    protected void registerNativeTemplate(TypeConstant type, ClassTemplate template)
        {
        f_mapTemplatesByType.putIfAbsent(type, template);
        }

    /**
     * Obtain an object type for the specified constant.
     */
    protected TypeConstant getConstType(Constant constValue)
        {
        String sComponent;

        switch (constValue.getFormat())
            {
            case Char, String:
            case Bit,  Nibble:

            case IntLiteral, FPLiteral:

            case Int,      UInt:
            case CInt8,    Int8,   CUInt8,   UInt8:
            case CInt16,   Int16,  CUInt16,  UInt16:
            case CInt32,   Int32,  CUInt32,  UInt32:
            case CInt64,   Int64,  CUInt64,  UInt64:
            case CInt128,  Int128, CUInt128, UInt128:
            case CIntN,    IntN,   CUIntN,   UIntN:
            case BFloat16:
            case Float16, Float32, Float64, Float128, FloatN:
            case Dec,     Dec32,   Dec64,   Dec128,   DecN:

            case Array, UInt8Array:
            case Date, TimeOfDay, Time, Duration:
            case Range, Path, Version, RegEx:
            case Module, Package:
            case Tuple:
            case SingletonConst:
                return constValue.getType();

            case FileStore:
                sComponent = "_native.fs.CPFileStore";
                break;

            case FSDir:
                sComponent = "_native.fs.CPDirectory";
                break;

            case FSFile:
                sComponent = "_native.fs.CPFile";
                break;

            case Map:
                sComponent = "collections.ListMap";
                break;

            case Set:
                // see xArray.createConstHandle()
                sComponent = "collections.Array";
                break;

            case MapEntry:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Class:
            case DecoratedClass:
            case NativeClass:
                sComponent = "reflect.Class";
                break;

            case PropertyClassType:
                sComponent = "_native.reflect.RTProperty";
                break;

            case AnnotatedType, ParameterizedType:
            case ImmutableType, AccessType, TerminalType:
            case UnionType, IntersectionType, DifferenceType:
                sComponent = "_native.reflect.RTType";
                break;

            case Method:
                sComponent = ((MethodConstant) constValue).isFunction()
                        ? "_native.reflect.RTFunction" : "_native.reflect.RTMethod";
                break;

            default:
                throw new IllegalStateException(constValue.toString());
            }

        return getClassStructure(sComponent).getIdentityConstant().getType();
        }

    private IdentityConstant getIdentityConstant(String sName)
        {
        try
            {
            return f_mapIdByName.computeIfAbsent(sName, s ->
                getClassStructure(s).getIdentityConstant());
            }
        catch (NullPointerException e)
            {
            throw new IllegalArgumentException("Missing constant: " + sName);
            }
        }

    @Override
    public String toString()
        {
        return "Primordial container";
        }


    // ----- constants and data fields -------------------------------------------------------------

    private static final String ECSTASY_MODULE = Constants.ECSTASY_MODULE;
    private static final String TURTLE_MODULE  = Constants.TURTLE_MODULE;
    private static final String NATIVE_MODULE  = Constants.NATIVE_MODULE;
    private static final String TURTLE_PREFIX  = "mack.";
    private static final int    TURTLE_LENGTH  = TURTLE_PREFIX.length();
    private static final String NATIVE_PREFIX  = "_native.";
    private static final int    NATIVE_LENGTH  = NATIVE_PREFIX.length();

    private ObjectHandle m_hOSStorage;
    private ObjectHandle m_hFileStore;
    private ObjectHandle m_hRootDir;
    private ObjectHandle m_hHomeDir;
    private ObjectHandle m_hCurDir;
    private ObjectHandle m_hTmpDir;
    private ObjectHandle m_hProperties;

    private ObjectHandle m_hSecureNetwork;
    private ObjectHandle m_hInsecureNetwork;
    private ObjectHandle m_hAlgorithms;

    private final ModuleRepository f_repository;
    private       ModuleStructure  m_moduleSystem;
    private       ModuleStructure  m_moduleTurtle;
    private       ModuleStructure  m_moduleNative;

    /**
     * Map of IdentityConstants by name.
     */
    private final Map<String, IdentityConstant> f_mapIdByName = new ConcurrentHashMap<>();

    /**
     * Map of resource names for a name based lookup.
     */
    private final Map<String, InjectionKey> f_mapResourceNames = new HashMap<>();

    /**
     * Map of resources that are injectable from this container, keyed by their InjectionKey.
     */
    private final Map<InjectionKey, InjectionSupplier> f_mapResources = new HashMap<>();
    }