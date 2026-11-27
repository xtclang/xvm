package org.xtclang.plugin.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * The revision of a javatools jar ({@link XtcLspSourceSetTask#javatoolsRevision}) tells two builds of the compiler and
 * runtime apart, but not two builds of the same one at other commits or branches, whose build information alone differs.
 */
class JavatoolsRevisionTest {
    private static final String INFO = "#Build information generated at build time\ngit.commit=%s\ngit.status=%s\nxdk.version=%s\n";

    @Test
    void aJarBuiltAtAnotherCommitOrBranchHasTheSameRevisionAndOneWithAnotherClassOrVersionAnother() throws IOException {
        final var built = revision("org/xvm/Compiler.class", "a compiler", INFO.formatted("82488d09", "lagergren/foreign-xdk", "0.4.4"));
        // another commit and branch, and the entries in another order
        final var later = new LinkedHashMap<String, String>();
        later.put("build-info.properties", INFO.formatted("f2112f7b", "master", "0.4.4"));
        later.put("org/xvm/Compiler.class", "a compiler");
        assertEquals(built, XtcLspSourceSetTask.javatoolsRevision(jar(later)));
        assertNotEquals(built, revision("org/xvm/Compiler.class", "another compiler", INFO.formatted("82488d09", "lagergren/foreign-xdk", "0.4.4")));
        assertNotEquals(built, revision("org/xvm/Compiler.class", "a compiler", INFO.formatted("82488d09", "lagergren/foreign-xdk", "0.4.5")));
        assertNotEquals(built, revision("org/xvm/Runtime.class", "a compiler", INFO.formatted("82488d09", "lagergren/foreign-xdk", "0.4.4")));
    }

    private static String revision(final String name, final String content, final String info) throws IOException {
        final var entries = new LinkedHashMap<String, String>();
        entries.put(name, content);
        entries.put("build-info.properties", info);
        return XtcLspSourceSetTask.javatoolsRevision(jar(entries));
    }

    private static byte[] jar(final Map<String, String> entries) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        try (var jar = new ZipOutputStream(bytes)) {
            jar.putNextEntry(new ZipEntry("META-INF/"));
            jar.closeEntry();
            for (final var entry : entries.entrySet()) {
                jar.putNextEntry(new ZipEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
