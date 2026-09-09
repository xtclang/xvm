package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import org.xvm.api.ModuleView;
import org.xvm.test.XdkOutputs;
import org.xvm.util.Handy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Checks that {@link Op#fields()} accounts for everything {@link Op#write} emits.
 *
 * <p>This is the oracle for the field model, and it exists because coverage claims are not
 * self-checking. Every op class answering {@code fields()} says nothing about whether it answers
 * COMPLETELY: when this check was first run, 48 op classes were silently under- or over-reporting -
 * {@code OpInvocable} modeled a return its {@code write} does not emit while every concrete
 * {@code Invoke_*} omitted the arguments it does, so an invoke rendered with its arguments missing
 * and nothing looked wrong. A dump that quietly drops half an op is worse than one that admits it
 * cannot read it.</p>
 *
 * <p>The comparison is deliberately crude and therefore hard to fool: serialize the op, count the
 * packed ints, and require the same number of fields. It does not check that the fields mean the
 * right thing - only that none is missing or invented.</p>
 */
public class OpFieldCompletenessTest {
    @Test
    public void everyOpModelsAsManyFieldsAsItWrites() throws Exception {
        Path lib = XdkOutputs.root().resolve("xdk/build/install/xdk/lib");
        assumeTrue(Files.isDirectory(lib), "compiled XDK lib is required: " + lib);

        List<Path> modules;
        try (var paths = Files.list(lib)) {
            modules = paths.filter(p -> p.toString().endsWith(".xtc")).sorted().toList();
        }
        assumeTrue(!modules.isEmpty(), "no compiled modules found in " + lib);

        var mismatches = new TreeMap<String, String>();
        var unverifiable = new TreeMap<String, Integer>();
        long checked = 0;
        for (Path path : modules) {
            ModuleView view = ModuleView.open(path);
            for (MethodStructure method : view.methods().toList()) {
                for (Op op : view.ops(method)) {
                    List<OpField> fields = op.fields().orElse(null);
                    if (fields == null) {
                        mismatches.putIfAbsent(op.getClass().getSimpleName(), "models no fields");
                        continue;
                    }
                    int written;
                    try {
                        written = countWrittenValues(op);
                    } catch (RuntimeException | Error e) {
                        // Some ops cannot be re-serialized from a module read off disk: the Var
                        // family's write() reaches through a Register that only exists while
                        // compiling, and NPEs. Those are recorded BY CLASS rather than merely
                        // counted, so the blind spot is explicit and cannot quietly widen - the
                        // first version of this test just skipped them, which meant a headline of
                        // "0 mismatches" over a sample that silently excluded whole op families.
                        unverifiable.merge(op.getClass().getSimpleName(), 1, Integer::sum);
                        continue;
                    }
                    checked++;
                    if (written != fields.size()) {
                        mismatches.putIfAbsent(op.getClass().getSimpleName(),
                                "writes " + written + " values but models " + fields.size());
                    }
                }
            }
        }

        long sampled = checked;
        assertTrue(sampled > 10_000, () -> "expected a substantial sample, checked " + sampled);
        assertEquals(Map.of(), mismatches,
                () -> "op classes whose field model disagrees with write(): " + mismatches);

        // the blind spot, pinned. These op classes cannot be re-serialized from a disk-read module,
        // so this oracle says nothing about them; naming them here means a NEW class joining that
        // set is a visible failure rather than a silent reduction in what is actually checked.
        assertEquals(Set.of("GuardStart", "Var", "Var_D", "Var_DN", "Var_I", "Var_IN", "Var_M",
                        "Var_N", "Var_S", "Var_SN", "Var_T"),
                unverifiable.keySet(),
                () -> "op classes this oracle cannot verify: " + unverifiable);
    }

    /**
     * @param op  the op to serialize
     *
     * @return how many packed values {@code op.write} emits after the opcode
     */
    private static int countWrittenValues(Op op) throws Exception {
        var bytes = new ByteArrayOutputStream();
        op.write(new DataOutputStream(bytes), null);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            in.readByte();                                  // the opcode itself
            int count = 0;
            while (in.available() > 0) {
                Handy.readPackedInt(in);
                count++;
            }
            return count;
        }
    }
}
