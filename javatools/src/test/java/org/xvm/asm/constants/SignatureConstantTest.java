package org.xvm.asm.constants;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.PropertyStructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link SignatureConstant}.
 */
public class SignatureConstantTest {
    @Test
    public void resolvingGenericPropertySignatureDoesNotPolluteConstantPool() {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ModuleStructure module = file.getModule();

        ClassStructure actual = module.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Actual", null);
        ClassStructure box = module.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Box", null);

        PropertyStructure typeParameter = box.addTypeParam("T", actual.getCanonicalType());
        TypeConstant formalType = typeParameter.getIdentityConstant().getFormalType();
        PropertyStructure value = box.createProperty(
                false, Component.Access.PUBLIC, Component.Access.PUBLIC, formalType, "value");

        SignatureConstant propertySignature = value.getIdentityConstant().getSignature();
        SignatureConstant resolved = propertySignature.resolveGenericTypes(
                pool, ignored -> actual.getCanonicalType());

        assertTrue(resolved.isProperty());
        assertNull(pool.getConstant(resolved));

        SignatureConstant methodSignature = pool.ensureSignatureConstant(
                "value", ConstantPool.NO_TYPES, new TypeConstant[] {actual.getCanonicalType()});
        assertFalse(methodSignature.isProperty());
    }
}
