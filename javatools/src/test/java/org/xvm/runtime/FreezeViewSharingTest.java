package org.xvm.runtime;


import java.io.File;
import java.io.IOException;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.regex.Pattern;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;

import org.xvm.test.XdkOutputs;
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
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, XdkOutputs.systemRepository(), ErrorListener.RUNTIME);
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
     * Every write to the mutability flag happens in a constructor.
     *
     * <p>Read from the compiled classes: a {@code putfield m_fMutable} from anywhere but a
     * constructor or one of the sanctioned transitions is the defect, wherever it is and however it
     * is spelled. The previous version counted regex matches across the source and asserted the
     * total was exactly 24 - a number any refactor broke, and which never checked the property its
     * own name claims.</p>
     */
    /**
     * The only methods allowed to write the flag: construction, and the two transitions that exist
     * precisely so a change is visible to every view sharing the freeze state.
     */
    private static final List<String> MUTABILITY_WRITERS =
            List.of("<init>", "setMutable", "makeImmutable");

    @Test
    public void mutabilityFlagWritesAreConstructorOnly() throws IOException, URISyntaxException {
        var listOffenders = new ArrayList<String>();
        var cScanned      = 0;

        Path pathAnchor = Path.of(ObjectHandle.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(pathAnchor),
                "the runtime must be scannable as exploded classes, but the code source is "
                        + pathAnchor);

        Path pathRuntime = pathAnchor.resolve("org/xvm/runtime");
        try (Stream<Path> files = Files.walk(pathRuntime)) {
            for (Path path : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                var sClass = pathAnchor.relativize(path).toString();
                for (var method : ClassFile.of().parse(Files.readAllBytes(path)).methods()) {
                    var sMethod = method.methodName().stringValue();
                    method.code().ifPresent(code -> code.elementList().stream()
                            .filter(FieldInstruction.class::isInstance)
                            .map(FieldInstruction.class::cast)
                            .filter(field -> "m_fMutable".equals(field.name().stringValue())
                                    && field.opcode() == Opcode.PUTFIELD)
                            .forEach(field -> {
                                if (!MUTABILITY_WRITERS.contains(sMethod)) {
                                    listOffenders.add(sClass + '.' + sMethod);
                                }
                            }));
                }
                cScanned++;
            }
        }

        assertTrue(cScanned > 100,
                "expected the runtime tree; only " + cScanned + " classes were scanned");
        assertEquals(List.of(), listOffenders,
                "m_fMutable must only be written in a constructor; a post-construction transition"
                        + " has to go through setMutable()/makeImmutable() so views share the"
                        + " freeze state");
    }

    private static Path mainSourceRoot() {
        var local = Path.of("src/main/java");
        return Files.isDirectory(local) ? local : XdkOutputs.root().resolve("javatools/src/main/java");
    }

    // ----- helpers (same discovery as ClassCompositionSafePublicationTest) ----------------------





}
