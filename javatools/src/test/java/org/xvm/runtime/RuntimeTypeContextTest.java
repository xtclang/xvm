package org.xvm.runtime;

import java.util.Arrays;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.RuntimeMethodStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Move;
import org.xvm.asm.op.Return_0;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTypeContextTest {
    @Test
    void derivedDescriptorsLeaveAFrozenImageAndItsIndicesUnchanged() {
        var file = new Image();
        var base = file.type("Box");
        var argument = file.type("Value");
        var image = file.getConstantPool();
        var constants = image.getConstants();
        var positions = Arrays.stream(constants).mapToInt(c -> c.getPosition()).toArray();
        file.freeze();

        var context = new RuntimeTypeContext(image);
        var unrelated = new FileStructure("Unrelated").getConstantPool();
        try (var scope = ConstantPool.withPool(unrelated)) {
            var descriptor = context.parameterize(base, argument);
            assertSame(descriptor, context.parameterize(base, argument));
            assertSame(context.getDescriptorPool(), descriptor.getConstantPool());
            assertEquals(-1, descriptor.getPosition());
            assertSame(unrelated, ConstantPool.getCurrentPool());
        }
        assertArrayEquals(constants, image.getConstants());
        assertArrayEquals(positions, Arrays.stream(constants).mapToInt(c -> c.getPosition()).toArray());
        assertTrue(Arrays.stream(context.getDescriptorPool().getConstants())
                .allMatch(c -> c.getPosition() == -1));
        assertFalse(context.getDescriptorPool().hasSerializedIndices());
        assertThrows(UnsupportedOperationException.class,
                () -> context.getDescriptorPool().getConstant(0));
        assertThrows(UnsupportedOperationException.class,
                () -> context.getDescriptorPool().getConstant(0, TypeConstant.class));
    }

    @Test
    void sameNamesFromAnotherGenerationCannotHitAnExistingDescriptor() {
        var first = new Image();
        var second = new Image();
        var base1 = first.type("Box");
        var base2 = second.type("Box");
        var value1 = first.type("Value");
        var value2 = second.type("Value");
        // Give the same-named class an incompatible declaration in the second generation.
        ((ClassStructure) second.getModule().getChild("Box"))
                .createProperty(false, Access.PUBLIC, Access.PUBLIC, value2, "added");
        assertEquals(base1, base2);
        assertEquals(value1, value2);
        var context1 = new RuntimeTypeContext(first.getConstantPool());
        var context2 = new RuntimeTypeContext(second.getConstantPool());
        var descriptor1 = context1.parameterize(base1, value1);
        var descriptor2 = context2.parameterize(base2, value2);
        assertNotSame(descriptor1, descriptor2);
        assertSame(first.getModule().getChild("Box"), descriptor1.getSingleUnderlyingClass(true).getComponent());
        assertSame(second.getModule().getChild("Box"), descriptor2.getSingleUnderlyingClass(true).getComponent());
        assertThrows(IllegalArgumentException.class, () -> context1.intern(base2));
        assertThrows(IllegalArgumentException.class, () -> context1.parameterize(base1, value2));
        assertThrows(IllegalArgumentException.class, () -> context1.intern(descriptor2));
        assertThrows(IllegalArgumentException.class,
                () -> context1.getDescriptorPool().getConstant(descriptor2));
        // Checking only the outer constant's owner would miss this foreign argument.
        var mixed = new ParameterizedTypeConstant(context1.getDescriptorPool(), base1, value2);
        assertThrows(IllegalArgumentException.class, () -> context1.intern(mixed));
        assertSame(descriptor1, context1.parameterize(base1, value1));
    }

    @Test
    void linkedDependencyUsesItsExactDefinitionGeneration() {
        var library = new FileStructure("Library");
        var value = library.getModule().createClass(Access.PUBLIC, Format.CLASS, "Value", null)
                .getIdentityConstant().getType();
        var file = new Image();
        var box = file.type("Box");
        var dependency = file.ensureModule("Library");
        dependency.fingerprintRequired();
        dependency.setFingerprintOrigin(library.getModule());
        var context = new RuntimeTypeContext(file.getConstantPool());
        var descriptor = context.parameterize(box, value);
        assertSame(library.getModule().getChild("Value"),
                descriptor.getParamType(0).getSingleUnderlyingClass(true).getComponent());
        var replacement = new FileStructure(library);
        var otherValue = ((ClassStructure) replacement.getModule().getChild("Value"))
                .getIdentityConstant().getType();
        assertThrows(IllegalArgumentException.class, () -> context.parameterize(box, otherValue));
    }

    @Test
    void independentContextsForTheSameImageDoNotShareMutableDescriptorState() {
        var file = new Image();
        var type = file.type("Value");
        var first = new RuntimeTypeContext(file.getConstantPool());
        var second = new RuntimeTypeContext(file.getConstantPool());
        assertNotSame(first.intern(type), second.intern(type));
        assertThrows(IllegalArgumentException.class, () -> second.intern(first.intern(type)));
        var captured = new HandleConstant(file.getConstantPool(), new ObjectHandle(null) {});
        assertThrows(IllegalArgumentException.class, () -> first.getDescriptorPool().register(captured));
    }

    @Test
    void previouslyUnusedDefaultIsCreatedOutsideTheFrozenImage() {
        var file = new Image();
        var type = file.type("Defaultable");
        var declaration = (ClassStructure) file.getModule().getChild("Defaultable");
        declaration.createProperty(true, Access.PUBLIC, null, type, "default");
        var constants = file.getConstantPool().getConstants();
        file.freeze();
        var context = new RuntimeTypeContext(file.getConstantPool());
        var value = type.getDefaultValue(context.getDescriptorPool());
        assertSame(context.getDescriptorPool(), value.getConstantPool());
        assertEquals(-1, value.getPosition());
        assertSame(value, type.getDefaultValue(context.getDescriptorPool()));
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void nativeDescriptorAdoptionMovesTheWrappedInterfaceAsWellAsTheParent() {
        var file = new Image();
        var declaration = file.getModule().createClass(Access.PUBLIC, Format.INTERFACE, "Native", null);
        var identity = new NativeRebaseConstant((ClassConstant) declaration.getIdentityConstant());
        var type = file.getConstantPool().ensureTerminalTypeConstant(identity);
        var context = new RuntimeTypeContext(file.getConstantPool());
        var adopted = (NativeRebaseConstant) context.intern(type).getDefiningConstant();
        assertNotSame(identity, adopted);
        assertSame(context.getDescriptorPool(), adopted.getClassConstant().getConstantPool());
        assertSame(declaration, adopted.getClassConstant().getComponent());
        assertSame(file.getConstantPool(), identity.getClassConstant().getConstantPool());
    }

    @Test
    void lateGeneratedCodeUsesLocalReferencesAndIsNotAnImageDeclaration() {
        var file = new Image();
        var owner = file.type("Value");
        var constants = file.getConstantPool().getConstants();
        var children = file.getModule().children().size();
        file.freeze();
        var pool = new RuntimeTypeContext(file.getConstantPool()).getDescriptorPool();
        var identity = pool.ensureMethodConstant(pool.register(owner.getSingleUnderlyingClass(true)),
                "late", TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
        var method = new RuntimeMethodStructure(identity);
        var value = pool.ensureStringConstant("created after activation");
        var code = method.createCode();
        code.add(new Move(value, new Register(null, null, Op.A_IGNORE)));
        code.add(new Return_0());
        method.forceAssembly(pool);
        assertThrows(IllegalArgumentException.class, () -> method.forceAssembly(file.getConstantPool()));
        assertSame(pool, method.getConstantPool());
        assertTrue(Arrays.stream(method.getLocalConstants()).anyMatch(c -> c == value));
        assertTrue(Arrays.stream(method.getLocalConstants()).allMatch(c -> c.getPosition() == -1));
        assertEquals(children, file.getModule().children().size());
        assertArrayEquals(constants, file.getConstantPool().getConstants());
        assertFalse(file.getModule().children().contains(method));
    }

    @Test
    @Timeout(10)
    void concurrentInterningPublishesOneCompleteDescriptorWithoutAnAmbientPool() throws Exception {
        var file = new Image();
        var base = file.type("Box");
        var value = file.type("Value");
        file.freeze();
        var context = new RuntimeTypeContext(file.getConstantPool());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<TypeConstant> task = () -> {
                try (var scope = ConstantPool.withPool(null)) {
                    ready.countDown();
                    start.await();
                    return context.parameterize(base, value);
                }
            };
            var first = threads.submit(task);
            var second = threads.submit(task);
            try {
                ready.await();
            } finally {
                start.countDown();
            }
            var descriptor = first.get();
            assertSame(descriptor, second.get());
            descriptor.forEachUnderlying(c -> assertSame(context.getDescriptorPool(), c.getConstantPool()));
        }
    }

    private static class Image extends FileStructure {
        Image() {
            super("App");
            // Constant names themselves use String's type. Supply a bundled system-module stub,
            // without requiring a built distribution or loading real runtime metadata.
            var system = new FileStructure(Constants.ECSTASY_MODULE);
            merge(system.getModule(), false, false);
        }

        TypeConstant type(String name) {
            return getModule().createClass(Access.PUBLIC, Format.CLASS, name, null)
                    .getIdentityConstant().getType();
        }

        void freeze() {
            markReadOnly();
        }
    }
}
