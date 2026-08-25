package org.xvm.asm.constants;

import java.lang.ScopedValue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for owner-explicit type relation helper APIs.
 */
public class TypeConstantOwnerApiTest {
    /**
     * Type relation helpers resolve owner-scoped helper constants. The old ownerless signatures
     * hid that dependency, so callers could compile while relying on arbitrary current-pool state.
     */
    @Test
    public void covarianceHelpersRequireExplicitPool() throws Exception {
        Class<?>[] oldSignature = {TypeConstant.class, TypeConstant.class};
        Class<?>[] newSignature = {ConstantPool.class, TypeConstant.class, TypeConstant.class};

        assertThrows(NoSuchMethodException.class, () ->
                TypeConstant.class.getMethod("isCovariantReturn", oldSignature));
        assertThrows(NoSuchMethodException.class, () ->
                TypeConstant.class.getMethod("isContravariantParameter", oldSignature));

        var covariant = TypeConstant.class.getMethod("isCovariantReturn", newSignature);
        var contravariant = TypeConstant.class.getMethod("isContravariantParameter", newSignature);

        assertTrue(Modifier.isPublic(covariant.getModifiers()));
        assertTrue(Modifier.isPublic(contravariant.getModifiers()));
    }

    /**
     * Missing owner must fail immediately. Falling through to `getCurrentPool()` was broken even
     * single-threaded because a nested helper could leave no pool or the wrong pool installed.
     */
    @Test
    public void covarianceHelpersRejectMissingPool() {
        var pool = new FileStructure("test").getConstantPool();
        var type = pool.typeObject();

        assertThrows(NullPointerException.class, () ->
                type.isCovariantReturn(null, type, null));
        assertThrows(NullPointerException.class, () ->
                type.isContravariantParameter(null, type, null));
    }

    @Test
    public void formalTypeUsageCacheIncludesAccess() {
        var file       = new FileStructure("usage_cache_test");
        var pool       = file.getConstantPool();
        var module     = file.getModule();
        var classBound = module.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Bound", null);
        var classCache = module.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "UsageCache", null);
        var formalType = classCache.addTypeParam("Element", classBound.getCanonicalType())
                .getIdentityConstant().getFormalType();
        var argument = new Parameter(pool, formalType, "value", null, false, 0, false);
        var result   = new Parameter(pool, formalType, null, null, true, 0, false);

        classCache.createMethod(false, Component.Access.PRIVATE, null, Parameter.NO_PARAMS,
                "produce", new Parameter[] {argument}, false, false);
        classCache.createMethod(false, Component.Access.PRIVATE, null, new Parameter[] {result},
                "consume", Parameter.NO_PARAMS, false, false);

        var typeCache = classCache.getFormalType();

        assertTrue(typeCache.producesFormalType("Element", Component.Access.PRIVATE));
        assertFalse(typeCache.producesFormalType("Element", Component.Access.PUBLIC));
        assertTrue(typeCache.consumesFormalType("Element", Component.Access.PRIVATE));
        assertFalse(typeCache.consumesFormalType("Element", Component.Access.PUBLIC));
    }

    @Test
    public void relationCacheSeparatesContextualAutoNarrowingQuestions() throws Exception {
        var file       = new FileStructure("relation_cache_test");
        var pool       = file.getConstantPool();
        var module     = file.getModule();
        var classLeft  = module.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Left", null);
        var classRight = module.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Right", null);
        var typeLeft  = pool.ensureThisTypeConstant(classLeft.getIdentityConstant(), null);
        var typeRight = pool.ensureThisTypeConstant(classRight.getIdentityConstant(), null);
        var typeCtx   = pool.ensureUnionTypeConstant(
                classLeft.getCanonicalType(), classRight.getCanonicalType());

        typeRight.calculateRelation(typeLeft);
        assertEquals(1, relationCacheSize(typeRight));

        ScopedValue.where(relationContext(), typeCtx).run(() -> typeRight.calculateRelation(typeLeft));

        assertEquals(2, relationCacheSize(typeRight));
    }

    /**
     * Normalization is immutable owner-local metadata, but the cache is reachable from runtime
     * type-info paths. The old plain field had no publication edge; the replacement deliberately
     * keeps benign duplicate computation while safely publishing the completed normalized type.
     */
    @Test
    public void normalizedTypeCacheIsVolatile() throws Exception {
        Field field = TypeConstant.class.getDeclaredField("m_typeNormalized");

        assertTrue(Modifier.isVolatile(field.getModifiers()));
    }

    @SuppressWarnings("unchecked")
    private static ScopedValue<TypeConstant> relationContext() throws ReflectiveOperationException {
        Field field = TypeConstant.class.getDeclaredField("s_context");
        field.setAccessible(true);
        return (ScopedValue<TypeConstant>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static int relationCacheSize(TypeConstant type) throws ReflectiveOperationException {
        Field field = TypeConstant.class.getDeclaredField("m_mapRelations");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(type)).size();
    }
}
