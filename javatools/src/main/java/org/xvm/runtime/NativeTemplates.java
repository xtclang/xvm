package org.xvm.runtime;


import java.util.List;

import java.util.function.Function;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;
import org.xvm.runtime.template.collections.xNibbleArray;
import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.maps.xListMap;

import org.xvm.runtime.template.annotations.xFuture;

import org.xvm.runtime.template.numbers.xBit;
import org.xvm.runtime.template.numbers.xDec128;
import org.xvm.runtime.template.numbers.xDec32;
import org.xvm.runtime.template.numbers.xDec64;
import org.xvm.runtime.template.numbers.xFloat32;
import org.xvm.runtime.template.numbers.xFloat64;
import org.xvm.runtime.template.numbers.xInt128;
import org.xvm.runtime.template.numbers.xInt16;
import org.xvm.runtime.template.numbers.xInt32;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xInt8;
import org.xvm.runtime.template.numbers.xIntN;
import org.xvm.runtime.template.numbers.xNibble;
import org.xvm.runtime.template.numbers.xUInt128;
import org.xvm.runtime.template.numbers.xUInt16;
import org.xvm.runtime.template.numbers.xUInt32;
import org.xvm.runtime.template.numbers.xUInt64;
import org.xvm.runtime.template.numbers.xUInt8;
import org.xvm.runtime.template.numbers.xUIntN;

import org.xvm.runtime.template.reflect.xModule;
import org.xvm.runtime.template.reflect.xPackage;
import org.xvm.runtime.template.reflect.xInjector;
import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template.text.xChar;
import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template.Identity;
import org.xvm.runtime.template.Proxy;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;
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
        f_templateBit  = Lazy.ofBound(owner -> owner.container().getTemplate(
                owner.container().getConstantPool().typeBitArray(), xBitArray.class));
        f_templateByte = Lazy.ofBound(owner -> owner.container().getTemplate(
                owner.container().getConstantPool().typeByteArray(), xByteArray.class));
        f_templateNibble = Lazy.ofBound(owner -> {
            var pool = owner.container().getConstantPool();
            return owner.container().getTemplate(pool.ensureArrayType(pool.typeNibble()),
                    xNibbleArray.class);
        });
        f_templateRef = Lazy.ofBound(owner -> owner.container().getTemplate(
                owner.container().getConstantPool().typeRef(), xRef.class));
        f_templateVar = Lazy.ofBound(owner -> owner.container().getTemplate(
                owner.container().getConstantPool().typeVar(), xVar.class));
        f_templateProxy = Lazy.ofBound(owner -> new Proxy(owner.container()));
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
        return get(requireNonNull(template, "template").container());
    }

    public xArray array() {
        return get(ARRAY);
    }

    public xBitArray bitArray() {
        return f_templateBit.get(this);
    }

    public xByteArray byteArray() {
        return f_templateByte.get(this);
    }

    public xNibbleArray nibbleArray() {
        return f_templateNibble.get(this);
    }

    public xListMap listMap() {
        return get(LIST_MAP);
    }

    public xTuple tuple() {
        return get(TUPLE);
    }

    public xFuture future() {
        return get(FUTURE);
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

    public xRTDelegate<?> delegate() {
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

    public xRTViewToBit<?> viewToBit() {
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

    public xBit bit() {
        return get(BIT);
    }

    public xDec32 dec32() {
        return get(DEC32);
    }

    public xDec64 dec64() {
        return get(DEC64);
    }

    public xDec128 dec128() {
        return get(DEC128);
    }

    public xFloat32 float32() {
        return get(FLOAT32);
    }

    public xFloat64 float64() {
        return get(FLOAT64);
    }

    public xNibble nibble() {
        return get(NIBBLE);
    }

    public xChar charTemplate() {
        return get(CHAR);
    }

    public xInt8 int8() {
        return get(INT8);
    }

    public xInt16 int16() {
        return get(INT16);
    }

    public xInt32 int32() {
        return get(INT32);
    }

    public xInt64 int64() {
        return get(INT64);
    }

    public xInt128 int128() {
        return get(INT128);
    }

    public xIntN intN() {
        return get(INT_N);
    }

    public xUInt8 uint8() {
        return get(UINT8);
    }

    public xUInt16 uint16() {
        return get(UINT16);
    }

    public xUInt32 uint32() {
        return get(UINT32);
    }

    public xUInt64 uint64() {
        return get(UINT64);
    }

    public xUInt128 uint128() {
        return get(UINT128);
    }

    public xUIntN uintN() {
        return get(UINT_N);
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

    public xBoolean booleanTemplate() {
        return get(BOOLEAN);
    }

    public xNullable nullable() {
        return get(NULLABLE);
    }

    public xOrdered ordered() {
        return get(ORDERED);
    }

    public xConst constTemplate() {
        return get(CONST);
    }

    public boolean isConst(ClassTemplate template) {
        return is(CONST, template);
    }

    public xException exception() {
        return get(EXCEPTION);
    }

    public boolean isException(ClassTemplate template) {
        return is(EXCEPTION, template);
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

    public xObject object() {
        return get(OBJECT);
    }

    public Identity identity() {
        return get(IDENTITY);
    }

    public Proxy proxy() {
        return f_templateProxy.get(this);
    }

    public xString string() {
        return get(STRING);
    }

    public xRef ref() {
        return f_templateRef.get(this);
    }

    public xVar var() {
        return f_templateVar.get(this);
    }

    public xInjector injector() {
        return get(INJECTOR);
    }

    public boolean isService(ClassTemplate template) {
        return is(SERVICE, template);
    }

    public boolean isObject(ClassTemplate template) {
        return is(OBJECT, template);
    }

    private <T extends ClassTemplate> boolean is(NativeTemplateRef<T> ref, ClassTemplate template) {
        return template != null && template == get(ref);
    }

    private <T extends ClassTemplate> T get(NativeTemplateRef<T> ref) {
        // Install the Lazy cell in the concurrent map, but resolve the template from Lazy.get().
        // Template resolution can recurse during bootstrap; doing that work inside computeIfAbsent()
        // would couple recursive runtime startup to ConcurrentHashMap's update path.
        Lazy.Bound<NativeTemplates, ?> lazy = f_mapTemplates.computeIfAbsent(ref,
                refTemplate -> Lazy.ofBound(owner -> refTemplate.resolve(owner.container())));

        return ref.cast((ClassTemplate) lazy.get(this));
    }

    private Container container() {
        return f_container;
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

    private static final NativeTemplateRef<xFuture> FUTURE =
            NativeTemplateRef.of("annotations.Future", xFuture.class);

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

    private static final NativeTemplateRef<xBit> BIT =
            NativeTemplateRef.of("numbers.Bit", xBit.class);

    private static final NativeTemplateRef<xDec32> DEC32 =
            NativeTemplateRef.of("numbers.Dec32", xDec32.class);

    private static final NativeTemplateRef<xDec64> DEC64 =
            NativeTemplateRef.of("numbers.Dec64", xDec64.class);

    private static final NativeTemplateRef<xDec128> DEC128 =
            NativeTemplateRef.of("numbers.Dec128", xDec128.class);

    private static final NativeTemplateRef<xFloat32> FLOAT32 =
            NativeTemplateRef.of("numbers.Float32", xFloat32.class);

    private static final NativeTemplateRef<xFloat64> FLOAT64 =
            NativeTemplateRef.of("numbers.Float64", xFloat64.class);

    private static final NativeTemplateRef<xNibble> NIBBLE =
            NativeTemplateRef.of("numbers.Nibble", xNibble.class);

    private static final NativeTemplateRef<xChar> CHAR =
            NativeTemplateRef.of("text.Char", xChar.class);

    private static final NativeTemplateRef<xInt8> INT8 =
            NativeTemplateRef.of("numbers.Int8", xInt8.class);

    private static final NativeTemplateRef<xInt16> INT16 =
            NativeTemplateRef.of("numbers.Int16", xInt16.class);

    private static final NativeTemplateRef<xInt32> INT32 =
            NativeTemplateRef.of("numbers.Int32", xInt32.class);

    private static final NativeTemplateRef<xInt64> INT64 =
            NativeTemplateRef.of("numbers.Int64", xInt64.class);

    private static final NativeTemplateRef<xInt128> INT128 =
            NativeTemplateRef.of("numbers.Int128", xInt128.class);

    private static final NativeTemplateRef<xIntN> INT_N =
            NativeTemplateRef.of("numbers.IntN", xIntN.class);

    private static final NativeTemplateRef<xUInt8> UINT8 =
            NativeTemplateRef.of("numbers.UInt8", xUInt8.class);

    private static final NativeTemplateRef<xUInt16> UINT16 =
            NativeTemplateRef.of("numbers.UInt16", xUInt16.class);

    private static final NativeTemplateRef<xUInt32> UINT32 =
            NativeTemplateRef.of("numbers.UInt32", xUInt32.class);

    private static final NativeTemplateRef<xUInt64> UINT64 =
            NativeTemplateRef.of("numbers.UInt64", xUInt64.class);

    private static final NativeTemplateRef<xUInt128> UINT128 =
            NativeTemplateRef.of("numbers.UInt128", xUInt128.class);

    private static final NativeTemplateRef<xUIntN> UINT_N =
            NativeTemplateRef.of("numbers.UIntN", xUIntN.class);

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

    private static final NativeTemplateRef<xBoolean> BOOLEAN =
            NativeTemplateRef.of("Boolean", xBoolean.class);

    private static final NativeTemplateRef<xNullable> NULLABLE =
            NativeTemplateRef.of("Nullable", xNullable.class);

    private static final NativeTemplateRef<xOrdered> ORDERED =
            NativeTemplateRef.of("Ordered", xOrdered.class);

    private static final NativeTemplateRef<xConst> CONST =
            NativeTemplateRef.of("Const", xConst.class);

    private static final NativeTemplateRef<xException> EXCEPTION =
            NativeTemplateRef.of("Exception", xException.class);

    private static final NativeTemplateRef<xModule> MODULE =
            NativeTemplateRef.of("reflect.Module", xModule.class);

    private static final NativeTemplateRef<xPackage> PACKAGE =
            NativeTemplateRef.of("reflect.Package", xPackage.class);

    private static final NativeTemplateRef<xService> SERVICE =
            NativeTemplateRef.of("Service", xService.class);

    private static final NativeTemplateRef<xObject> OBJECT =
            NativeTemplateRef.of("Object", xObject.class);

    private static final NativeTemplateRef<Identity> IDENTITY =
            NativeTemplateRef.of("reflect.Ref.Identity", Identity.class);

    private static final NativeTemplateRef<xString> STRING =
            NativeTemplateRef.of("text.String", xString.class);

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
    private final Lazy.Bound<NativeTemplates, xBitArray> f_templateBit;

    /**
     * Specialized array templates are selected by the array element type; resolving them by the mixin
     * name would return the generic xObject fallback for the mixin itself.
     */
    private final Lazy.Bound<NativeTemplates, xByteArray> f_templateByte;

    /**
     * Specialized array templates are selected by the array element type; resolving them by the mixin
     * name would return the generic xObject fallback for the mixin itself.
     */
    private final Lazy.Bound<NativeTemplates, xNibbleArray> f_templateNibble;

    /**
     * Canonical owner Ref template. Ref-derived templates inherit this template's
     * native rebase and get-signature metadata; they must not recompute it from
     * their own reflected structures.
     */
    private final Lazy.Bound<NativeTemplates, xRef> f_templateRef;

    /**
     * Canonical owner Var template. Var-derived templates inherit this template's
     * native rebase and set-signature metadata; they must not recompute it from
     * their own reflected structures.
     */
    private final Lazy.Bound<NativeTemplates, xVar> f_templateVar;

    /**
     * Owner-local proxy support object. Proxy is not a normal registered native template; the
     * legacy runtime constructed one global helper from Service registration. This preserves that
     * helper object, but scopes it to the container that owns the proxy composition.
     */
    private final Lazy.Bound<NativeTemplates, Proxy> f_templateProxy;

    /**
     * A named, typed key for an owner-local value this container derives once.
     *
     * <p>The point is that a call site asks for a key and gets a value, instead of reaching into
     * some template's field. State that used to live in a mutable static became a {@code Lazy.Bound}
     * field on the owning template, which fixed the ownership but left the value reachable only by
     * naming another object's field from outside it - and so still not instrumentable in one place,
     * which was the whole reason for moving it.</p>
     *
     * <p>The key carries the value's type, so one heterogeneous map serves every kind of cached
     * value without a cast at the call site.</p>
     *
     * @param <T> the type of value this key resolves to
     */
    public static final class CacheKey<T> {
        private final String f_sName;
        private final Function<Container, T> f_resolve;
        private final boolean f_fPlaneWide;

        private CacheKey(String sName, Function<Container, T> resolve, boolean fPlaneWide) {
            f_sName      = requireNonNull(sName,   "sName");
            f_resolve    = requireNonNull(resolve, "resolve");
            f_fPlaneWide = fPlaneWide;
        }

        /**
         * Declare a value that belongs to the container plane: metadata every container in the
         * plane shares, of the kind {@code initNative()} derived once on the native container.
         *
         * <p>The resolver is handed the <b>native</b> container, and the value is cached there, so
         * a plane-wide value cannot come to be derived from - or owned by - whichever container
         * happened to ask for it first. That was a live mistake, not a hypothetical one: an earlier
         * pass at this took the pool from the asking container and would have moved plane metadata
         * onto the first asker.</p>
         *
         * @param sName    what the value is, for diagnostics
         * @param resolve  how to derive it, given the native container
         */
        public static <T> CacheKey<T> ofPlane(String sName, Function<Container, T> resolve) {
            return new CacheKey<>(sName, resolve, true);
        }

        /**
         * Declare a value that belongs to the container that asks for it - a composition or a
         * handle, which {@code ensureClassComposition} and friends deliberately do not share
         * upward.
         *
         * <p>The resolver is handed the asking container. Where such a value is derived from
         * plane-wide inputs, the resolver reaches the plane explicitly through
         * {@link Container#getNativeContainer()}, so the mixture is visible at the key.</p>
         *
         * @param sName    what the value is, for diagnostics
         * @param resolve  how to derive it, given the asking container
         */
        public static <T> CacheKey<T> ofContainer(String sName, Function<Container, T> resolve) {
            return new CacheKey<>(sName, resolve, false);
        }

        boolean isPlaneWide() {
            return f_fPlaneWide;
        }

        @Override
        public String toString() {
            return f_sName;
        }
    }

    /**
     * Obtain a value this container derives once and keeps.
     *
     * @param key  the key naming the value
     *
     * @return the value for this container
     */
    public <T> T get(CacheKey<T> key) {
        requireNonNull(key, "key");

        // A plane-wide value is resolved and cached on the plane, not here. Delegating rather than
        // trusting each key's resolver to reach for the right container is what makes the ownership
        // structural: a resolver is only ever handed the container its key declared.
        if (key.isPlaneWide()) {
            Container containerNative = f_container.getNativeContainer();
            if (containerNative != null && containerNative != f_container) {
                return NativeTemplates.get(containerNative).get(key);
            }
        }

        // Resolve outside the map's update path: deriving one value can ask for another, and
        // computeIfAbsent() forbids a recursive update to the same map.
        Lazy.Bound<NativeTemplates, ?> lazy = f_mapCached.computeIfAbsent(key,
                k -> Lazy.ofBound(owner -> ((CacheKey<?>) k).f_resolve.apply(owner.container())));

        @SuppressWarnings("unchecked")   // the key's resolver produces T, by construction
        T value = (T) lazy.get(this);
        return value;
    }

    /**
     * The keys this container has actually resolved. Owner-local cached state is now one map, so
     * what a container has derived is a question with one place to ask it.
     */
    public List<String> resolvedKeys() {
        return f_mapCached.keySet().stream().map(CacheKey::toString).sorted().toList();
    }

    /**
     * Lazily resolved templates by immutable native-template key.
     */
    private final ConcurrentMap<NativeTemplateRef<?>, Lazy.Bound<NativeTemplates, ?>> f_mapTemplates =
            new ConcurrentHashMap<>();

    /**
     * Owner-local values derived once per container, by key.
     */
    private final ConcurrentMap<CacheKey<?>, Lazy.Bound<NativeTemplates, ?>> f_mapCached =
            new ConcurrentHashMap<>();
}
