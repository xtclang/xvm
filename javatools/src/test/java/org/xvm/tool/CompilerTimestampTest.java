package org.xvm.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

/**
 * {@code xtc build} stamps a compiled module with no time, so that the same source compiles to the same
 * bytes, unless {@code --timestamp} asks for one: "now", or an ISO-8601 instant.
 */
class CompilerTimestampTest {
    @TempDir
    Path work;

    @Test
    void aModuleCarriesNoTimeUnlessOneIsAskedFor() throws IOException, URISyntaxException {
        assertNull(compile().getTimestamp());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"),
                timestamp(compile("--timestamp", "2026-01-01T00:00:00Z")));

        Instant before  = Instant.now();
        Instant stamped = timestamp(compile("--timestamp", "now"));
        assertFalse(stamped.isBefore(before), () -> stamped + " is before " + before);
        assertFalse(stamped.isAfter(Instant.now()), stamped::toString);
    }

    @Test
    void theDescriptionShowsOnlyATimeThatWasAskedFor() throws IOException, URISyntaxException {
        assertFalse(compile().getDescription().contains("timestamp="));
        assertTrue(compile("--timestamp", "2026-01-01T00:00:00Z").getDescription()
                .contains("timestamp=\"2026-01-01T00:00:00Z\""));
    }

    @Test
    void aTimeThatIsNeitherNowNorAnInstantFailsTheBuild() throws IOException, URISyntaxException {
        assertNotEquals(0, launch(Files.createTempDirectory(work, "out"), "--timestamp", "yesterday"));
    }

    /**
     * @return the module that {@code xtc build} wrote, with the specified extra options
     */
    private ModuleStructure compile(String... options) throws IOException, URISyntaxException {
        Path out = Files.createTempDirectory(work, "out");
        assertEquals(0, launch(out, options));
        try (var files = Files.list(out)) {
            File module = files.filter(file -> file.toString().endsWith(".xtc")).findFirst().orElseThrow().toFile();
            return new FileStructure(module).getModule();
        }
    }

    private int launch(Path out, String... options) throws IOException, URISyntaxException {
        var modules = CompilerTimestampTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        Path source = work.resolve("Stamped.x");
        Files.writeString(source, "module Stamped {}\n");

        List<String> args = new ArrayList<>(List.of(options));
        args.addAll(List.of("-L", new File(modules.toURI()).getPath(), "-o", out.toString(), source.toString()));
        return Launcher.launch(Launcher.CMD_BUILD, args.toArray(String[]::new), new Console() {}, new ErrorList(25));
    }

    private static Instant timestamp(ModuleStructure module) {
        return Instant.parse(module.getTimestamp().getValue());
    }
}
