package org.xvm.asm.constants;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;

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
}
