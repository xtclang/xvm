package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural mutation must fail before changing a published constant table or its indices. */
class FrozenConstantPoolTest {
    @Test
    void bulkRegistrationCannotPruneOrRenumberReadOnlyConstants() {
        var file = new FileStructure("Image");
        var pool = file.getConstantPool();
        var unused = pool.ensureStringConstant("unused but published");
        var constants = pool.getConstants();
        var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
        file.ensureReadOnly();

        assertThrows(IllegalStateException.class, () -> file.reregisterConstants(true));
        assertThrows(IllegalStateException.class, () -> file.reregisterConstants(false));
        assertSame(unused, pool.ensureStringConstant("unused but published"));
        assertArrayEquals(constants, pool.getConstants());
        assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());
    }

    @Test
    void directIndexAndTableWritesAreRejectedBeforeMutation() throws Exception {
        var file = new FileStructure("Image");
        var pool = file.getConstantPool();
        Constant type = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Value", null)
                .getIdentityConstant();
        var replacement = pool.ensureModuleConstant("Replacement");
        var constants = pool.getConstants();
        var position = type.getPosition();
        file.ensureReadOnly();

        assertThrows(IllegalStateException.class, () -> type.setPosition(-1));
        assertEquals(position, type.getPosition());
        assertThrows(IllegalStateException.class, type::resetRefs);
        assertThrows(IllegalStateException.class, type::addRef);
        assertThrows(IllegalStateException.class, () -> pool.replaceModule(file.getModuleId(), replacement));
        try (var input = new DataInputStream(new ByteArrayInputStream(new byte[0]))) {
            assertThrows(IllegalStateException.class, () -> pool.disassemble(input));
            assertThrows(IllegalStateException.class, () -> file.disassemble(input));
        }
        try (var output = new DataOutputStream(new ByteArrayOutputStream())) {
            assertThrows(IllegalStateException.class, () -> file.assemble(output));
        }
        assertArrayEquals(constants, pool.getConstants());
    }

    @Test
    void serializingAReadOnlyImagePreservesItsPublishedTable() throws Exception {
        var file = new FileStructure("Image");
        var pool = file.getConstantPool();
        pool.ensureStringConstant("unused but published");
        var constants = pool.getConstants();
        var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
        file.ensureReadOnly();

        var output = new ByteArrayOutputStream();
        file.writeTo(output);
        var restored = new FileStructure(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(file.getModuleId(), restored.getModuleId());
        assertArrayEquals(constants, pool.getConstants());
        assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());
        var copy = file.ensureMutable();
        assertNotSame(file, copy);
        assertDoesNotThrow(() -> copy.getConstantPool().ensureStringConstant("new in copy"));
    }

    @Test
    void freezingAFingerprintUsesItsConstraintsRatherThanAnActualModuleVersion() {
        var file = new FileStructure("Image");
        var dependency = file.ensureModule("Dependency");
        dependency.fingerprintRequired();
        var version = new Version("1.0");
        var allowed = new VersionTree<Boolean>().put(version, true);
        dependency.setFingerprintVersions(allowed);

        assertDoesNotThrow(file::ensureReadOnly);
        assertTrue(dependency.isReadOnly());
        assertEquals(Boolean.TRUE, dependency.getFingerprintVersions().get(version));
        assertThrows(IllegalStateException.class,
                () -> dependency.getFingerprintVersions().put(new Version("2.0"), true));
    }
}
