package org.xvm.runtime;


import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.regex.Pattern;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the shared freeze state of object views (must-audit row 161, mechanism 5). The
 * mutability flag was a per-instance field shallow-copied by {@code cloneAs}, while the object's
 * field storage stays shared by all views: on the old shape - master's shape -
 * {@code makeImmutable()} through one view left sibling views claiming mutability and therefore
 * willing to write into the frozen shared field array. The freeze state now migrates into a cell
 * shared by all views, installed lazily (and CAS-raced-safely) by the first view clone, so
 * handles that never have views never pay for it.
 */
public class FreezeViewSharingTest {
    /**
     * Freezing through any view must be observed by every view. Red on master's per-view flag:
     * the sibling view kept reporting mutable.
     */
    @Test
    public void freezeThroughOneViewFreezesAllViews() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var hObject = new GenericHandle(clz);
            var hView   = (GenericHandle) hObject.cloneAs(clz.ensureAccess(Access.PROTECTED));
            assertNotSame(hObject, hView);
            assertTrue(hObject.isMutable());
            assertTrue(hView.isMutable());

            assertTrue(hView.makeImmutable(), "freezing an empty structure must succeed");

            assertFalse(hView.isMutable());
            assertFalse(hObject.isMutable(),
                    "a freeze through one view must be visible through every view;"
                            + " a still-mutable sibling would write into the frozen shared storage");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The flag's write discipline is constructor-only: views cannot exist during construction, so
     * direct field writes there are safe, but every post-construction transition must go through
     * setMutable()/makeImmutable() or it bypasses the shared freeze cell and reintroduces the
     * split. This ratchet pins the blessed constructor-write count; if it fails, either a new
     * constructor write was added (update the count) or - the bug this guards against - a
     * post-construction path writes the field directly and must use setMutable() instead.
     */
    @Test
    public void mutabilityFlagWritesAreConstructorOnly() throws IOException {
        var pattern = Pattern.compile("m_fMutable\\s*=[^=]");
        var count   = 0;
        try (Stream<Path> paths = Files.walk(mainSourceRoot())) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(Files.readString(path));
                while (matcher.find()) {
                    ++count;
                }
            }
        }
        assertEquals(24, count,
                "m_fMutable writes must stay constructor-only; post-construction transitions"
                        + " must use setMutable()/makeImmutable() so views share the freeze state");
    }

    private static Path mainSourceRoot() {
        var local = Path.of("src/main/java");
        return Files.isDirectory(local) ? local : checkoutRoot().resolve("javatools/src/main/java");
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
                .map(FreezeViewSharingTest::repositoryFor)
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
