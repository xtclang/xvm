package org.xvm.runtime;

import java.util.Arrays;
import java.util.List;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.RuntimeMethodStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.RuntimeTypeContext.IncompatibleTypeOwnerException;

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
    void unchangedNarrowingCreatesTheImmutableWrapperInTheDestination() {
        var file = new Image();
        var value = file.type("Value");
        var immutable = new ImmutableTypeConstant(file.getConstantPool(), value);
        var context = new RuntimeTypeContext(file.getConstantPool());
        var constants = file.getConstantPool().getConstants();
        context.freezeDefinitions();

        var resolved = immutable.resolveAutoNarrowing(context.getDescriptorPool(), false, null, null);
        assertSame(context.getDescriptorPool(), resolved.getConstantPool());
        assertTrue(resolved.isImmutabilitySpecified());
        assertSame(context.intern(value), resolved.getUnderlyingType());
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void coldAnnotationTargetsIncludingDefaultsUseTheQueryOwner() {
        var file = new Image();
        var value = file.type("Value");
        var base = file.getModule().createClass(Access.PUBLIC, Format.ANNOTATION, "Base", null);
        base.addContribution(Composition.Into, value);
        var child = file.getModule().createClass(Access.PUBLIC, Format.ANNOTATION, "Child", null);
        child.addContribution(Composition.Extends, base.getIdentityConstant().getType());
        var defaulted = file.getModule().createClass(Access.PUBLIC, Format.ANNOTATION, "Defaulted", null);
        var context = new RuntimeTypeContext(file.getConstantPool());
        var constants = file.getConstantPool().getConstants();
        context.freezeDefinitions();

        assertSame(context.intern(value), context.typeOf(child.getIdentityConstant()).getExplicitClassInto());
        var target = context.typeOf(defaulted.getIdentityConstant()).getExplicitClassInto();
        assertSame(context.getDescriptorPool(), target.getConstantPool());
        assertSame(context.getDescriptorPool().typeObject(), target);
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void explicitSharedImportRebindsWithoutOpeningOrdinaryInterning() {
        var first = new Image();
        var sourceType = first.type("Value");
        var second = new Image();
        var targetType = second.type("Value");
        var source = new RuntimeTypeContext(first.getConstantPool());
        var target = new RuntimeTypeContext(second.getConstantPool());
        var firstConstants = first.getConstantPool().getConstants();
        var secondConstants = second.getConstantPool().getConstants();
        source.freezeDefinitions();
        target.freezeDefinitions();

        assertThrows(IncompatibleTypeOwnerException.class,
                () -> target.importShared(sourceType, source, _ -> false));
        var result = target.importShared(sourceType, source, _ -> true);
        assertSame(target.intern(targetType), result);
        assertSame(targetType.getSingleUnderlyingClass(true).getComponent(),
                result.getSingleUnderlyingClass(true).getComponent());
        assertThrows(IncompatibleTypeOwnerException.class, () -> target.intern(source.intern(sourceType)));
        assertThrows(IncompatibleTypeOwnerException.class, () -> target.intern(sourceType));
        assertThrows(IncompatibleTypeOwnerException.class,
                () -> target.importShared(targetType, source, _ -> true));
        assertArrayEquals(firstConstants, first.getConstantPool().getConstants());
        assertArrayEquals(secondConstants, second.getConstantPool().getConstants());
    }

    @Test
    void failedSharedAdoptionRestoresStrictOwnershipChecks() {
        var first = new Image();
        var identity = first.type("Value").getSingleUnderlyingClass(true);
        var second = new Image();
        second.type("Value");
        var source = new RuntimeTypeContext(first.getConstantPool());
        var target = new RuntimeTypeContext(second.getConstantPool());
        var pool = source.getDescriptorPool();
        var failure = new IllegalStateException("injected adoption failure");
        var broken = pool.register(new TerminalTypeConstant(pool, pool.register(identity)) {
            @Override
            protected Constant adoptedBy(ConstantPool destination) {
                throw failure;
            }
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> target.importShared(broken, source, _ -> true)));
        assertThrows(IncompatibleTypeOwnerException.class, () -> target.intern(broken));
        assertThrows(IncompatibleTypeOwnerException.class,
                () -> target.getDescriptorPool().register(pool.register(identity)));
    }

    @Test
    void coldClassFormalsAndDefaultsUseTheQueryOwner() {
        var file = new Image();
        var value = file.type("Value");
        var box = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Box", null);
        box.addTypeParam("Element", value);
        var context = new RuntimeTypeContext(file.getConstantPool());
        var constants = file.getConstantPool().getConstants();
        context.freezeDefinitions();

        var pool = context.getDescriptorPool();
        var formal = box.getFormalType(pool);
        assertSame(pool, formal.getParamType(0).getConstantPool());
        assertTrue(formal.getParamType(0).isFormalType());
        assertSame(context.intern(value), box.getCanonicalType(pool).getParamType(0));
        assertSame(box.getCanonicalType(pool), box.resolveType(pool, List.of()));
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void coldDeclarationTypeIsCreatedInTheContextAfterFreezing() {
        var file = new Image();
        var identity = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Cold", null)
                .getIdentityConstant();
        var context = new RuntimeTypeContext(file.getConstantPool());
        var constants = file.getConstantPool().getConstants();
        context.freezeDefinitions();

        assertThrows(IllegalStateException.class, identity::getType);
        var descriptor = context.typeOf(identity);
        assertSame(context.getDescriptorPool(), descriptor.getConstantPool());
        assertSame(identity.getComponent(), descriptor.getSingleUnderlyingClass(true).getComponent());
        assertSame(descriptor, context.typeOf(identity));
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void unchangedContributionIsAdoptedBeforeMetadataAddsAccess() {
        var file = new Image();
        var declaration = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        var base = file.type("Base");
        var contribution = declaration.addContribution(Composition.Extends, base);
        var context = new RuntimeTypeContext(file.getConstantPool());
        var child = context.typeOf(declaration.getIdentityConstant());
        var constants = file.getConstantPool().getConstants();
        context.freezeDefinitions();

        var resolved = contribution.resolveGenerics(context.getDescriptorPool(), child);
        assertSame(context.intern(base), resolved);
        assertSame(context.getDescriptorPool(), resolved.ensureAccess(Access.PROTECTED).getConstantPool());
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void derivedDescriptorsLeaveAFrozenImageAndItsIndicesUnchanged() {
        var file = new Image();
        var base = file.type("Box");
        var argument = file.type("Value");
        var image = file.getConstantPool();
        var constants = image.getConstants();
        var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
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
        assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());
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
        assertThrows(IncompatibleTypeOwnerException.class, () -> context1.calculateRelation(base1, base2));
        assertThrows(IncompatibleTypeOwnerException.class, () -> descriptor1.calculateRelation(descriptor2));
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
        context.freezeDefinitions();
        context.freezeDefinitions();
        assertTrue(file.isReadOnly());
        assertTrue(library.isReadOnly());
        assertFalse(context.getDescriptorPool().isReadOnly());
        assertThrows(IllegalStateException.class,
                () -> library.getConstantPool().ensureStringConstant("new definition"));
        assertThrows(IllegalStateException.class,
                () -> dependency.setFingerprintOrigin(new FileStructure("Library").getModule()));
        assertSame(descriptor, context.parameterize(box, value));
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeTypeContext(context.getDescriptorPool()));
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
    void reflectiveNormalizationDistinguishesOwnershipRejectionFromImplementationFailure() {
        var file = new Image();
        var value = file.type("Value");
        var context = new RuntimeTypeContext(file.getConstantPool());
        var other = new Image();
        assertThrows(IncompatibleTypeOwnerException.class,
                () -> context.adoptParameters(other.type("Value")));
        assertThrows(IncompatibleTypeOwnerException.class,
                () -> context.adoptParameters(value, other.type("Argument")));
        assertSame(context.intern(value), context.adoptParameters(value));

        var identity = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Broken", null)
                .getIdentityConstant();
        var failure = new IllegalStateException("injected normalization failure");
        var broken = new TerminalTypeConstant(file.getConstantPool(), identity) {
            @Override
            public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] parameters) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> context.adoptParameters(broken)));
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
            new RuntimeTypeContext(getConstantPool()).freezeDefinitions();
        }
    }
}
