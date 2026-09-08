package org.xvm.asm.constants;


import java.io.IOException;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Nothing may ask for a TypeInfo by handing it a listener that means "do not listen".
 *
 * <p>{@code ensureTypeInfo(errs)} both builds a type and validates it, so its listener parameter was
 * doing double duty - it named a sink, but callers were really using it to say which of the two
 * operations they wanted. {@link TypeConstant#typeInfo()} says the same thing by being a different
 * method, and that is where the idiom is spelled once so nowhere else has to.
 *
 * <p>Two spellings are wrong for different reasons:
 *
 * <ul>
 * <li>{@code ensureTypeInfo(PROBE)} is the right VALUE and the wrong spelling - it puts the mode
 *     back at the call site. Allowed only inside {@code typeInfo()} itself;</li>
 * <li>{@code ensureTypeInfo(BLACKHOLE)} is wrong outright - asking for a TypeInfo is a question,
 *     never a diagnostic with nowhere to go. Allowed nowhere.</li>
 * </ul>
 *
 * <p><b>Reads the compiled classes, not the source.</b> This gate previously scanned {@code .java}
 * text, which is the pattern `54bcea306` deleted five tests for - it passes when the code is spelled
 * the expected way and breaks on reformatting. In bytecode the check is exact rather than textual:
 * the argument is loaded immediately before the call, so a {@code getstatic} of {@code PROBE} or
 * {@code BLACKHOLE} directly followed by an {@code ensureTypeInfo} invocation IS the violation,
 * whatever whitespace, line breaks or local variables surround it in source.
 */
public class TypeInfoModeIsExplicitTest {
    private static final String LISTENER = "org/xvm/asm/ErrorListener";
    private static final String ENSURE   = "ensureTypeInfo";

    /**
     * The single legitimate occurrence: the body of {@code typeInfo()}, where the idiom lives.
     */
    private static final String ALLOWED_IN = "org/xvm/asm/constants/TypeConstant.typeInfo";

    @Test
    public void onlyTypeInfoSpellsOutTheComputeMode() throws IOException, URISyntaxException {
        List<String> offenders = callSitesPassing("PROBE");
        offenders.removeIf(ALLOWED_IN::equals);

        assertTrue(offenders.isEmpty(),
                () -> "call typeInfo() instead of naming the mode:\n  " + String.join("\n  ", offenders));
    }

    @Test
    public void nothingAsksForATypeInfoWithAnAbsentSink() throws IOException, URISyntaxException {
        List<String> offenders = callSitesPassing("BLACKHOLE");

        assertTrue(offenders.isEmpty(),
                () -> "asking for a TypeInfo is a question; use typeInfo():\n  "
                        + String.join("\n  ", offenders));
    }

    /**
     * The gate is worthless if it is scanning nothing, so prove it found the tree - and prove the
     * one legitimate call site is still there, since if {@code typeInfo()} stopped spelling the
     * idiom this gate would be guarding a rule nobody follows any more.
     */
    @Test
    public void theGateIsReadingRealBytecode() throws IOException, URISyntaxException {
        int cScanned = scanned();
        assertTrue(cScanned > 500, () -> "expected the compiled tree, scanned " + cScanned);
        assertEquals(List.of(ALLOWED_IN), callSitesPassing("PROBE"),
                "typeInfo() must still be the one place that names the compute mode");
    }

    /**
     * @param sConstant  the {@code ErrorListener} constant to look for
     *
     * @return "package/Class.method" for every {@code ensureTypeInfo} call whose argument was loaded
     *         straight from that constant
     */
    private static List<String> callSitesPassing(String sConstant) throws IOException, URISyntaxException {
        var found = new ArrayList<String>();
        forEachClass((sClass, model) -> {
            for (var method : model.methods()) {
                method.code().ifPresent(code -> {
                    List<CodeElement> list = code.elementList();
                    for (int i = 1; i < list.size(); ++i) {
                        if (list.get(i) instanceof InvokeInstruction invoke
                                && ENSURE.equals(invoke.method().name().stringValue())
                                && list.get(i - 1) instanceof FieldInstruction field
                                && LISTENER.equals(field.owner().asInternalName())
                                && sConstant.equals(field.name().stringValue())) {
                            found.add(sClass + '.' + method.methodName().stringValue());
                        }
                    }
                });
            }
        });
        return found;
    }

    private static int scanned() throws IOException, URISyntaxException {
        int[] count = {0};
        forEachClass((sClass, model) -> count[0]++);
        return count[0];
    }

    private interface ClassVisitor {
        void visit(String sClass, java.lang.classfile.ClassModel model);
    }

    private static void forEachClass(ClassVisitor visitor) throws IOException, URISyntaxException {
        Path anchor = Path.of(TypeConstant.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(anchor),
                "the compiler must be scannable as exploded classes, but the code source is " + anchor);

        try (Stream<Path> files = Files.walk(anchor.resolve("org/xvm"))) {
            for (Path path : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String sClass = anchor.relativize(path).toString()
                        .replace(".class", "").replace(java.io.File.separatorChar, '/');
                visitor.visit(sClass, ClassFile.of().parse(Files.readAllBytes(path)));
            }
        }
    }
}
