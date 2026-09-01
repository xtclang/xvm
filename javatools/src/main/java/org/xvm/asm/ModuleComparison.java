package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.nio.file.Files;

import java.time.Instant;

import java.util.Arrays;

/**
 * Compares two compiled modules for equivalence, ignoring their creation timestamp.
 *
 * <p>A {@code .xtc} carries the wall-clock instant at which its {@link FileStructure} was created,
 * so two compiles of identical source are never byte-identical (see the timestamp issue). That makes
 * a raw byte comparison useless for the question people actually want to ask: <em>did these two
 * compiles produce the same module?</em> - which is how you check that a reused or parallel compiler
 * still emits what a fresh one does.</p>
 *
 * <p>The comparison works by normalization rather than by walking the structure: both modules are
 * stamped with the same fixed timestamp and re-serialized through the ordinary writer, and the
 * results are compared. Everything the serializer emits therefore participates - constants, pool
 * ordering, components, and code - with no hand-maintained list of fields to keep in sync as the
 * format grows, which is the failure mode a structural walker would have.</p>
 */
public final class ModuleComparison {
    /**
     * The instant both modules are stamped with before comparison. Any fixed value works; the epoch
     * is used because it is recognisable in a hex dump as "normalized" rather than real.
     */
    private static final Instant NORMALIZED = Instant.EPOCH;

    private ModuleComparison() {}

    /**
     * The outcome of comparing two modules.
     *
     * @param equivalent      true iff the modules are identical once their timestamps are ignored
     * @param firstDifference the byte offset of the first difference, or -1 when equivalent
     * @param detail          a human-readable description of the outcome
     */
    public record Result(boolean equivalent, int firstDifference, String detail) {
        @Override
        public String toString() {
            return detail;
        }
    }

    /**
     * Compare two compiled modules on disk.
     *
     * @param file1  the first {@code .xtc}
     * @param file2  the second {@code .xtc}
     *
     * @return the comparison result
     */
    public static Result compare(File file1, File file2) throws IOException {
        return compare(Files.readAllBytes(file1.toPath()), Files.readAllBytes(file2.toPath()));
    }

    /**
     * Compare two compiled modules held as serialized bytes.
     *
     * @param abModule1  the first module's bytes
     * @param abModule2  the second module's bytes
     *
     * @return the comparison result
     */
    public static Result compare(byte[] abModule1, byte[] abModule2) throws IOException {
        byte[] abNorm1 = normalize(abModule1);
        byte[] abNorm2 = normalize(abModule2);

        if (Arrays.equals(abNorm1, abNorm2)) {
            return new Result(true, -1, "modules are equivalent (" + abNorm1.length + " bytes)");
        }

        int cMin = Math.min(abNorm1.length, abNorm2.length);
        int ofFirst = -1;
        int cDiff   = 0;
        for (int of = 0; of < cMin; of++) {
            if (abNorm1[of] != abNorm2[of]) {
                if (ofFirst < 0) {
                    ofFirst = of;
                }
                cDiff++;
            }
        }
        if (ofFirst < 0) {
            ofFirst = cMin;
        }
        return new Result(false, ofFirst,
                "modules differ: lengths " + abNorm1.length + '/' + abNorm2.length
                        + ", first difference at byte " + ofFirst
                        + ", " + cDiff + " differing byte(s) in the common prefix");
    }

    /**
     * Re-emit a module with a fixed timestamp, so that two modules differing only in creation time
     * serialize identically.
     */
    private static byte[] normalize(byte[] abModule) throws IOException {
        FileStructure   struct = new FileStructure(new ByteArrayInputStream(abModule));
        ModuleStructure module = struct.getModule();

        module.setTimestamp(struct.getConstantPool().ensureTimeConstant(NORMALIZED));

        var out = new ByteArrayOutputStream(abModule.length);
        struct.writeTo(out);
        return out.toByteArray();
    }
}
