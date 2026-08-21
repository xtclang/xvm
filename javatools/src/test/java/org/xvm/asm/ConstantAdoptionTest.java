package org.xvm.asm;


import java.lang.ref.WeakReference;

import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.runtime.ObjectHandle;

import org.xvm.util.TransientThreadLocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link Constant#adoptedBy(ConstantPool)} ownership handoff behavior.
 */
public class ConstantAdoptionTest {
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

    @Test
    public void adoptedSignatureConstantGetsFreshComparisonAndJitHelpers()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        SignatureConstant source = sourcePool.ensureSignatureConstant(
                "demo", ConstantPool.NO_TYPES, new TypeConstant[] {sourcePool.typeString()});
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

    @SuppressWarnings("unchecked")
    private static <T extends Constant> T adopt(T constant, ConstantPool pool) {
        return (T) constant.adoptedBy(pool);
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
}
