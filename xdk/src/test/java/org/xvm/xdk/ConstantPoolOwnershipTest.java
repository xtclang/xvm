package org.xvm.xdk;

import java.nio.file.Path;

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

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.ServiceContext;

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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeBootstrapRestoresTheCallerPoolEvenOnFailure(boolean fail) {
        var caller = new FileStructure("Caller").getConstantPool();
        try (var scope = ConstantPool.withPool(caller); var runtime = new Runtime()) {
            if (fail) {
                assertThrows(BootstrapFailure.class, () -> new FailingContainer(runtime, repository()));
            } else {
                new NativeContainer(runtime, repository());
            }
            assertSame(caller, ConstantPool.getCurrentPool());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cachedMetadataReplaysWarningsToEachRequest(boolean silentFirst) {
        var repository = repository();
        try (var session = EmbeddingSupport.create(repository)) {
            var compilation = new ErrorList(100);
            var module = session.compile("""
                    module WarningOwner {
                        class Base<Element> { @Atomic Int count = 1; }
                        class Derived<Element> extends Base<Element> { @Atomic @Override Int count = 2; }
                    }
                    """, null, compilation);
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
    }

    private static ModuleRepository repository() {
        var installed = Path.of("build", "install", "xdk");
        return new LinkedRepository(new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
    }

    private static final class BootstrapFailure extends RuntimeException {}

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
