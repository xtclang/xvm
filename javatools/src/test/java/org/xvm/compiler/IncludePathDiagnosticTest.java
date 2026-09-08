package org.xvm.compiler;


import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.ErrorListener;

import org.xvm.tool.ModuleInfo;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * "It is not there" and "it is there and I could not read it" are different facts, and the include
 * path reported the first for both. Master issue 52.
 *
 * <p>The mechanism is not what it looks like. {@code Source.includeString} gates on
 * {@code Handy.checkReadable}, which answers false - <i>without throwing</i> - for a path that
 * exists but is a directory or has no read permission. So the common unreadable case produces no
 * exception at all, and a message keyed off a caught {@code IOException} would still have called it
 * missing. What distinguishes them is {@code resolvePath}: if that resolved, the path exists.
 *
 * <p>This test uses a directory as the unreadable thing rather than a chmod, so it behaves the same
 * for every user and on every filesystem - including CI running as root, where a permission bit
 * would not stop a read.
 */
public class IncludePathDiagnosticTest {
    @TempDir
    Path tempDir;

    @Test
    public void aPathThatResolvesButCannotBeReadIsNotReportedAsMissing() throws IOException {
        // a resource directory containing a SUBDIRECTORY - resolvable, but not a readable file
        Path resources = Files.createDirectory(tempDir.resolve("resources"));
        Files.createDirectory(resources.resolve("sub"));

        Source source = sourceIncluding("$/sub", resources);

        CompilerException e = assertThrows(CompilerException.class,
                () -> new Parser(source, ErrorListener.PROBE).parseSource());

        assertTrue(e.getMessage().contains("cannot read"),
                () -> "the path resolved, so this is a read failure, not an absence: " + e.getMessage());
        assertTrue(e.getMessage().contains("sub"), () -> e.getMessage());
    }

    /**
     * The other half: a path that genuinely does not resolve must still say so. A fix that called
     * everything unreadable would pass the test above and be just as wrong.
     */
    @Test
    public void aPathThatDoesNotResolveIsStillReportedAsMissing() throws IOException {
        Path resources = Files.createDirectory(tempDir.resolve("resources"));

        Source source = sourceIncluding("$/nothing-here", resources);

        CompilerException e = assertThrows(CompilerException.class,
                () -> new Parser(source, ErrorListener.PROBE).parseSource());

        assertTrue(e.getMessage().contains("no such"),
                () -> "nothing resolved, so it really is missing: " + e.getMessage());
    }

    /**
     * Build a Source that can resolve resource paths. A plain {@code new Source(String)} cannot -
     * {@code resolvePath} goes through a {@code ModuleInfo.FileNode}, so the module has to exist on
     * disk with its resource directory declared.
     */
    private Source sourceIncluding(String sInclude, Path resources) throws IOException {
        Path module = tempDir.resolve("TestInclude.x");
        Files.writeString(module, """
                module TestInclude {
                    static String Contents = %s;
                }
                """.formatted(sInclude));

        var info = new ModuleInfo(module.toFile(), false, List.of(resources.toFile()), (File) null);
        var node = (ModuleInfo.FileNode) info.getSourceTree(ErrorListener.PROBE);
        assertNotNull(node, "the fixture must produce a source node, or the test proves nothing");
        return new Source(node);
    }
}
