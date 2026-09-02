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
import java.util.concurrent.ConcurrentMap;

import java.util.function.Consumer;

import java.util.jar.JarFile;

import org.xvm.asm.ErrorListener;
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
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xInjector;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.xBasicHashCollector;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms;
import org.xvm.runtime.template._native.crypto.xRTCertificateManager;
import org.xvm.runtime.template._native.crypto.xRTKeyStore;

import org.xvm.runtime.template._native.io.xTerminalConsole;

import org.xvm.runtime.template._native.lang.src.xRTCompiler;

import org.xvm.runtime.template._native.mgmt.xContainerLinker;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;

import org.xvm.runtime.template._native.net.xRTNetwork;

import org.xvm.runtime.template._native.numbers.xRTRandom;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTType;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;

import org.xvm.runtime.template._native.web.xRTConnector;
import org.xvm.runtime.template._native.web.xRTServer;

import org.xvm.tool.Launcher.LauncherException;

import org.xvm.util.Handy;


/**
 * The main container (zero) associated with the main module.
 */
public class NativeContainer
        extends Container {
    /**
     * Create and initialize the native container.
     *
     * Native template loading publishes owner-local template objects into this container. Keeping
     * that work out of the constructor avoids exposing a partially constructed owner while
     * preserving the previous "ready before returned to the connector" behavior.
     */
    public static NativeContainer create(Runtime runtime, ModuleRepository repository,
            ErrorListener errs) {
        // register only after construction and template initialization complete, matching the
        // MainContainer/NestedContainer post-construction registration discipline; registration
        // makes the native parent visible to world-state diagnostics (snapshotWorld), which must
        // enumerate the WHOLE world from the runtime registry, native parents included
        return runtime.registerContainer(
                new NativeContainer(runtime, repository, errs).initializeNativeTemplates());
    }

    private NativeContainer(Runtime runtime, ModuleRepository repository, ErrorListener errs) {
        super(runtime, null, null, errs);

        f_repository = repository;
    }

    private NativeContainer initializeNativeTemplates() {
        ConstantPool pool = loadNativeTemplates();
        initResources(pool);
        return this;
    }


    // ----- initialization ------------------------------------------------------------------------

    private ConstantPool loadNativeTemplates() {
        ModuleStructure moduleRoot   = f_repository.loadModule(ECSTASY_MODULE);
        ModuleStructure moduleTurtle = f_repository.loadModule(TURTLE_MODULE);
        ModuleStructure moduleNative = f_repository.loadModule(NATIVE_MODULE);

        if (moduleRoot == null || moduleTurtle == null || moduleNative == null) {
            String sDesc = null;
            int    count = 0;
            if (moduleRoot == null) {
                sDesc = ECSTASY_MODULE;
                ++count;
            }
            if (moduleTurtle == null) {
                sDesc = count == 0 ? TURTLE_MODULE : (sDesc + ", " + TURTLE_MODULE);
                ++count;
            }
            if (moduleNative == null) {
                sDesc = count == 0 ? NATIVE_MODULE : (sDesc + ", " + NATIVE_MODULE);
                ++count;
            }
            throw new LauncherException(true, "Missing boot-strap " +
                    (count == 1 ?  "library" : "libraries") + ": " + sDesc);
        }

        // "root" is a merge of "native" module into the "system"
        FileStructure fileRoot = new FileStructure(moduleRoot, true);
        fileRoot.merge(moduleTurtle, true, false);
        fileRoot.merge(moduleNative, true, false);

        ModuleConstant idMissing = fileRoot.linkModules(f_repository, true);
        if (idMissing != null) {
            throw new LauncherException(true, "Missing module: " + idMissing.getName());
        }

        // obtain the cloned modules that belong to the merged container
        m_moduleSystem = fileRoot.getChild(ECSTASY_MODULE);
        m_moduleTurtle = fileRoot.getChild(TURTLE_MODULE);
        m_moduleNative = fileRoot.getChild(NATIVE_MODULE);

        ConstantPool pool = fileRoot.getConstantPool();
        finishNativeTemplateLoad(pool);
        return pool;
    }

    private void finishNativeTemplateLoad(ConstantPool pool) {
        if (pool.getNakedRefType() == null) {
            ClassStructure clzNakedRef = (ClassStructure) m_moduleTurtle.getChild("NakedRef");
            pool.setNakedRefType(clzNakedRef.getFormalType());
        }

        String sRoot = xObject.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        sRoot = URLDecoder.decode(sRoot, StandardCharsets.UTF_8);

        final Map<String, Class<? extends ClassTemplate>> mapTemplateClasses = new HashMap<>();
        if (sRoot.endsWith(".jar")) {
            scanNativeJarDirectory(sRoot, "org/xvm/runtime/template", mapTemplateClasses);
        } else {
            File dirTemplates = new File(sRoot, "org/xvm/runtime/template");
            scanNativeDirectory(dirTemplates, "", mapTemplateClasses);
        }

        // Base templates are installed before reflective template loading so converted templates
        // can resolve canonical owners through NativeTemplates instead of constructor-published
        // INSTANCE fields.
        storeNativeTemplate(new xObject (this, getClassStructure("Object")));
        storeNativeTemplate(new xEnum   (this, getClassStructure("Enum")));
        storeNativeTemplate(new xConst  (this, getClassStructure("Const")));
        storeNativeTemplate(new xService(this, getClassStructure("Service")));

        for (Map.Entry<String, Class<? extends ClassTemplate>> entry : mapTemplateClasses.entrySet()) {
            ClassStructure structClass = getClassStructure(entry.getKey());
            if (structClass == null) {
                // this is a native class for a composite type;
                // it will be declared by the corresponding "primitive"
                // (see xArray.initNative() for an example)
                continue;
            }

            if (f_mapTemplatesByType.containsKey(
                    structClass.getIdentityConstant().getType())) {
                // already loaded - one of the "base" classes
                continue;
            }

            Class<? extends ClassTemplate> clz = entry.getValue();
            if (!Modifier.isAbstract(clz.getModifiers())) {
                try {
                    storeNativeTemplate(instantiateNativeTemplate(clz, structClass));
                } catch (Exception e) {
                    throw new LauncherException(true, "Constructor failed for " + clz.getName(), e);
                }
            }
        }

        // add run-time templates
        f_mapTemplatesByType.put(pool.typeFunction(), xRTFunction.getInstance(this));
        f_mapTemplatesByType.put(pool.typeType()    , xRTType.getInstance(this));

        // clone the map since the loop below can add to it
        Set<ClassTemplate> setTemplates = new HashSet<>(f_mapTemplatesByType.values());

        for (ClassTemplate template : setTemplates) {
            template.registerNativeTemplates();
        }

        Utils.initNative(this);

        for (ClassTemplate template : f_mapTemplatesByType.values()) {
            template.initNative();
        }

        // Every template class is now loaded, so every plane-wide key has registered itself.
        // Resolve them here, while the pool is still unpublished, rather than leaving each to
        // whichever container first asks - which would be after publication for anything not
        // already interned, and is what the registration guard exists to reject.
        nativeTemplates().warmPlaneWideValues();

        ensureServiceContext();
    }

    private void scanNativeJarDirectory(String sJarFile, String sPackage, Map<String, Class<? extends ClassTemplate>> mapTemplateClasses) {
        try (JarFile jf = new JarFile(sJarFile)) {
            jf.stream().filter(entry  -> isNativeClass(sPackage, entry.getName()))
                       .forEach(entry -> {
                           Class<? extends ClassTemplate> clz = classForName(entry.getName());
                           mapTemplateClasses.put(
                                   ecstasyNameOf(clz, componentName(entry.getName())), clz);
                       });
        } catch (IOException e) {
            throw new LauncherException(e);
        }
    }

    private static boolean isNativeClass(String sPackage, String sFile) {
        return sFile.startsWith(sPackage)
            && sFile.endsWith(".class")
            && sFile.indexOf('$') < 0
            && sFile.charAt(sFile.lastIndexOf('/') + 1) == 'x';
    }

    private static String componentName(String sFile) {
        // input : org/xvm/runtime/template/numbers/xFloat64.class
        // output: numbers.Float64
        String[]      parts = Handy.parseDelimitedString(sFile, '/');
        StringBuilder sb    = new StringBuilder();
        for (int i = 4, c = parts.length - 1; i < c; ++i) {
            sb.append(parts[i])
              .append('.');
        }
        String sClass = parts[parts.length-1];
        assert sClass.charAt(0) == 'x';
        assert sClass.endsWith(".class");
        sb.append(sClass, 1, sClass.indexOf('.'));
        return sb.toString();
    }

    /**
     * The Ecstasy class a template implements: what it declares, or - for a template that declares
     * nothing - what its file name implies.
     *
     * @param clz            the template class
     * @param sFromFileName  the name derived from the file, used when the class declares none
     */
    private static String ecstasyNameOf(Class<? extends ClassTemplate> clz, String sFromFileName) {
        NativeTemplate annotation = clz.getAnnotation(NativeTemplate.class);

        return annotation == null ? sFromFileName : annotation.value();
    }

    private static Class<? extends ClassTemplate> classForName(String sFile) {
        assert sFile.endsWith(".class");
        String sClz = sFile.substring(0, sFile.length() - ".class".length()).replace('/', '.');
        try {
            return Class.forName(sClz).asSubclass(ClassTemplate.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // sPackage is either empty or ends with a dot
    private void scanNativeDirectory(File dirNative, String sPackage, Map<String, Class<? extends ClassTemplate>> mapTemplateClasses) {
        for (String sName : dirNative.list()) {
            if (sName.endsWith(".class")) {
                if (sName.startsWith("x") && !sName.contains("$")) {
                    String sSimpleName = sName.substring(1, sName.length() - 6);
                    String sQualifiedName = sPackage + sSimpleName;
                    String sClass = "org.xvm.runtime.template." + sPackage + "x" + sSimpleName;

                    try {
                        Class<? extends ClassTemplate> clz =
                                Class.forName(sClass).asSubclass(ClassTemplate.class);
                        mapTemplateClasses.put(ecstasyNameOf(clz, sQualifiedName), clz);
                    } catch (ClassNotFoundException e) {
                        throw new LauncherException(true, "Cannot load " + sClass, e);
                    }
                }
            } else {
                File dir = new File(dirNative, sName);
                if (dir.isDirectory()) {
                    scanNativeDirectory(dir, sPackage.isEmpty() ? sName + '.' : sPackage + sName + '.',
                        mapTemplateClasses);
                }
            }
        }
    }

    private void storeNativeTemplate(ClassTemplate template) {
        // register just a naked underlying type
        TypeConstant typeBase = template.getClassConstant().getType();

        registerNativeTemplate(typeBase, template);
    }

    private void initResources(ConstantPool pool) {
        // Resource templates are container-owned. Resolve them from this container's table instead
        // of the legacy process-global INSTANCE fields, so injected resources cannot capture
        // another concurrently starting container's template or constant pool.
        NativeTemplates templates = nativeTemplates();

        // +++ temporal.LocalClock
        xLocalClock  templateClock = templates.localClock();
        TypeConstant typeClock     = templateClock.getCanonicalType();
        addResourceSupplier(new InjectionKey("clock"     , typeClock), templateClock::ensureDefaultClock);
        addResourceSupplier(new InjectionKey("localClock", typeClock), templateClock::ensureLocalClock);
        addResourceSupplier(new InjectionKey("utcClock"  , typeClock), templateClock::ensureUTCClock);

        // +++ temporal.NanosTimer
        xNanosTimer  templateTimer = templates.nanosTimer();
        TypeConstant typeTimer     = templateTimer.getCanonicalType();
        addResourceSupplier(new InjectionKey("timer", typeTimer), templateTimer::ensureTimer);

        // +++ io.Console
        xTerminalConsole templateConsole = templates.terminalConsole();
        TypeConstant     typeConsole     = templateConsole.getCanonicalType();
        addResourceSupplier(new InjectionKey("console", typeConsole), templateConsole::ensureConsole);

        // +++ numbers.Random
        xRTRandom    templateRandom = templates.random();
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
        xRTNetwork   templateNetwork = templates.network();
        TypeConstant typeNetwork     = templateNetwork.getCanonicalType();
        addResourceSupplier(new InjectionKey("network"        , typeNetwork), this::ensureInsecureNetwork);
        addResourceSupplier(new InjectionKey("insecureNetwork", typeNetwork), this::ensureInsecureNetwork);
        addResourceSupplier(new InjectionKey("secureNetwork"  , typeNetwork), this::ensureSecureNetwork);

        // +++ crypto:KeyStore
        xRTKeyStore  templateKeyStore = templates.keyStore();
        TypeConstant typeKeyStore     = templateKeyStore.getCanonicalType();
        addResourceSupplier(new InjectionKey("keystore", typeKeyStore), templateKeyStore::ensureKeyStore);

        // +++ crypto:CertificateManager
        xRTCertificateManager templateCertManager = templates.certificateManager();
        TypeConstant          typeCertManager     = templateCertManager.getCanonicalType();
        addResourceSupplier(new InjectionKey("manager", typeCertManager), templateCertManager::ensureManager);

        // +++ crypto:Algorithms
        xRTAlgorithms templateAlgorithms = templates.algorithms();
        TypeConstant  typeAlgorithms     = pool.ensureTerminalTypeConstant(
                pool.ensureClassConstant(pool.ensureModuleConstant("crypto.xtclang.org"), "Algorithms"));
        addResourceSupplier(new InjectionKey("algorithms", typeAlgorithms), templateAlgorithms::ensureAlgorithms);

        // +++ web:Client.Connector
        xRTConnector templateConnector = templates.connector();
        TypeConstant typeConnector     = templateConnector.getCanonicalType();
        addResourceSupplier(new InjectionKey("connector", typeConnector), templateConnector::ensureConnector);

        // +++ web:WebServer
        xRTServer templateServer = templates.server();
        TypeConstant typeServer  = templateServer.getCanonicalType();
        addResourceSupplier(new InjectionKey("server", typeServer), templateServer::ensureServer);

        // +++ mgmt.Linker
        xContainerLinker templateLinker = xContainerLinker.getInstance(this);
        TypeConstant     typeLinker     = templateLinker.getCanonicalType();
        addResourceSupplier(new InjectionKey("linker", typeLinker), templateLinker::ensureLinker);

        // +++ mgmt.ModuleRepository
        xCoreRepository templateRepo = templates.coreRepository();
        TypeConstant    typeRepo     = templateRepo.getCanonicalType();
        addResourceSupplier(new InjectionKey("repository", typeRepo), templateRepo::ensureModuleRepository);

        // +++ lang.src.Compiler
        xRTCompiler  templateCompiler = templates.compiler();
        TypeConstant typeCompiler     = templateCompiler.getCanonicalType();
        addResourceSupplier(new InjectionKey("compiler", typeCompiler), templateCompiler::ensureCompiler);

        // +++ reflect.Injector
        xInjector templateInjector = templates.injector();
        TypeConstant typeInjector = templateInjector.getCanonicalType();
        addResourceSupplier(new InjectionKey("injector", typeInjector), templateInjector::ensureInjector);

        // +++ xvmProperties
        TypeConstant typeProps = pool.ensureMapType(pool.typeString(), pool.typeString());
        addResourceSupplier(new InjectionKey("properties", typeProps), this::ensureProperties);

        // +++ collections.HashCollector
        xBasicHashCollector templateHashCollector = templates.basicHashCollector();
        TypeConstant        typeHashCollector     = templateHashCollector.getCanonicalType();
        addResourceSupplier(new InjectionKey("hash", typeHashCollector), templateHashCollector::ensureCollector);
    }

    /**
     * Add a native resource supplier for an injection.
     *
     * @param key       the injection key
     * @param supplier  the resource supplier
     */
    private void addResourceSupplier(InjectionKey key, InjectionSupplier supplier) {
        assert !f_mapResources.containsKey(key);

        f_mapResources.put(key, supplier);
        f_mapResourceNames.put(key.f_sName, key);
    }

    public ObjectHandle ensureOSStorage(Frame frame, ObjectHandle hOpts) {
        ObjectHandle hStorage = m_hOSStorage;
        if (hStorage == null) {
            ClassTemplate    template    = getTemplate("_native.fs.OSStorage");
            ClassComposition clzStorage  = template.getCanonicalClass();
            MethodStructure  constructor = template.getStructure().findConstructor();
            ServiceContext   contextNew  = createServiceContext("OSStorage");

            switch (contextNew.sendConstructRequest(frame, clzStorage, constructor,
                            null, Utils.OBJECTS_NONE, Op.A_STACK)) {
            case Op.R_NEXT:
                hStorage = publishOSStorage(contextNew, frame.popStack());
                break;

            case Op.R_EXCEPTION:
                // the construction failed; drop the freshly registered service context
                terminate(contextNew);
                break;

            case Op.R_CALL: {
                Frame frameNext = frame.m_frameNext;
                frameNext.addContinuation(frameCaller -> {
                    ObjectHandle hResolved = frameCaller.popStack();
                    frameCaller.pushStack(publishOSStorage(contextNew, hResolved));
                    return Op.R_NEXT;
                });
                return new DeferredCallHandle(frameNext);
            }

            default:
                throw new IllegalStateException();
            }
        }

        return hStorage;
    }

    /**
     * First-wins publication for the OSStorage injectable: racing first injections each build a
     * candidate service, so the shared cache field is written exactly once and a losing
     * candidate's freshly created service context is terminated instead of staying registered
     * forever as a duplicate.
     */
    private synchronized ObjectHandle publishOSStorage(ServiceContext contextNew,
                                                       ObjectHandle hCandidate) {
        ObjectHandle hStorage = m_hOSStorage;
        if (hStorage == null) {
            m_hOSStorage = hStorage = hCandidate;
        } else {
            terminate(contextNew);
        }
        return hStorage;
    }

    private ObjectHandle ensureFileStore(Frame frame, ObjectHandle hOpts) {
        return ensureStorageProperty(frame, hOpts, "store");
    }

    private ObjectHandle ensureRootDir(Frame frame, ObjectHandle hOpts) {
        return ensureStorageProperty(frame, hOpts, "rootDir");
    }

    private ObjectHandle ensureHomeDir(Frame frame, ObjectHandle hOpts) {
        return ensureStorageProperty(frame, hOpts, "homeDir");
    }

    private ObjectHandle ensureCurDir(Frame frame, ObjectHandle hOpts) {
        return ensureStorageProperty(frame, hOpts, "curDir");
    }

    private ObjectHandle ensureTmpDir(Frame frame, ObjectHandle hOpts) {
        return ensureStorageProperty(frame, hOpts, "tmpDir");
    }

    /**
     * Derive one of {@code OSStorage}'s filesystem properties for the container that is asking,
     * and cache it there.
     *
     * <p>The {@code OSStorage} service itself stays plane-wide: it is a service, which is
     * legitimate cross-container currency, and giving each container its own would start a second
     * storage service. What must not be plane-wide is the value read off it. {@code curDir} and
     * friends are {@code GenericHandle}s carrying a {@code TypeComposition}, and a composition
     * belongs to exactly one container. Caching them on the native container gave whichever
     * container asked first ownership of the composition, and served that same handle to every
     * later container - so two sibling containers under one native plane ended up sharing a
     * composition owned by neither's ancestor. Deriving per asker means the handle, and every
     * handle in its fields, is built against the frame of the container that will hold it, which
     * is what masking could not do: masking rebuilds the outer handle but leaves the field
     * handles pointing at the original owner's compositions.</p>
     *
     * @param frame  the asking frame; its container owns the derived handle
     * @param hOpts  the injection options
     * @param sProp  the {@code OSStorage} property to read
     */
    private ObjectHandle ensureStorageProperty(Frame frame, ObjectHandle hOpts, String sProp) {
        Container                           container = frame.container();
        ConcurrentMap<String, ObjectHandle> mapCache  = container.ensureNativeResourceCache();

        ObjectHandle hCached = mapCache.get(sProp);
        if (hCached != null) {
            return hCached;
        }

        ClassTemplate    template = getTemplate("_native.fs.OSStorage");
        PropertyConstant idProp   = template.getCanonicalType().
                ensureTypeInfo(container.getErrorListener()).findProperty(sProp).getIdentity();

        // first-wins on the put, so racing fibers in one container never flip the cache between
        // equal-but-distinct reads; the race is within a container now, not across them
        return getProperty(frame, ensureOSStorage(frame, hOpts), idProp,
                h -> mapCache.putIfAbsent(sProp, h));
    }

    private ObjectHandle ensureProperties(Frame frame, ObjectHandle hOpts) {
        ObjectHandle hProps = m_hProperties;
        if (hProps == null) {
            List<StringHandle> listKeys = new ArrayList<>();
            List<StringHandle> listVals = new ArrayList<>();
            for (String sKey : (Set<String>) (Set) System.getProperties().keySet()) {
                if (sKey.startsWith("xvm.")) {
                    String sVal = System.getProperty(sKey);
                    if (sVal != null) {
                        listKeys.add(xString.makeHandle(this, sKey.substring(4)));
                        listVals.add(xString.makeHandle(this, sVal));
                    }
                }
            }
            ObjectHandle haKeys   = xArray.makeStringArrayHandle(this,
                    listKeys.toArray(Utils.STRINGS_NONE));
            ObjectHandle haValues = xArray.makeStringArrayHandle(this,
                    listVals.toArray(Utils.STRINGS_NONE));

            ConstantPool pool       = getConstantPool();
            TypeConstant typeReveal = pool.ensureMapType(pool.typeString(), pool.typeString());
            TypeConstant typeActual = pool.ensureParameterizedTypeConstant(
                                        pool.ensureEcstasyTypeConstant("maps.ListMap"),
                                        pool.typeString(), pool.typeString());

            switch (Utils.constructListMap(frame, resolveClass(typeActual), haKeys, haValues, Op.A_STACK)) {
            case Op.R_NEXT:
                hProps = frame.popStack().maskAs(this, typeReveal);
                break;

            case Op.R_EXCEPTION:
                break;

            case Op.R_CALL: {
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
                                     Consumer<ObjectHandle> consumer) {
        TypeConstant typeRevealed = idProp.getType();
        if (hTarget instanceof DeferredCallHandle hDeferred) {
            hDeferred.addContinuation(frameCaller -> {
                ObjectHandle hTargetReal = frameCaller.popStack();
                int          iResult     = hTargetReal.getTemplate().getPropertyValue(
                                                frameCaller, hTargetReal, idProp, Op.A_STACK);
                switch (iResult) {
                case Op.R_NEXT: {
                    ObjectHandle h = frameCaller.popStack().maskAs(this, typeRevealed);
                    frameCaller.pushStack(h);
                    consumer.accept(h);
                    break;
                }

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(frameCaller1 -> {
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
        switch (template.getPropertyValue(frame, hTarget, idProp, Op.A_STACK)) {
        case Op.R_NEXT: {
            ObjectHandle h = frame.popStack().maskAs(this, typeRevealed);
            consumer.accept(h);
            return h;
        }

        case Op.R_CALL:
            Frame frameNext = frame.m_frameNext;
            frameNext.addContinuation(frameCaller -> {
                ObjectHandle h = frameCaller.popStack().maskAs(this, typeRevealed);
                consumer.accept(h);
                return frameCaller.pushStack(h);
            });
            return new DeferredCallHandle(frameNext);

        case Op.R_EXCEPTION:
            return new DeferredCallHandle(frame.clearException());

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureSecureNetwork(Frame frame, ObjectHandle hOpts) {
        // the old shape cached instantiateNetwork's result directly: racing first injections
        // could each construct a network service, and the R_CALL path cached a frame-bound
        // DeferredCallHandle that a later caller would receive. The cache is now written only
        // with the resolved handle, first-wins.
        ObjectHandle hNetwork = m_hSecureNetwork;
        return hNetwork == null ? instantiateNetwork(frame, hOpts, true) : hNetwork;
    }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureInsecureNetwork(Frame frame, ObjectHandle hOpts) {
        // see ensureSecureNetwork
        ObjectHandle hNetwork = m_hInsecureNetwork;
        return hNetwork == null ? instantiateNetwork(frame, hOpts, false) : hNetwork;
    }

    protected ObjectHandle instantiateNetwork(Frame frame, ObjectHandle hOpts, boolean fSecure) {
        ObjectHandle     hNetwork        = null;
        ClassTemplate    templateNetwork = getTemplate(getIdentityConstant("_native.net.RTNetwork"));
        ClassComposition clzMask         = templateNetwork.getCanonicalClass();
        ConstantPool     pool            = getConstantPool();
        MethodStructure  constructor     = templateNetwork.getStructure().findConstructor(pool.typeBoolean());
        ObjectHandle[]   ahParams        = {xBoolean.makeHandle(frame, fSecure)};

        switch (templateNetwork.construct(frame, constructor, clzMask, null, ahParams, Op.A_STACK)) {
        case Op.R_NEXT:
            hNetwork = publishNetwork(fSecure, frame.popStack());
            break;

        case Op.R_EXCEPTION:
            break;

        case Op.R_CALL: {
            Frame frameNext = frame.m_frameNext;
            frameNext.addContinuation(frameCaller -> {
                    ObjectHandle hResolved = frameCaller.popStack();
                    frameCaller.pushStack(publishNetwork(fSecure, hResolved));
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
     * First-wins publication for the network injectables; the losing candidate service becomes
     * unreachable and is dropped by the weak service registry.
     */
    private synchronized ObjectHandle publishNetwork(boolean fSecure, ObjectHandle hCandidate) {
        ObjectHandle hNetwork = fSecure ? m_hSecureNetwork : m_hInsecureNetwork;
        if (hNetwork == null) {
            hNetwork = hCandidate;
            if (fSecure) {
                m_hSecureNetwork = hCandidate;
            } else {
                m_hInsecureNetwork = hCandidate;
            }
        }
        return hNetwork;
    }


    // ----- Container methods ---------------------------------------------------------------------

    @Override
    public ModuleConstant getModule() {
        return m_moduleSystem.getIdentityConstant();
    }

    @Override
    public ConstantPool getConstantPool() {
        return m_moduleNative.getConstantPool();
    }

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts) {
        InjectionKey key = f_mapResourceNames.get(sName);
        if (key == null) {
            // for "Nullable" types the NativeContainer can only supply a trivial result;
            // anything better than that must be done naturally by a container that hosts the
            // calling container
            return type.isNullable() ? xNullable.makeHandle(frame) : null;
        }

        // check for equality first, but allow "congruency", "duck type" equality as well or
        // sans-Nullable equivalency
        TypeConstant typeResource = key.f_type;
        return typeResource.equals(type) || typeResource.isEquivalent(type)
                    || typeResource.isEquivalent(type.removeNullable())
                ? f_mapResources.get(key).supply(frame, hOpts)
                : null;
    }

    @Override
    public Container getOriginContainer(SingletonConstant constSingle) {
        return this;
    }

    @Override
    public ClassTemplate getTemplate(String sName) {
        return getTemplate(getIdentityConstant(sName));
    }

    @Override
    public ClassStructure getClassStructure(String sName) {
        Component component = sName.startsWith(NATIVE_PREFIX)
                ? m_moduleNative.getChildByPath(sName.substring(NATIVE_LENGTH))
                : sName.startsWith(TURTLE_PREFIX)
                    ? m_moduleTurtle.getChildByPath(sName.substring(TURTLE_LENGTH))
                    : m_moduleSystem.getChildByPath(sName);

        while (component instanceof TypedefStructure typedef) {
            component = typedef.getType().getSingleUnderlyingClass(true).getComponent();
        }

        return (ClassStructure) component;
    }

    @Override
    public ModuleRepository getModuleRepository() {
        return f_repository;
    }

    @Override
    public FileStructure createFileStructure(ModuleStructure moduleApp) {
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
    protected void registerNativeTemplate(TypeConstant type, ClassTemplate template) {
        f_mapTemplatesByType.putIfAbsent(type, template);
    }

    private ClassTemplate instantiateNativeTemplate(
            Class<? extends ClassTemplate> clz, ClassStructure structClass) throws Exception {
        // The old boolean fInstance fallback was a hidden owner-role side channel. Native
        // templates now expose the ordinary owner constructor; any derived/canonical distinction
        // must be explicit inside the template hierarchy.
        return clz.getConstructor(Container.class, ClassStructure.class).newInstance(this, structClass);
    }

    /**
     * Obtain an object type for the specified constant.
     */
    protected TypeConstant getConstType(Constant constValue) {
        String sComponent;

        switch (constValue.getFormat()) {
        case Char, String:
        case Bit,  Nibble:

        case IntLiteral, FPLiteral:

        case Int8,   UInt8:
        case Int16,  UInt16:
        case Int32,  UInt32:
        case Int64,  UInt64:
        case Int128, UInt128:
        case IntN,   UIntN:
        case BFloat16:
        case Float16, Float32, Float64, Float128, FloatN:
        case          Dec32,   Dec64,   Dec128,   DecN:

        case Array, UInt8Array:
        case Date, TimeOfDay, Time, TimeZone, Duration:
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
            sComponent = "maps.ListMap";
            break;

        case Set:
            // see xArray.createConstHandle()
            sComponent = "collections.Array";
            break;

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

        case MapEntry:
        default:
            throw new LauncherException(true, "No implementation for constant: " + constValue);
        }

        return getClassStructure(sComponent).getIdentityConstant().getType();
    }

    private IdentityConstant getIdentityConstant(String sName) {
        try {
            return f_mapIdByName.computeIfAbsent(sName, s ->
                getClassStructure(s).getIdentityConstant());
        } catch (NullPointerException e) {
            throw new LauncherException(true, "Missing constant: " + sName, e);
        }
    }

    @Override
    public String toString() {
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

    private volatile ObjectHandle m_hOSStorage;
    private ObjectHandle m_hProperties;

    private volatile ObjectHandle m_hSecureNetwork;
    private volatile ObjectHandle m_hInsecureNetwork;

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
