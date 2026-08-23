package org.xvm.asm;


import java.lang.ref.WeakReference;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.nio.file.attribute.FileTime;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.DynamicFormalConstant;
import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MethodBindingConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.runtime.ObjectHandle;

import org.xvm.util.TransientThreadLocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link Constant#adoptedBy(ConstantPool)} ownership handoff behavior.
 */
public class ConstantAdoptionTest {
    /**
     * TypeConstant adoption must not copy owner-local helper state. A shallow clone can look fine
     * single-threaded but later reuse source-pool recursion or cache helpers in the target pool.
     */
    @Test
    public void adoptedTypeConstantClearsOwnerLocalHelperState()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        ParameterizedTypeConstant source = (ParameterizedTypeConstant)
                sourcePool.ensureParameterizedTypeConstant(
                        sourcePool.typeArray(), sourcePool.typeString());
        AtomicInteger sourceDepth = fieldValue(source, "m_cRecursiveDepth");
        Object tlo = new TransientThreadLocal<Set<TypeConstant>>();

        sourceDepth.set(7);
        setField(source, "m_tloInProgress", tlo);
        setField(source, "m_mapConsumes", Map.of("source", new Object()));
        setField(source, "m_mapProduces", Map.of("source", new Object()));
        setField(source, "m_sJitName", "SourceJitName");
        setField(source, "m_typeNormalized", source);

        ParameterizedTypeConstant adopted = adopt(source, targetPool);

        assertNotSame(source, adopted);
        assertSame(sourceDepth, fieldValue(source, "m_cRecursiveDepth"));
        assertNotSame(sourceDepth, fieldValue(adopted, "m_cRecursiveDepth"));
        assertEquals(0, ((AtomicInteger) fieldValue(adopted, "m_cRecursiveDepth")).get());
        assertNull(fieldValue(adopted, "m_tloInProgress"));
        assertNull(fieldValue(adopted, "m_mapConsumes"));
        assertNull(fieldValue(adopted, "m_mapProduces"));
        assertNull(fieldValue(adopted, "m_sJitName"));
        assertNull(fieldValue(adopted, "m_typeNormalized"));
    }

    /**
     * ParameterizedTypeConstant has subclass helper state in addition to base TypeConstant state.
     * Adoption must give the target pool fresh helpers rather than copied source-owner cells.
     */
    @Test
    public void adoptedParameterizedTypeConstantGetsFreshSubclassHelpers()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        ParameterizedTypeConstant source = (ParameterizedTypeConstant)
                sourcePool.ensureParameterizedTypeConstant(
                        sourcePool.typeArray(), sourcePool.typeString());
        Object sourceLock = fieldValue(source, "m_lockPrev");

        setField(source, "m_typeResolverPrev", sourcePool.typeObject());
        setField(source, "m_typeResolvedPrev", sourcePool.typeString());
        setField(source, "m_typeJitCallable", sourcePool.typeObject());

        ParameterizedTypeConstant adopted = adopt(source, targetPool);

        assertNotSame(sourceLock, fieldValue(adopted, "m_lockPrev"));
        assertNull(fieldValue(adopted, "m_typeResolverPrev"));
        assertNull(fieldValue(adopted, "m_typeResolvedPrev"));
        assertNull(fieldValue(adopted, "m_typeJitCallable"));
    }

    /**
     * SignatureConstant adoption must not copy comparison or JIT helper state. Otherwise a target
     * pool can inherit source-owner caches through the default clone path.
     */
    @Test
    public void adoptedSignatureConstantGetsFreshComparisonAndJitHelpers()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        TypeConstant[] params = {sourcePool.typeString()};
        SignatureConstant source = sourcePool.ensureSignatureConstant("demo", ConstantPool.NO_TYPES, params);
        SignatureConstant previous = sourcePool.ensureSignatureConstant(
                "other", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        Object sourceLock = fieldValue(source, "m_lockPrev");

        setField(source, "m_sigPrev", previous);
        setField(source, "m_refSigPrev", new WeakReference<>(previous));
        setField(source, "m_nCmpPrev", 42);
        setField(source, "m_sJitName", "SourceJitMethod");
        setField(source, "m_fProperty", true);

        SignatureConstant adopted = adopt(source, targetPool);

        assertNotSame(sourceLock, fieldValue(adopted, "m_lockPrev"));
        assertNull(fieldValue(adopted, "m_sigPrev"));
        assertNull(fieldValue(adopted, "m_refSigPrev"));
        assertEquals(0, ((Integer) fieldValue(adopted, "m_nCmpPrev")).intValue());
        assertNull(fieldValue(adopted, "m_sJitName"));
        assertTrue((Boolean) fieldValue(adopted, "m_fProperty"));
    }

    /**
     * TypeParameterConstant uses a reentry cell during comparisons. Adoption must allocate a fresh
     * cell so recursive comparisons in one pool cannot share thread-local state with another.
     */
    @Test
    public void adoptedTypeParameterConstantGetsFreshReentryCell()
            throws Exception {
        FileStructure sourceFile = new FileStructure("source");
        ConstantPool  sourcePool = sourceFile.getConstantPool();
        ConstantPool  targetPool = new FileStructure("target").getConstantPool();
        ClassStructure struct = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        SignatureConstant sig = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant method = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        TypeParameterConstant source = sourcePool.ensureRegisterConstant(method, 0, "T");
        Object sourceReentry = fieldValue(source, "f_tloReEntry");

        setField(source, "m_typeConstraint", sourcePool.typeString());

        TypeParameterConstant adopted = adopt(source, targetPool);

        assertNotSame(sourceReentry, fieldValue(adopted, "f_tloReEntry"));
        assertNull(fieldValue(adopted, "m_typeConstraint"));
    }

    /**
     * MethodConstant identity is logical ASM metadata, but its cached type and JIT method name are
     * owner/type-system helper state. Adoption must preserve parent/signature identity without
     * carrying those caches to another pool or future JIT TypeSystem.
     */
    @Test
    public void adoptedMethodConstantDropsTypeAndJitCaches() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var source     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);

        setField(source, "m_type", sourcePool.typeObject());
        setField(source, "m_sJitName", "SourceJitMethod");

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertNull(fieldValue(adopted, "m_type"));
        assertNull(fieldValue(adopted, "m_sJitName"));
    }

    /**
     * PropertyConstant adoption must not carry cached property type, synthetic signature,
     * constraint, or JIT-name state to another pool. Those values are derived from the target owner
     * or a future JIT TypeSystem, while the logical property identity is only parent + name.
     */
    @Test
    public void adoptedPropertyConstantDropsMetadataAndJitCaches() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var source     = sourcePool.ensurePropertyConstant(struct.getIdentityConstant(), "prop");
        var sig        = sourcePool.ensureSignatureConstant(
                "prop", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);

        setField(source, "m_type", sourcePool.typeString());
        setField(source, "m_constSig", sig);
        setField(source, "m_typeConstraint", sourcePool.typeString());
        setField(source, "m_sJitName", "SourceJitProperty");

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertNull(fieldValue(adopted, "m_type"));
        assertNull(fieldValue(adopted, "m_constSig"));
        assertNull(fieldValue(adopted, "m_typeConstraint"));
        assertNull(fieldValue(adopted, "m_sJitName"));
    }

    /**
     * FormalTypeChildConstant inherits PropertyConstant cache fields, but its format is different
     * and must survive adoption. A base property copy hook would silently corrupt this identity.
     */
    @Test
    public void adoptedFormalTypeChildPreservesFormatAndDropsInheritedCaches() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var parent     = sourcePool.ensureRegisterConstant(method, 0, "T");
        var source     = sourcePool.ensureFormalTypeChildConstant(parent, "Element");

        setField(source, "m_typeConstraint", sourcePool.typeObject());
        setField(source, "m_sJitName", "SourceFormalChildJitProperty");

        var adopted = adopt(source, targetPool);

        assertSame(Constant.Format.FormalTypeChild, adopted.getFormat());
        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertNull(fieldValue(adopted, "m_typeConstraint"));
        assertNull(fieldValue(adopted, "m_sJitName"));
    }

    /**
     * DynamicFormalConstant stores enough logical register identity to assemble and compare after
     * register allocation. Adoption must copy that stable identity while dropping the transient
     * compiler Register object, which belongs to the source compiler/method owner. Registered
     * type constants may still use TypeConstant sharing rules; they just must be valid for the
     * target pool.
     */
    @Test
    public void adoptedDynamicFormalDropsCompileTimeRegister() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var formal     = sourcePool.ensurePropertyConstant(struct.getIdentityConstant(), "Element");
        var reg        = new Register(struct.getIdentityConstant().getType(), "value", 3);
        var source     = sourcePool.ensureDynamicFormal(method, reg, formal, "value");

        var adopted = adopt(source, targetPool);
        var registered = targetPool.register(source);

        assertNull(adopted.getRegister());
        assertEquals(reg.getIndex(), adopted.getRegisterIndex());
        assertSame(targetPool, registered.getConstantPool());
        assertNull(registered.getRegister());
        assertEquals(reg.getIndex(), registered.getRegisterIndex());
        assertSame(targetPool, registered.getFormalConstant().getConstantPool());
        var registeredType = (TypeConstant) fieldValue(registered, "m_typeReg");
        assertTrue(registeredType.isShared(targetPool));
    }

    /**
     * A dynamic formal whose register type names an unrelated module-local class cannot be moved
     * into another pool by copying references. The old shallow clone allowed that invalid foreign
     * type to sit inside the target constant until later linkage or runtime code tripped over it.
     */
    @Test
    public void dynamicFormalRejectsForeignRegisterTypeDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var formal     = sourcePool.ensurePropertyConstant(struct.getIdentityConstant(), "Element");
        var reg        = new Register(struct.getIdentityConstant().getType(), "value", 3);
        var source     = sourcePool.ensureDynamicFormal(method, reg, formal, "value");

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("foreign register type"));
    }

    /**
     * RegisterConstant serializes the register index, not the compiler Register object. Adoption
     * must therefore preserve the runtime index while dropping the source compiler register and
     * its owner/type state.
     */
    @Test
    public void adoptedRegisterConstantDropsCompileTimeRegister() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var reg        = new Register(sourcePool.typeObject(), "value", 3);
        var source     = new RegisterConstant(sourcePool, reg);

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertNull(adopted.getRegister());
        assertEquals(reg.getIndex(), adopted.getRegisterIndex());
        assertEquals(targetPool.typeObject(), adopted.getType());
    }

    /**
     * An unknown compiler Register can still be assigned later. The old shallow clone copied that
     * moving object to another pool; dropping it would freeze the wrong index, so adoption now
     * rejects this shape until allocation has completed.
     */
    @Test
    public void registerConstantRejectsUnknownRegisterAdoption() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var reg        = new Register(sourcePool.typeObject(), "value", (MethodStructure) null);
        var source     = new RegisterConstant(sourcePool, reg);

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("before register allocation"));
    }

    /**
     * MethodBindingConstant is a serialized frame-dependent descriptor, not a live runtime handle.
     * Adoption should reconstruct the method-binding value and let target registration adopt the
     * method identity, closing the old FrameDependentConstant default-clone fallback.
     */
    @Test
    public void adoptedMethodBindingConstantPreservesMethodIdentity() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var source     = new MethodBindingConstant(sourcePool, method);

        var adopted = adopt(source, targetPool);
        var registered = targetPool.register(source);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(method.getName(), adopted.getMethodConstant().getName());
        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getMethodConstant().getConstantPool());
        assertEquals(method.getName(), registered.getMethodConstant().getName());
    }

    /**
     * A live ObjectHandle is owner-specific runtime state, not logical constant data. Moving an
     * already-owned handle constant to another pool must fail instead of leaking the source owner.
     */
    @Test
    public void ownedHandleConstantCannotMoveToAnotherPool() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        HandleConstant unowned = new HandleConstant(ObjectHandle.DEFAULT);
        HandleConstant source = adopt(unowned, sourcePool);

        assertSame(sourcePool, source.getConstantPool());

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> adopt(source, targetPool));
        assertTrue(error.getMessage().contains("live ObjectHandle"));
    }

    /**
     * FSNodeConstant caches source-pool path handles. Adoption must drop those handles so a target
     * pool cannot observe filesystem handles owned by the source pool.
     */
    @Test
    public void adoptedFSNodeConstantDropsSourcePoolPathCache() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();
        FileTime     created    = FileTime.fromMillis(1);
        FileTime     modified   = FileTime.fromMillis(2);

        byte[] contents = {1, 2, 3};
        FSNodeConstant source = sourcePool.ensureFileConstant("demo.txt", created, modified, contents);
        LiteralConstant sourcePath = source.getPathConstant();

        FSNodeConstant adopted = adopt(source, targetPool);
        LiteralConstant adoptedPath = adopted.getPathConstant();

        assertSame(sourcePool, sourcePath.getConstantPool());
        assertSame(targetPool, adopted.getConstantPool());
        assertSame(targetPool, adoptedPath.getConstantPool());
        assertNotSame(sourcePath, adoptedPath);
        assertSame(adoptedPath, adopted.getPathConstant());
    }

    /**
     * Base Constant adoption is no longer "clone unless somebody remembered to override it". A new
     * constant class must either provide an explicit adoption implementation or opt in to the
     * transitional default-clone policy with a local explanation.
     */
    @Test
    public void defaultAdoptionCloneRequiresExplicitPolicy() {
        var targetPool = new FileStructure("target").getConstantPool();
        var source     = new NoPolicyConstant(new FileStructure("source").getConstantPool());

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("default adoption-clone policy"));
    }

    /**
     * Adoption must go through one owner-transfer wrapper. The old design allowed each subclass to
     * override {@code adoptedBy(...)} directly, which made it easy to copy runtime/helper state and
     * forget the common owner/ref checks. The reviewed special cases now implement only the logical
     * copy hook.
     */
    @Test
    public void adoptionWrapperIsFinalAndSpecialCasesUseCopyHook()
            throws Exception {
        var adoptedBy = Constant.class.getDeclaredMethod("adoptedBy", ConstantPool.class);

        assertTrue(Modifier.isFinal(adoptedBy.getModifiers()));

        Set.of(FSNodeConstant.class,
               DynamicFormalConstant.class,
               FileStoreConstant.class,
               FormalTypeChildConstant.class,
               HandleConstant.class,
               MethodBindingConstant.class,
               MethodConstant.class,
               ParameterizedTypeConstant.class,
               PropertyConstant.class,
               RegisterConstant.class,
               SignatureConstant.class,
               SingletonConstant.class,
               TypeParameterConstant.class)
                .forEach(clz -> {
                    assertDoesNotThrow(() -> clz.getDeclaredMethod(
                            "copyForAdoption", Constant.AdoptionContext.class));
                    assertThrows(NoSuchMethodException.class,
                            () -> clz.getDeclaredMethod("adoptedBy", ConstantPool.class));
                });
    }

    /**
     * The adoption validator must detect the exact bad default-clone shape: a copied helper
     * reference that still belongs to the source constant after ownership changes.
     */
    @Test
    public void adoptionValidatorReportsDefaultCloneCopiedHelperReference() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        DiagnosticConstant source  = new DiagnosticConstant(sourcePool);
        DiagnosticConstant adopted = adopt(source, targetPool);

        ConstantAdoptionValidator.Validation validation =
                ConstantAdoptionValidator.validate(source, adopted);

        assertFalse(validation.isValid());
        assertEquals(1, validation.sharedReferences().size());
        assertTrue(validation.message().contains("DiagnosticConstant.helper"));
        assertTrue(validation.message().contains("AtomicReference"));
    }

    /**
     * Runtime handles are owner-bearing state. A default-cloned constant that carries an arbitrary
     * ObjectHandle would move live runtime state by reference while pretending to only adopt
     * logical constant value into a new pool.
     */
    @Test
    public void adoptionValidatorRejectsDefaultCloneCopiedRuntimeHandle() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();

        var source  = new RuntimeHandleConstant(sourcePool, new TestHandle());
        var adopted = adopt(source, targetPool);

        var validation = ConstantAdoptionValidator.validate(source, adopted);

        assertFalse(validation.isValid());
        assertEquals(1, validation.sharedReferences().size());
        assertTrue(validation.message().contains("RuntimeHandleConstant.handle"));
        assertTrue(validation.message().contains("TestHandle"));
    }

    /**
     * HandleConstant is the one legacy exception: runtime annotation construction creates a fresh
     * unowned handle constant and immediately registers that handle in the current pool. The final
     * adoption wrapper still rejects second adoption of the now-owned constant.
     */
    @Test
    public void adoptionValidatorAllowsFreshHandleConstantFirstRegistration() {
        var targetPool = new FileStructure("target").getConstantPool();

        var source  = new HandleConstant(new TestHandle());
        var adopted = ((Constant) source).adoptedBy(targetPool);

        var validation = ConstantAdoptionValidator.validate(source, adopted);

        assertTrue(validation.isValid(), validation::message);
    }

    /**
     * Registration with adoption validation enabled must reject bad default clones before they are
     * published into the target pool's lookup structures.
     */
    @Test
    public void registerFailsOnBadDefaultCloneWhenAdoptionValidationIsEnabled() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();
        DiagnosticConstant source = new DiagnosticConstant(sourcePool);

        withAdoptionValidation(() -> {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> targetPool.register(source));
            assertTrue(error.getMessage().contains("Invalid XVM constant adoption"));
            assertTrue(error.getMessage().contains("DiagnosticConstant.helper"));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Constant> T adopt(T constant, ConstantPool pool) {
        return (T) constant.adoptedBy(pool);
    }

    private static void withAdoptionValidation(Runnable action) {
        String property = ConstantAdoptionValidator.VALIDATE_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldValue(Object target, String... names)
            throws ReflectiveOperationException {
        Field field = findField(target.getClass(), names);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clz, String... names)
            throws NoSuchFieldException {
        Set<String> setNames = Set.of(names);
        for (Class<?> current = clz; current != null; current = current.getSuperclass()) {
            Field field = Arrays.stream(current.getDeclaredFields())
                    .filter(candidate -> setNames.contains(candidate.getName()))
                    .findFirst()
                    .orElse(null);
            if (field != null) {
                return field;
            }
        }
        throw new NoSuchFieldException(String.join("/", names));
    }

    private static final class DiagnosticConstant
            extends Constant {
        private final AtomicReference<Object> helper = new AtomicReference<>();

        private DiagnosticConstant(ConstantPool pool) {
            super(pool);
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "diagnostic";
        }

        @Override
        public String getDescription() {
            return "diagnostic";
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }

        @Override
        protected boolean allowsDefaultAdoptionClone() {
            return true;
        }
    }

    private static final class RuntimeHandleConstant
            extends Constant {
        private final ObjectHandle handle;

        private RuntimeHandleConstant(ConstantPool pool, ObjectHandle handle) {
            super(pool);
            this.handle = handle;
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "runtime-handle";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }

        @Override
        protected boolean allowsDefaultAdoptionClone() {
            return true;
        }
    }

    private static final class NoPolicyConstant
            extends Constant {
        private NoPolicyConstant(ConstantPool pool) {
            super(pool);
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "no-policy";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }
    }

    private static final class TestHandle
            extends ObjectHandle {
        private TestHandle() {
            super(null);
        }
    }
}
