package org.xvm.asm.constants;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.PropertyStructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link SignatureConstant}.
 */
public class SignatureConstantTest {
    @Test
    public void resolvingGenericPropertySignaturePreservesCanonicalInstances() {
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
        SignatureConstant resolvedAgain = propertySignature.resolveGenericTypes(
                pool, ignored -> actual.getCanonicalType());

        assertTrue(resolved.isProperty());
        assertSame(resolved, pool.getConstant(resolved));
        assertSame(resolved, resolvedAgain);

        SignatureConstant methodSignature = pool.ensureSignatureConstant(
                "value", ConstantPool.NO_TYPES, new TypeConstant[] {actual.getCanonicalType()});
        assertFalse(methodSignature.isProperty());
        assertNotSame(resolved, methodSignature);
    }

    /**
     * Stage-3 Family A contract (array-exposure audit): a signature's type storage is frozen.
     * The old shape returned the interned raw arrays from getRawParams()/getRawReturns() -
     * consumers relied on scattered clone conventions - and getReturns() was a LIVE
     * Arrays.asList view: list.set(...) wrote straight into the interned constant shared by
     * every container. Red on that shape (the getReturns half is red on master too).
     */
    @Test
    public void signatureTypeStorageIsFrozen() {
        var file = new FileStructure("frozensig");
        var pool = file.getConstantPool();

        var typeA = pool.typeString();
        var typeB = pool.typeBoolean();
        var sig   = pool.ensureSignatureConstant("m",
                new TypeConstant[] {typeA}, new TypeConstant[] {typeB});

        // the List views are immutable snapshots, not live windows into the constant
        assertThrows(UnsupportedOperationException.class,
                () -> sig.getReturns().set(0, typeA),
                "getReturns() must never be a writable view of interned storage");
        assertThrows(UnsupportedOperationException.class,
                () -> sig.getParams().set(0, typeB));

        // a copy() is the caller's own array; mutating it cannot touch the constant
        TypeConstant[] aCopy = sig.getRawReturns().copy();
        aCopy[0] = typeA;
        assertSame(typeB, sig.getRawReturns().get(0),
                "mutating a copy must not affect the interned signature");

        // sharing the frozen wrapper between constants is the safe form of the old raw-array
        // aliasing (e.g. MethodConstant building a signature from another signature's types)
        var sigShared = pool.ensureSignatureConstant("m2",
                sig.getRawParams(), sig.getRawReturns());
        assertSame(sig.getRawParams().unsafeArray(), sigShared.getRawParams().unsafeArray(),
                "wrapper sharing keeps zero-copy interning without a mutation channel");
    }
}
