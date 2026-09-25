package org.xvm.xdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.api.EmbeddingSupport;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Op;
import org.xvm.asm.RuntimeMethodStructure;

import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.runtime.Fiber;
import org.xvm.runtime.Frame;
import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.NestedContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.RuntimeTypeContext.IncompatibleTypeOwnerException;
import org.xvm.runtime.ServiceContext;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ownership checks requiring library metadata use the distribution provisioned by the XDK task.
 */
class ConstantPoolOwnershipTest {
    // Master's embedding API exposes one configured compiler. Compiling does not start a runtime.
    private static final EmbeddingSupport COMPILER = EmbeddingSupport.instance().configure(repository(), null);

    @ParameterizedTest
    @ValueSource(strings = {"Singletons.x", "SingletonPaths.x", "RuntimeDescriptors.x", "RuntimeConstruction.x", "MetadataQueries.x"})
    @Timeout(60)
    void ownershipProgramsRunInIndependentApplications(String source) throws Exception {
        runOwnershipProgram(source, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RuntimeDescriptors.x", "RuntimeConstruction.x"})
    @Timeout(60)
    void coldEntryAndGenericConstructionRunOverFrozenDefinitions(String source) throws Exception {
        runOwnershipProgram(source, true);
    }

    private void runOwnershipProgram(String source, boolean freeze) throws Exception {
        var repository = repository();
        var runtime = new Runtime();
        try (var input = getClass().getResourceAsStream("/ownership/" + source)) {
            assertNotNull(input);
            var compilation = new ErrorList(100);
            var module = COMPILER.compile(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8), repository, compilation);
            assertNotNull(module, () -> compilation.getErrors().toString());
            assertFalse(compilation.hasSeriousErrors(), compilation::toString);
            // Use the same unlinked compiled artifact that the launcher reads from an XTC file.
            // Compiler-linked definitions have not undergone native runtime preparation.
            var binary = new ByteArrayOutputStream();
            module.getFileStructure().writeTo(binary);
            module = new FileStructure(new ByteArrayInputStream(binary.toByteArray())).getModule();
            var modules = new BuildRepository();
            modules.storeModule(module);
            var runtimeRepository = new LinkedRepository(modules, repository);
            var root = new NativeContainer(runtime, runtimeRepository);
            for (int i = 0; i < 2; i++) {
                // Reuse the native root while each application starts with independent definitions.
                var file = root.createFileStructure(new FileStructure(module.getFileStructure()).getModule());
                assertNull(file.linkModules(runtimeRepository, true));
                var application = new MainContainer(runtime, root, file.getModuleId());
                if (!freeze && source.equals("RuntimeDescriptors.x")) {
                    verifyLateInitializer(application);
                    verifyRelationCache(application);
                }
                var constants = file.getConstantPool().getConstants();
                var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
                if (freeze) {
                    // Freeze before entry/type queries, not after warming metadata. The second
                    // execution uses another prepared image while retaining the native root.
                    application.getTypeContext().freezeDefinitions();
                }
                application.start(Map.of());
                application.invokeAsync("run").join();
                if (freeze) {
                    assertArrayEquals(constants, file.getConstantPool().getConstants());
                    assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());
                }
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    /** Compare actual type algebra with cold, warm and cleared runtime relation tables. */
    private static void verifyRelationCache(MainContainer application) {
        var pool = application.getConstantPool();
        var box = ((ClassStructure) application.getModule().getComponent().getChild("Box"))
                .getIdentityConstant().getType();
        var types = List.of(pool.typeString(), pool.typeInt64(), pool.typeObject(),
                pool.ensureParameterizedTypeConstant(box, pool.typeString()),
                pool.ensureParameterizedTypeConstant(box, pool.typeInt64()),
                pool.ensureNullableTypeConstant(pool.typeString()));
        var expected = types.stream().map(right -> types.stream()
                .map(right::calculateRelation).toList()).toList();
        var context = application.getTypeContext();
        var descriptors = types.stream().map(context::intern).toList();
        var constants = pool.getConstants();
        var unrelated = new FileStructure("UnrelatedRelations").getConstantPool();
        try (var scope = ConstantPool.withPool(unrelated)) {
            context.clearRelations();
            for (int pass = 0; pass < 3; pass++) {
                if (pass == 2) {
                    context.clearRelations();
                }
                var actual = descriptors.stream().map(right -> descriptors.stream()
                        .map(left -> context.calculateRelation(right, left)).toList()).toList();
                assertEquals(expected, actual);
                for (int i = 0; i < types.size(); i++) {
                    assertSame(descriptors.get(i), context.intern(types.get(i)));
                }
                assertSame(unrelated, ConstantPool.getCurrentPool());
            }
        }
        assertArrayEquals(constants, pool.getConstants());
    }

    /** Check the boundary before either generic composition's initializer has executed. */
    private static void verifyLateInitializer(MainContainer application) {
        var pool = application.getConstantPool();
        var box = ((ClassStructure) application.getModule().getComponent().getChild("Box"))
                .getIdentityConstant().getType();
        var composition = application.resolveClass(pool.ensureParameterizedTypeConstant(box, pool.typeInt64()));
        // Composition metadata and generated initializers belong to the execution context.
        // Keep a separate cold/frozen execution test so this setup cannot hide image writes.
        var constants = pool.getConstants();
        var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
        var context = application.getTypeContext();
        var descriptor = context.parameterize(box, pool.typeString());
        assertEquals(-1, descriptor.getPosition());
        var initializer = composition.ensureAutoInitializer();
        assertTrue(initializer instanceof RuntimeMethodStructure);
        assertSame(context.getDescriptorPool(), initializer.getConstantPool());
        assertTrue(Arrays.stream(initializer.getLocalConstants()).allMatch(c -> c.getPosition() == -1));
        assertArrayEquals(constants, pool.getConstants());
        assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());
        assertFalse(application.getModule().getComponent().children().contains(initializer));

        // Warm the canonical reflective layout, then request a parameterization unused by the
        // compiled program. The handle must not send its descriptor back through the image pool.
        pool.typeObject().ensureTypeHandle(application);
        constants = pool.getConstants();
        positions = Arrays.stream(constants).map(Constant::getPosition).toList();
        var reflected = context.adoptParameters(box, pool.typeBoolean());
        var handle = reflected.ensureTypeHandle(application);
        assertSame(reflected, handle.getDataType());
        assertSame(context.getDescriptorPool(), handle.getType().getConstantPool());
        assertSame(handle, reflected.ensureTypeHandle(application));
        var reflectedComposition = application.resolveClass(reflected);
        assertSame(context.getDescriptorPool(), reflectedComposition.getType().getConstantPool());
        assertSame(reflectedComposition, application.resolveClass(reflected));
        assertArrayEquals(constants, pool.getConstants());
        assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());

        var arrayType = context.adoptParameters(pool.typeArray(), reflected);
        var arrayComposition = application.resolveClass(arrayType);
        assertSame(context.getDescriptorPool(), arrayComposition.getType().getConstantPool());
        assertSame(context.getDescriptorPool(), arrayComposition.getConstantPool());
    }

    @Test
    void coldPropertyAnnotationsUseIndependentContextsOverAFrozenImage() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("ColdAnnotations").getModule());
            var image = file.getConstantPool();
            var declaration = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Holder", null);
            var property = declaration.createProperty(false, Access.PUBLIC, Access.PUBLIC,
                    image.typeString(), "value");
            property.addAnnotation(image.clzRO());
            property.addAnnotation(image.clzLazy());
            var first = new MainContainer(runtime, root, file.getModuleId()).getTypeContext();
            var second = new MainContainer(runtime, root, file.getModuleId()).getTypeContext();
            var constants = image.getConstants();
            var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
            first.freezeDefinitions();

            var firstAnnotations = property.getAnnotationGroups(first.getDescriptorPool());
            var secondAnnotations = property.getAnnotationGroups(second.getDescriptorPool());
            assertEquals(1, firstAnnotations.property().size());
            assertEquals(1, firstAnnotations.reference().size());
            assertEquals(firstAnnotations, secondAnnotations);
            assertNotSame(firstAnnotations.reference().getFirst(), secondAnnotations.reference().getFirst());
            for (var annotations : List.of(firstAnnotations.property(), firstAnnotations.reference())) {
                assertSame(first.getDescriptorPool(), annotations.getFirst().getConstantPool());
                assertThrows(UnsupportedOperationException.class, annotations::clear);
            }
            assertSame(second.getDescriptorPool(), secondAnnotations.reference().getFirst().getConstantPool());
            assertEquals(firstAnnotations, property.getAnnotationGroups(first.getDescriptorPool()));
            assertArrayEquals(constants, image.getConstants());
            assertEquals(positions, Arrays.stream(image.getConstants()).map(Constant::getPosition).toList());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void nativeParameterizedConstructionDoesNotRegisterInTheFrozenImage() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("NativeConstruction").getModule());
            var application = new MainContainer(runtime, root, file.getModuleId());
            var image = file.getConstantPool();
            var context = application.getTypeContext();
            var pool = context.getDescriptorPool();
            var constants = image.getConstants();
            var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
            context.freezeDefinitions();

            for (var type : List.of(pool.typeRef(), pool.typeArray())) {
                var template = application.getTemplate(type);
                var composition = template.ensureParameterizedClass(application, pool.typeString());
                assertSame(pool, composition.getType().getConstantPool());
                assertSame(application, composition.getContainer());
                assertSame(composition, template.ensureParameterizedClass(application, pool.typeString()));
            }
            assertArrayEquals(constants, image.getConstants());
            assertEquals(positions, Arrays.stream(image.getConstants()).map(Constant::getPosition).toList());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void coldModuleMetadataCanBeBuiltInTwoContextsOverOneFrozenImage() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("ColdMetadata").getModule());
            var first = new MainContainer(runtime, root, file.getModuleId());
            var second = new MainContainer(runtime, root, file.getModuleId());
            var constants = file.getConstantPool().getConstants();
            var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
            first.getTypeContext().freezeDefinitions();

            var firstType = first.getTypeContext().typeOf(file.getModuleId());
            var secondType = second.getTypeContext().typeOf(file.getModuleId());
            var firstInfo = firstType.ensureTypeInfo();
            var secondInfo = secondType.ensureTypeInfo();
            assertTrue(TypeConstant.isComplete(firstInfo));
            assertTrue(TypeConstant.isComplete(secondInfo));
            assertNotSame(firstInfo, secondInfo);
            assertSame(firstInfo, firstType.ensureTypeInfo());
            assertSame(secondInfo, secondType.ensureTypeInfo());
            first.getTypeContext().clearMetadata();
            var rebuilt = firstType.ensureTypeInfo();
            assertNotSame(firstInfo, rebuilt);
            assertEquals(firstInfo.getMethods().keySet(), rebuilt.getMethods().keySet());
            assertEquals(firstInfo.getProperties().keySet(), rebuilt.getProperties().keySet());
            assertSame(firstType, first.getTypeContext().typeOf(file.getModuleId()));
            assertSame(secondInfo, secondType.ensureTypeInfo());
            assertArrayEquals(constants, file.getConstantPool().getConstants());
            assertEquals(positions, Arrays.stream(file.getConstantPool().getConstants())
                    .map(Constant::getPosition).toList());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void frozenBootstrapMetadataUsesThePreparedNakedRefDeclaration() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("BootstrapMetadata").getModule());
            var application = new MainContainer(runtime, root, file.getModuleId());
            var image = file.getConstantPool();
            var context = application.getTypeContext();
            var referent = context.intern(image.typeString());
            var prototype = image.getNakedRefType().getSingleUnderlyingClass(true);
            var local = file.getModule(prototype.getModuleConstant()).getChild(prototype.getName());
            assertNotSame(prototype.getComponent(), local);
            var constants = image.getConstants();
            context.freezeDefinitions();

            var info = context.getDescriptorPool().getNakedRefInfo(referent);
            assertSame(context.getDescriptorPool(), info.getType().getConstantPool());
            assertSame(local, info.getType().getSingleUnderlyingClass(true).getComponent());
            var method = info.findMethods("get", 0, MethodKind.Method)
                    .iterator().next();
            assertSame(context.getDescriptorPool(), method.getConstantPool());
            assertSame(referent, method.getSignature().getReturns().getFirst());
            assertSame(info, context.getDescriptorPool().getNakedRefInfo(referent));
            assertThrows(IncompatibleTypeOwnerException.class,
                    () -> context.getDescriptorPool().register(prototype));
            assertArrayEquals(constants, image.getConstants());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void reflectiveHandlesBelongToContainersEvenWhenDefinitionsAreIdentical() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("Handles").getModule());
            var first = new MainContainer(runtime, root, file.getModuleId());
            var second = new MainContainer(runtime, root, file.getModuleId());
            var type = file.getConstantPool().typeString();
            var firstHandle = type.ensureTypeHandle(first);
            var secondHandle = type.ensureTypeHandle(second);

            assertNotSame(firstHandle, secondHandle);
            assertSame(first, firstHandle.getComposition().getContainer());
            assertSame(second, secondHandle.getComposition().getContainer());
            assertSame(firstHandle, type.ensureTypeHandle(first));
            assertSame(secondHandle, type.ensureTypeHandle(second));
            var descriptor = first.getTypeContext().intern(type);
            var descriptorHandle = descriptor.ensureTypeHandle(first);
            assertNotSame(firstHandle, descriptorHandle);
            assertSame(descriptor, descriptorHandle.getDataType());
            var foreignHandle = descriptor.ensureTypeHandle(second);
            assertTrue(foreignHandle.isForeign());
            assertSame(descriptor, foreignHandle.getUnsafeDataType());
            assertThrows(IncompatibleTypeOwnerException.class, () -> second.resolveClass(descriptor));

            // Freeze after preparing the canonical reflection layout. A new runtime-derived
            // parameterization and its handle must still use this container's descriptor store.
            var arrayType = first.getConstantPool().typeArray();
            var constants = first.getConstantPool().getConstants();
            var context = first.getTypeContext();
            context.freezeDefinitions();
            assertTrue(file.isReadOnly());
            // This prepared application bundles its definitions; its execution parent is not
            // automatically part of the linked definition graph being frozen.
            assertFalse(root.getConstantPool().isReadOnly());
            assertFalse(context.getDescriptorPool().isReadOnly());
            var arrayDescriptor = context.parameterize(arrayType, descriptor);
            assertSame(context.getDescriptorPool(), arrayDescriptor.getConstantPool());
            assertSame(arrayDescriptor, arrayDescriptor.ensureTypeHandle(first).getDataType());
            assertArrayEquals(constants, first.getConstantPool().getConstants());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void sharedTypeTransportUsesAncestryAndLeavesFrozenImagesUnchanged() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("Transport").getModule());
            var parent = new MainContainer(runtime, root, file.getModuleId());
            var child = new NestedContainer(parent, new FileStructure(file).getModuleId(), null, List.of());
            var sibling = new NestedContainer(parent, new FileStructure(file).getModuleId(), null, List.of());
            var parentImage = parent.getConstantPool();
            var childImage = child.getConstantPool();
            var parentConstants = parentImage.getConstants();
            var childConstants = childImage.getConstants();
            parent.getTypeContext().freezeDefinitions();
            child.getTypeContext().freezeDefinitions();
            sibling.getTypeContext().freezeDefinitions();

            var source = child.getTypeContext().getDescriptorPool();
            var shared = source.ensureArrayType(source.typeString());
            var translated = parent.importSharedType(shared, child);
            var target = parent.getTypeContext().getDescriptorPool();
            assertSame(target.ensureArrayType(target.typeString()), translated);
            assertNotSame(shared, translated);
            assertThrows(IncompatibleTypeOwnerException.class, () -> parent.getTypeContext().intern(shared));

            // Copies with the same module name do not share their application declarations.
            var ownType = child.getTypeContext().typeOf(child.getModule());
            assertThrows(IncompatibleTypeOwnerException.class, () -> parent.importSharedType(ownType, child));
            assertThrows(IncompatibleTypeOwnerException.class, () -> sibling.importSharedType(ownType, child));
            assertThrows(IncompatibleTypeOwnerException.class, () -> parent.importSharedType(shared, sibling));
            assertArrayEquals(parentConstants, parentImage.getConstants());
            assertArrayEquals(childConstants, childImage.getConstants());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void explicitlySharedNestedModulesUseTheHighestOwningAncestor() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("App").getModule());
            var parent = new MainContainer(runtime, root, file.getModuleId());
            var childFile = new FileStructure(file);
            var child = new NestedContainer(parent, childFile.getModuleId(), null,
                    List.of(parent.getModule()));
            var grandchild = new NestedContainer(child, new FileStructure(file).getModuleId(), null,
                    List.of(child.getModule()));
            var original = parent.getConstantPool().ensureSingletonConstConstant(parent.getModule());
            var value = new ObjectHandle(null) {};
            parent.ensureSingletonState(original).setHandle(value);
            var alias = grandchild.getConstantPool().register(original);

            assertSame(parent, grandchild.getOriginContainer(alias));
            assertSame(original, grandchild.ensureSingletonConstant(alias));
            assertSame(parent.ensureSingletonState(original), grandchild.ensureSingletonState(alias));
            assertSame(value, grandchild.ensureConstHandle(new TestFrame(grandchild.ensureServiceContext()), alias));
            assertSame(root, grandchild.getOriginContainer(grandchild.getConstantPool().valTrue()));
        } finally {
            runtime.shutdownXVM();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeSingletonIdentityDoesNotDependOnHeapWarmup(boolean warmHeap) {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("Child").getModule());
            var child = new MainContainer(runtime, root, file.getModuleId());
            var frame = new TestFrame(child.ensureServiceContext());
            var pool = root.getConstantPool();
            var cases = Map.of(pool.valTrue(), xBoolean.TRUE, pool.valFalse(), xBoolean.FALSE,
                    pool.valNull(), xNullable.NULL);
            for (var entry : cases.entrySet()) {
                var original = entry.getKey();
                var expected = entry.getValue();
                assertSame(expected, root.ensureSingletonState(original).getHandle());
                if (warmHeap) {
                    root.f_heap.saveConstHandle(original, expected);
                }
                var alias = child.getConstantPool().register(original);
                assertNotSame(original, alias);
                assertSame(root.ensureSingletonState(original), child.ensureSingletonState(alias));
                assertSame(root, child.getOriginContainer(alias));
                assertSame(expected, child.ensureConstHandle(frame, alias));
                assertSame(expected, child.ensureConstHandle(frame, original));
                assertSame(expected, root.ensureSingletonState(original).getHandle());
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeBootstrapRestoresTheCallerPoolEvenOnFailure(boolean fail) {
        var caller = new FileStructure("Caller").getConstantPool();
        var runtime = new Runtime();
        try (var scope = ConstantPool.withPool(caller)) {
            if (fail) {
                assertThrows(BootstrapFailure.class, () -> new FailingContainer(runtime, repository()));
            } else {
                new NativeContainer(runtime, repository());
            }
            assertSame(caller, ConstantPool.getCurrentPool());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cachedMetadataReplaysWarningsToEachRequest(boolean silentFirst) {
        var repository = repository();
        var compilation = new ErrorList(100);
        var module = COMPILER.compile("""
                module WarningOwner {
                    class Base<Element> { @Atomic Int count = 1; }
                    class Derived<Element> extends Base<Element> { @Atomic @Override Int count = 2; }
                }
                """, repository, compilation);
        assertNotNull(module, compilation::toString);
        assertFalse(compilation.hasSeriousErrors(), compilation::toString);
        var file = module.getFileStructure();
        assertNull(file.linkModules(repository, false));
        var turtle = repository.loadModule(Constants.TURTLE_MODULE);
        assertNull(turtle.getFileStructure().linkModules(repository, false));
        var nakedRef = ((ClassStructure) turtle.getChild("NakedRef")).getFormalType();
        file.getConstantPool().setNakedRefType(nakedRef);
        for (var name : repository.getModuleNames()) {
            repository.loadModule(name).getConstantPool().setNakedRefType(nakedRef);
        }
        var pool = file.getConstantPool();
        var derived = ((ClassStructure) module.getChild("Derived")).getIdentityConstant().getType();
        var concrete = pool.ensureParameterizedTypeConstant(derived, pool.typeString());
        var unrelated = new FileStructure("Unrelated").getConstantPool();
        try (var scope = ConstantPool.withPool(unrelated)) {
            var first = new ErrorList(100);
            var firstSource = new Source("module FirstRequest {}");
            var firstSite = new Parser(firstSource, new ErrorList(1)).parseSource();
            var attempt = first.branch(firstSite);
            var info = silentFirst ? concrete.ensureTypeInfo() : concrete.ensureTypeInfo(attempt);
            assertFalse(first.hasErrors(), "A speculative query must not report before merge");
            attempt.merge();
            if (!silentFirst) {
                assertSame(firstSource, first.getErrors().stream()
                        .filter(error -> error.getCode().equals("VERIFY-75")).findFirst().orElseThrow().getSource());
            }
            var later = new ErrorList(100);
            assertSame(info, concrete.ensureTypeInfo(later));
            assertTrue(later.hasError("VERIFY-75"), later.getErrors()::toString);
            assertFalse(later.hasSeriousErrors(), later::toString);
            assertEquals(silentFirst ? 0 : later.getErrors().size(), first.getErrors().size());
            var count = later.getErrors().size();
            assertSame(info, concrete.ensureTypeInfo(later));
            assertEquals(count, later.getErrors().size());

            var relation = concrete.calculateRelation(pool.typeObject());
            pool.getTypeRelations().clear();
            assertEquals(relation, concrete.calculateRelation(pool.typeObject()));
            var afterClear = new ErrorList(100);
            assertSame(info, concrete.ensureTypeInfo(afterClear));
            assertEquals(later.getErrors().stream().map(error -> error.getCode()).toList(),
                    afterClear.getErrors().stream().map(error -> error.getCode()).toList());
            later.clear();
            assertSame(info, concrete.ensureTypeInfo(later));
            assertEquals(count, later.getErrors().size());

            // A cached result must use the new request's branch and source site, not the first's.
            var nextRequest = new ErrorList(100);
            var nextSource = new Source("module NextRequest {}");
            var nextSite = new Parser(nextSource, new ErrorList(1)).parseSource();
            var discarded = nextRequest.branch(nextSite);
            assertSame(info, concrete.ensureTypeInfo(discarded));
            assertTrue(discarded.hasError("VERIFY-75"));
            assertFalse(nextRequest.hasErrors());
            var committed = nextRequest.branch(nextSite);
            assertSame(info, concrete.ensureTypeInfo(committed));
            committed.merge();
            assertSame(nextSource, nextRequest.getErrors().stream()
                    .filter(error -> error.getCode().equals("VERIFY-75")).findFirst().orElseThrow().getSource());

            // Invalidating metadata must rebuild both the metadata and its diagnostic snapshot.
            concrete.invalidateTypeInfo();
            var rebuiltErrors = new ErrorList(100);
            assertNotSame(info, concrete.ensureTypeInfo(rebuiltErrors));
            assertTrue(rebuiltErrors.hasError("VERIFY-75"), rebuiltErrors.getErrors()::toString);
            // A full semantic clear must replay the same definition-relative warnings to a new
            // request, without retaining the source site or listener from earlier queries.
            pool.getTypeMetadata().clear();
            var clearedErrors = new ErrorList(100);
            var clearedInfo = concrete.ensureTypeInfo(clearedErrors);
            assertNotSame(info, clearedInfo);
            assertEquals(rebuiltErrors.getErrors().stream().map(error -> error.getCode()).toList(),
                    clearedErrors.getErrors().stream().map(error -> error.getCode()).toList());
            assertSame(unrelated, ConstantPool.getCurrentPool());
        }
    }

    @Test
    void runtimeMethodMatchesDoNotContaminateCompilerLookups() {
        var repository = repository();
        var errors = new ErrorList(100);
        var module = COMPILER.compile("""
                module LookupOwner {
                    class Target {
                        Int value = 0;
                        void take(String value) {}
                    }
                    class Producer<Element> {
                        Element get() { assert; }
                        private void take(Element value) {}
                    }
                }
                """, repository, errors);
        assertNotNull(module, errors::toString);
        assertFalse(errors.hasSeriousErrors(), errors::toString);
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository);
            var file = root.createFileStructure(module);
            var application = new MainContainer(runtime, root, file.getModuleId());
            var context = application.getTypeContext();
            var declaration = (ClassStructure) file.getModule().getChild("Target");
            var type = context.typeOf(declaration.getIdentityConstant());
            var pool = context.getDescriptorPool();
            var producer = (ClassStructure) file.getModule().getChild("Producer");
            var formal = producer.getFormalType(pool);
            assertFalse(formal.consumesFormalType("Element", Access.PUBLIC));
            assertTrue(formal.consumesFormalType("Element", Access.PRIVATE));
            assertTrue(formal.producesFormalType("Element", Access.PUBLIC));
            assertFalse(formal.consumesFormalType("Element", Access.PUBLIC));
            var info = type.ensureTypeInfo();
            var propertyType = (PropertyClassTypeConstant) pool.ensurePropertyClassTypeConstant(type,
                    pool.ensurePropertyConstant(type.getSingleUnderlyingClass(true), "value"));
            var propertyInfo = propertyType.getPropertyInfo();
            // Runtime lookup allows the wider Object parameter as a compatibility fallback;
            // compiler substitutability correctly rejects it for take(String).
            var signature = pool.ensureSignatureConstant("take",
                    new TypeConstant[] {pool.typeObject()}, TypeConstant.NO_TYPES);
            assertNull(info.getMethodBySignature(signature, type, false));
            assertNotNull(info.getMethodBySignature(signature, type, true));
            assertNull(info.getMethodBySignature(signature, type, false));
            assertNotNull(info.getMethodByNestedId(signature, true));
            assertNull(info.getMethodByNestedId(signature, false));
            var identity = pool.ensureMethodConstant(type.getSingleUnderlyingClass(true), signature);
            assertNotNull(info.getMethodById(identity, true));
            assertNull(info.getMethodById(identity, false));
            context.clearMetadata();
            var rebuilt = type.ensureTypeInfo();
            assertNotSame(info, rebuilt);
            assertNotSame(propertyInfo, propertyType.getPropertyInfo());
            assertEquals(propertyInfo.getIdentity(), propertyType.getPropertyInfo().getIdentity());
            assertNull(rebuilt.getMethodBySignature(signature, type, false));
            assertNotNull(rebuilt.getMethodBySignature(signature, type, true));
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static ModuleRepository repository() {
        var installed = Path.of("build", "install", "xdk");
        return new LinkedRepository(new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
    }

    private static final class BootstrapFailure extends RuntimeException {}

    private static final class TestFrame extends Frame {
        private TestFrame(ServiceContext context) {
            super(new Fiber(context, new CallLaterRequest(null, null, new ObjectHandle[0], 0)),
                    0, new Op[0], new ObjectHandle[0], Op.A_IGNORE, null);
        }
    }

    /** Inject failure after the native pool is bound, without clocks or external resources. */
    private static final class FailingContainer extends NativeContainer {
        private FailingContainer(Runtime runtime, ModuleRepository repository) {
            super(runtime, repository);
        }

        @Override
        public ServiceContext ensureServiceContext() {
            throw new BootstrapFailure();
        }
    }
}
