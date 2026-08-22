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
