package org.xvm.asm;


import java.lang.ref.WeakReference;

import java.lang.reflect.Field;

import java.nio.file.attribute.FileTime;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.runtime.ObjectHandle;

import org.xvm.util.TransientThreadLocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
