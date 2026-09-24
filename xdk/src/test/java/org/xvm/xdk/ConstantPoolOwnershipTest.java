package org.xvm.xdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.api.EmbeddingSupport;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Op;

import org.xvm.asm.constants.SingletonConstant;

import org.xvm.compiler.BuildRepository;

import org.xvm.runtime.Fiber;
import org.xvm.runtime.Frame;
import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.NestedContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.ServiceContext;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

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

    @Test
    @Timeout(60)
    void nestedSingletonsRemainIsolatedAcrossApplicationsInOneRuntime() throws Exception {
        var repository = repository();
        var runtime = new Runtime();
        try (var input = getClass().getResourceAsStream("/ownership/Singletons.x")) {
            assertNotNull(input);
            var compilation = new ErrorList(100);
            var module = COMPILER.compile(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8), repository, compilation);
            assertNotNull(module, compilation::toString);
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
                application.start(Map.of());
                application.invokeAsync("run").join();
            }
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
            original.setHandle(value);
            var alias = grandchild.getConstantPool().register(original);

            assertSame(parent, grandchild.getOriginContainer(alias));
            assertSame(original, grandchild.ensureSingletonConstant(alias));
            assertNull(alias.getHandle());
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
            var constants = new SingletonConstant[] {
                    pool.valTrue(), pool.valFalse(), pool.valNull()};
            var handles = new ObjectHandle[] {xBoolean.TRUE, xBoolean.FALSE, xNullable.NULL};
            for (int i = 0; i < constants.length; i++) {
                var original = constants[i];
                var expected = handles[i];
                assertSame(expected, original.getHandle());
                if (warmHeap) {
                    root.f_heap.saveConstHandle(original, expected);
                }
                var alias = child.getConstantPool().register(original);
                assertNotSame(original, alias);
                assertNull(alias.getHandle());
                assertSame(root, child.getOriginContainer(alias));
                assertSame(expected, child.ensureConstHandle(frame, alias));
                assertSame(expected, child.ensureConstHandle(frame, original));
                assertSame(expected, original.getHandle());
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
            var info = silentFirst ? concrete.ensureTypeInfo() : concrete.ensureTypeInfo(first);
            var later = new ErrorList(100);
            assertSame(info, concrete.ensureTypeInfo(later));
            assertTrue(later.hasError("VERIFY-75"), later.getErrors()::toString);
            assertFalse(later.hasSeriousErrors(), later::toString);
            assertEquals(silentFirst ? 0 : later.getErrors().size(), first.getErrors().size());
            var count = later.getErrors().size();
            assertSame(info, concrete.ensureTypeInfo(later));
            assertEquals(count, later.getErrors().size());
            later.clear();
            assertSame(info, concrete.ensureTypeInfo(later));
            assertEquals(count, later.getErrors().size());

            // Invalidating metadata must rebuild both the metadata and its diagnostic snapshot.
            concrete.invalidateTypeInfo();
            var rebuiltErrors = new ErrorList(100);
            assertNotSame(info, concrete.ensureTypeInfo(rebuiltErrors));
            assertTrue(rebuiltErrors.hasError("VERIFY-75"), rebuiltErrors.getErrors()::toString);
            assertSame(unrelated, ConstantPool.getCurrentPool());
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
