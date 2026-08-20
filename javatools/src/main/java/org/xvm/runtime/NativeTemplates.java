package org.xvm.runtime;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;
import org.xvm.runtime.template.collections.xNibbleArray;
import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.maps.xListMap;

import org.xvm.runtime.template.reflect.xModule;
import org.xvm.runtime.template.reflect.xPackage;
import org.xvm.runtime.template.reflect.xInjector;

import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.collections.xBasicHashCollector;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromBit;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromByte;
import org.xvm.runtime.template._native.collections.arrays.xRTViewToBit;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms;
import org.xvm.runtime.template._native.crypto.xRTCertificateManager;
import org.xvm.runtime.template._native.crypto.xRTKeyStore;

import org.xvm.runtime.template._native.fs.xOSDirectory;
import org.xvm.runtime.template._native.fs.xOSFile;
import org.xvm.runtime.template._native.fs.xRawOSFileChannel;

import org.xvm.runtime.template._native.io.xTerminalConsole;

import org.xvm.runtime.template._native.lang.src.xRTCompiler;

import org.xvm.runtime.template._native.mgmt.xContainerControl;
import org.xvm.runtime.template._native.mgmt.xContainerLinker;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;

import org.xvm.runtime.template._native.net.xRTNameService;
import org.xvm.runtime.template._native.net.xRTNetwork;
import org.xvm.runtime.template._native.net.xRTSocket;

import org.xvm.runtime.template._native.numbers.xRTRandom;

import org.xvm.runtime.template._native.reflect.xRTClassTemplate;
import org.xvm.runtime.template._native.reflect.xRTComponentTemplate;
import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTMethod;
import org.xvm.runtime.template._native.reflect.xRTModuleTemplate;
import org.xvm.runtime.template._native.reflect.xRTProperty;
import org.xvm.runtime.template._native.reflect.xRTPropertyClassTemplate;
import org.xvm.runtime.template._native.reflect.xRTSignature;
import org.xvm.runtime.template._native.reflect.xRTType;
import org.xvm.runtime.template._native.reflect.xRTTypeTemplate;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;

import org.xvm.runtime.template._native.web.xRTConnector;
import org.xvm.runtime.template._native.web.xRTServer;

import org.xvm.runtime.template._native.xRTServiceControl;

import org.xvm.util.Lazy;


/**
 * Container-owned lookup table for native templates.
 *
 * <p>The keys are immutable JVM constants, but the resolved template objects are cached by the
 * owning {@link Container}. This preserves the old "one base template per container" behavior
 * without constructor-published process-global INSTANCE fields.</p>
 */
public final class NativeTemplates {
    NativeTemplates(Container container) {
        f_container    = requireNonNull(container, "container");
        f_templateBit  = Lazy.of(() -> f_container.getTemplate(
                f_container.getConstantPool().typeBitArray(), xBitArray.class));
        f_templateByte = Lazy.of(() -> f_container.getTemplate(
                f_container.getConstantPool().typeByteArray(), xByteArray.class));
        f_templateNibble = Lazy.of(() -> {
            var pool = f_container.getConstantPool();
            return f_container.getTemplate(pool.ensureArrayType(pool.typeNibble()),
                    xNibbleArray.class);
        });
    }

    /**
     * @return the lookup table for the specified container
     */
    public static NativeTemplates get(Container container) {
        return requireNonNull(container, "container").nativeTemplates();
    }

    /**
     * @return the lookup table for the specified frame's container
     */
    public static NativeTemplates get(Frame frame) {
        return get(requireNonNull(frame, "frame").container());
    }

    /**
     * @return the lookup table for the specified template's container
     */
    public static NativeTemplates get(ClassTemplate template) {
        return get(requireNonNull(template, "template").f_container);
    }

    public xArray array() {
        return get(ARRAY);
    }

    public xBitArray bitArray() {
        return f_templateBit.get();
    }

    public xByteArray byteArray() {
        return f_templateByte.get();
    }

    public xNibbleArray nibbleArray() {
        return f_templateNibble.get();
    }

    public xListMap listMap() {
        return get(LIST_MAP);
    }

    public xTuple tuple() {
        return get(TUPLE);
    }

    public xOSDirectory osDirectory() {
        return get(OS_DIRECTORY);
    }

    public xOSFile osFile() {
        return get(OS_FILE);
    }

    public xRawOSFileChannel rawOSFileChannel() {
        return get(RAW_OS_FILE_CHANNEL);
    }

    public boolean isArray(ClassTemplate template) {
        return is(ARRAY, template);
    }

    public xRTDelegate delegate() {
        return get(RT_DELEGATE);
    }

    public boolean isDelegate(ClassTemplate template) {
        return is(RT_DELEGATE, template);
    }

    public xRTViewFromBit viewFromBit() {
        return get(RT_VIEW_FROM_BIT);
    }

    public boolean isViewFromBit(ClassTemplate template) {
        return is(RT_VIEW_FROM_BIT, template);
    }

    public xRTViewFromByte viewFromByte() {
        return get(RT_VIEW_FROM_BYTE);
    }

    public boolean isViewFromByte(ClassTemplate template) {
        return is(RT_VIEW_FROM_BYTE, template);
    }

    public xRTViewToBit viewToBit() {
        return get(RT_VIEW_TO_BIT);
    }

    public xRTSlicingDelegate slicingDelegate() {
        return get(RT_SLICING_DELEGATE);
    }

    public boolean isViewToBit(ClassTemplate template) {
        return is(RT_VIEW_TO_BIT, template);
    }

    public xContainerControl containerControl() {
        return get(CONTAINER_CONTROL);
    }

    public xContainerLinker containerLinker() {
        return get(CONTAINER_LINKER);
    }

    public xBasicHashCollector basicHashCollector() {
        return get(BASIC_HASH_COLLECTOR);
    }

    public xRTAlgorithms algorithms() {
        return get(RT_ALGORITHMS);
    }

    public xRTCertificateManager certificateManager() {
        return get(RT_CERTIFICATE_MANAGER);
    }

    public xRTKeyStore keyStore() {
        return get(RT_KEY_STORE);
    }

    public xTerminalConsole terminalConsole() {
        return get(TERMINAL_CONSOLE);
    }

    public xRTCompiler compiler() {
        return get(RT_COMPILER);
    }

    public xCoreRepository coreRepository() {
        return get(CORE_REPOSITORY);
    }

    public xRTNameService nameService() {
        return get(RT_NAME_SERVICE);
    }

    public xRTNetwork network() {
        return get(RT_NETWORK);
    }

    public xRTSocket socket() {
        return get(RT_SOCKET);
    }

    public xRTRandom random() {
        return get(RT_RANDOM);
    }

    public xRTClassTemplate classTemplate() {
        return get(RT_CLASS_TEMPLATE);
    }

    public boolean isClassTemplate(ClassTemplate template) {
        return is(RT_CLASS_TEMPLATE, template);
    }

    public xRTComponentTemplate componentTemplate() {
        return get(RT_COMPONENT_TEMPLATE);
    }

    public boolean isComponentTemplate(ClassTemplate template) {
        return is(RT_COMPONENT_TEMPLATE, template);
    }

    public xRTFunction function() {
        return get(RT_FUNCTION);
    }

    public xRTMethod method() {
        return get(RT_METHOD);
    }

    public xRTModuleTemplate moduleTemplate() {
        return get(RT_MODULE_TEMPLATE);
    }

    public xRTProperty property() {
        return get(RT_PROPERTY);
    }

    public xRTPropertyClassTemplate propertyClassTemplate() {
        return get(RT_PROPERTY_CLASS_TEMPLATE);
    }

    public xRTSignature signature() {
        return get(RT_SIGNATURE);
    }

    public boolean isPropertyClassTemplate(ClassTemplate template) {
        return is(RT_PROPERTY_CLASS_TEMPLATE, template);
    }

    public xRTType type() {
        return get(RT_TYPE);
    }

    public xRTTypeTemplate typeTemplate() {
        return get(RT_TYPE_TEMPLATE);
    }

    public xRTServiceControl serviceControl() {
        return get(RT_SERVICE_CONTROL);
    }

    public xLocalClock localClock() {
        return get(LOCAL_CLOCK);
    }

    public xNanosTimer nanosTimer() {
        return get(NANOS_TIMER);
    }

    public xRTConnector connector() {
        return get(RT_CONNECTOR);
    }

    public xRTServer server() {
        return get(RT_SERVER);
    }

    public xEnum enumTemplate() {
        return get(ENUM);
    }

    public boolean isEnum(ClassTemplate template) {
        return is(ENUM, template);
    }

    public xModule module() {
        return get(MODULE);
    }

    public boolean isModule(ClassTemplate template) {
        return is(MODULE, template);
    }

    public xPackage packageTemplate() {
        return get(PACKAGE);
    }

    public xService service() {
        return get(SERVICE);
    }

    public xInjector injector() {
        return get(INJECTOR);
    }

    public boolean isService(ClassTemplate template) {
        return is(SERVICE, template);
    }

    private <T extends ClassTemplate> boolean is(NativeTemplateRef<T> ref, ClassTemplate template) {
        return template != null && template == get(ref);
    }

    private <T extends ClassTemplate> T get(NativeTemplateRef<T> ref) {
        // Install the Lazy cell in the concurrent map, but resolve the template from Lazy.get().
        // Template resolution can recurse during bootstrap; doing that work inside computeIfAbsent()
        // would couple recursive runtime startup to ConcurrentHashMap's update path.
        Lazy<?> lazy = f_mapTemplates.computeIfAbsent(ref,
                refTemplate -> Lazy.of(() -> refTemplate.resolve(f_container)));

        return ref.cast((ClassTemplate) lazy.get());
    }


    // ----- immutable lookup keys ----------------------------------------------------------------

    private static final NativeTemplateRef<xArray> ARRAY =
            NativeTemplateRef.of("collections.Array", xArray.class);

    private static final NativeTemplateRef<xRTDelegate> RT_DELEGATE =
            NativeTemplateRef.of("_native.collections.arrays.RTDelegate", xRTDelegate.class);

    private static final NativeTemplateRef<xRTViewFromBit> RT_VIEW_FROM_BIT =
            NativeTemplateRef.of("_native.collections.arrays.RTViewFromBit", xRTViewFromBit.class);

    private static final NativeTemplateRef<xRTViewFromByte> RT_VIEW_FROM_BYTE =
            NativeTemplateRef.of("_native.collections.arrays.RTViewFromByte", xRTViewFromByte.class);

    private static final NativeTemplateRef<xRTViewToBit> RT_VIEW_TO_BIT =
            NativeTemplateRef.of("_native.collections.arrays.RTViewToBit", xRTViewToBit.class);

    private static final NativeTemplateRef<xRTSlicingDelegate> RT_SLICING_DELEGATE =
            NativeTemplateRef.of("_native.collections.arrays.RTSlicingDelegate",
                    xRTSlicingDelegate.class);

    private static final NativeTemplateRef<xListMap> LIST_MAP =
            NativeTemplateRef.of("maps.ListMap", xListMap.class);

    private static final NativeTemplateRef<xTuple> TUPLE =
            NativeTemplateRef.of("collections.Tuple", xTuple.class);

    private static final NativeTemplateRef<xOSDirectory> OS_DIRECTORY =
            NativeTemplateRef.of("_native.fs.OSDirectory", xOSDirectory.class);

    private static final NativeTemplateRef<xOSFile> OS_FILE =
            NativeTemplateRef.of("_native.fs.OSFile", xOSFile.class);

    private static final NativeTemplateRef<xRawOSFileChannel> RAW_OS_FILE_CHANNEL =
            NativeTemplateRef.of("_native.fs.RawOSFileChannel", xRawOSFileChannel.class);

    private static final NativeTemplateRef<xContainerControl> CONTAINER_CONTROL =
            NativeTemplateRef.of("_native.mgmt.ContainerControl", xContainerControl.class);

    private static final NativeTemplateRef<xContainerLinker> CONTAINER_LINKER =
            NativeTemplateRef.of("_native.mgmt.ContainerLinker", xContainerLinker.class);

    private static final NativeTemplateRef<xBasicHashCollector> BASIC_HASH_COLLECTOR =
            NativeTemplateRef.of("_native.collections.BasicHashCollector",
                    xBasicHashCollector.class);

    private static final NativeTemplateRef<xRTAlgorithms> RT_ALGORITHMS =
            NativeTemplateRef.of("_native.crypto.RTAlgorithms", xRTAlgorithms.class);

    private static final NativeTemplateRef<xRTCertificateManager> RT_CERTIFICATE_MANAGER =
            NativeTemplateRef.of("_native.crypto.RTCertificateManager",
                    xRTCertificateManager.class);

    private static final NativeTemplateRef<xRTKeyStore> RT_KEY_STORE =
            NativeTemplateRef.of("_native.crypto.RTKeyStore", xRTKeyStore.class);

    private static final NativeTemplateRef<xTerminalConsole> TERMINAL_CONSOLE =
            NativeTemplateRef.of("_native.io.TerminalConsole", xTerminalConsole.class);

    private static final NativeTemplateRef<xRTCompiler> RT_COMPILER =
            NativeTemplateRef.of("_native.lang.src.RTCompiler", xRTCompiler.class);

    private static final NativeTemplateRef<xCoreRepository> CORE_REPOSITORY =
            NativeTemplateRef.of("_native.mgmt.CoreRepository", xCoreRepository.class);

    private static final NativeTemplateRef<xRTNameService> RT_NAME_SERVICE =
            NativeTemplateRef.of("_native.net.RTNameService", xRTNameService.class);

    private static final NativeTemplateRef<xRTNetwork> RT_NETWORK =
            NativeTemplateRef.of("_native.net.RTNetwork", xRTNetwork.class);

    private static final NativeTemplateRef<xRTSocket> RT_SOCKET =
            NativeTemplateRef.of("_native.net.RTSocket", xRTSocket.class);

    private static final NativeTemplateRef<xRTRandom> RT_RANDOM =
            NativeTemplateRef.of("_native.numbers.RTRandom", xRTRandom.class);

    private static final NativeTemplateRef<xRTClassTemplate> RT_CLASS_TEMPLATE =
            NativeTemplateRef.of("_native.reflect.RTClassTemplate", xRTClassTemplate.class);

    private static final NativeTemplateRef<xRTComponentTemplate> RT_COMPONENT_TEMPLATE =
            NativeTemplateRef.of("_native.reflect.RTComponentTemplate", xRTComponentTemplate.class);

    private static final NativeTemplateRef<xRTFunction> RT_FUNCTION =
            NativeTemplateRef.of("_native.reflect.RTFunction", xRTFunction.class);

    private static final NativeTemplateRef<xRTMethod> RT_METHOD =
            NativeTemplateRef.of("_native.reflect.RTMethod", xRTMethod.class);

    private static final NativeTemplateRef<xRTModuleTemplate> RT_MODULE_TEMPLATE =
            NativeTemplateRef.of("_native.reflect.RTModuleTemplate", xRTModuleTemplate.class);

    private static final NativeTemplateRef<xRTProperty> RT_PROPERTY =
            NativeTemplateRef.of("_native.reflect.RTProperty", xRTProperty.class);

    private static final NativeTemplateRef<xRTPropertyClassTemplate> RT_PROPERTY_CLASS_TEMPLATE =
            NativeTemplateRef.of("_native.reflect.RTPropertyClassTemplate",
                    xRTPropertyClassTemplate.class);

    private static final NativeTemplateRef<xRTSignature> RT_SIGNATURE =
            NativeTemplateRef.of("_native.reflect.RTSignature", xRTSignature.class);

    private static final NativeTemplateRef<xRTType> RT_TYPE =
            NativeTemplateRef.of("_native.reflect.RTType", xRTType.class);

    private static final NativeTemplateRef<xRTTypeTemplate> RT_TYPE_TEMPLATE =
            NativeTemplateRef.of("_native.reflect.RTTypeTemplate", xRTTypeTemplate.class);

    private static final NativeTemplateRef<xRTServiceControl> RT_SERVICE_CONTROL =
            NativeTemplateRef.of("_native.RTServiceControl", xRTServiceControl.class);

    private static final NativeTemplateRef<xLocalClock> LOCAL_CLOCK =
            NativeTemplateRef.of("_native.temporal.LocalClock", xLocalClock.class);

    private static final NativeTemplateRef<xNanosTimer> NANOS_TIMER =
            NativeTemplateRef.of("_native.temporal.NanosTimer", xNanosTimer.class);

    private static final NativeTemplateRef<xRTConnector> RT_CONNECTOR =
            NativeTemplateRef.of("_native.web.RTConnector", xRTConnector.class);

    private static final NativeTemplateRef<xRTServer> RT_SERVER =
            NativeTemplateRef.of("_native.web.RTServer", xRTServer.class);

    private static final NativeTemplateRef<xEnum> ENUM =
            NativeTemplateRef.of("Enum", xEnum.class);

    private static final NativeTemplateRef<xModule> MODULE =
            NativeTemplateRef.of("reflect.Module", xModule.class);

    private static final NativeTemplateRef<xPackage> PACKAGE =
            NativeTemplateRef.of("reflect.Package", xPackage.class);

    private static final NativeTemplateRef<xService> SERVICE =
            NativeTemplateRef.of("Service", xService.class);

    private static final NativeTemplateRef<xInjector> INJECTOR =
            NativeTemplateRef.of("reflect.Injector", xInjector.class);


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The owning container.
     */
    private final Container f_container;

    /**
     * Specialized array templates are selected by the array element type; resolving them by the mixin
     * name would return the generic xObject fallback for the mixin itself.
     */
    private final Lazy<xBitArray> f_templateBit;

    /**
     * Specialized array templates are selected by the array element type; resolving them by the mixin
     * name would return the generic xObject fallback for the mixin itself.
     */
    private final Lazy<xByteArray> f_templateByte;

    /**
     * Specialized array templates are selected by the array element type; resolving them by the mixin
     * name would return the generic xObject fallback for the mixin itself.
     */
    private final Lazy<xNibbleArray> f_templateNibble;

    /**
     * Lazily resolved templates by immutable native-template key.
     */
    private final ConcurrentMap<NativeTemplateRef<?>, Lazy<?>> f_mapTemplates =
            new ConcurrentHashMap<>();
}
