package org.xvm.runtime.template.reflect;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards register-bound refs against view cloning (must-audit row 161, mechanism 1). A
 * register-bound ref reads and writes through {@code m_frame.f_ahVar[m_iVar]}, and
 * {@code Frame.VarInfo.release()} dereferences only the single ref instance it cached at scope
 * exit. On the unguarded shape - master's shape - a shallow {@code cloneAs} view carried its own
 * frame/register binding, missed the dereference transition, and kept reading and writing the
 * recycled register slot, which by then belongs to an unrelated variable: silent value corruption
 * with no error. The designed mechanism for a second handle onto a register-bound ref is REF_REF
 * delegation ({@code createRegisterRef}), never a shallow copy.
 */
public class RefViewGuardTest {
    /**
     * Cloning a register-bound ref must fail loudly. Red on the unguarded shape, where cloneAs
     * quietly produced the dangling view.
     */
    @Test
    public void registerBoundRefRefusesViewCloning() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository(), ErrorListener.RUNTIME);
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var hRegisterBound = new RegisterBoundProbe(clz);
            var error = assertThrows(IllegalStateException.class,
                    () -> hRegisterBound.cloneAs(view(clz)),
                    "a register-bound ref view would keep reading a recycled register slot");
            assertTrue(error.getMessage().contains("register-bound"), error.getMessage());

            // no over-tightening: a dereferenced (referent-bound) ref must still support views -
            // inflated property refs depend on it for access-view creation
            var hReferentBound = new RefHandle(clz, "probe") {};
            assertNotSame(hReferentBound, hReferentBound.cloneAs(view(clz)),
                    "referent-bound refs must still clone into access views");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * A probe with the register-bound shape: the {@code m_iVar >= 0} binding that the codebase
     * pairs invariantly with a live frame ({@code isAssigned} asserts the pair).
     */
    private static final class RegisterBoundProbe extends RefHandle {
        RegisterBoundProbe(TypeComposition clazz) {
            super(clazz, "probe");
            m_iVar = 0;
        }
    }

    private static TypeComposition view(ClassComposition clz) {
        return clz.ensureAccess(Access.PROTECTED);
    }

    // ----- helpers (same discovery as ClassCompositionSafePublicationTest) ----------------------

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static ModuleRepository systemRepository() {
        var manualRepository = repositoryFor("manualTests/build/xtc/xdk/lib");
        if (manualRepository != null) {
            return manualRepository;
        }

        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(RefViewGuardTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        case 1  -> repositories.get(0);
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory()
                ? new DirRepository(directory, true)
                : null;
    }

    private static File checkoutFile(String path) {
        return checkoutRoot().resolve(path).toFile();
    }

    private static Path checkoutRoot() {
        var path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("javatools")) &&
                    Files.isDirectory(path.resolve("manualTests"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate checkout root");
    }
}
