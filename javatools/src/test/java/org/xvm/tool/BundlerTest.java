package org.xvm.tool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Version;
import org.xvm.asm.ModuleStructure.ModuleType;

import org.xvm.asm.VersionTree;
import org.xvm.tool.LauncherOptions.BundlerOptions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the "bundle" command: options parsing, launcher dispatch, and the multi-module
 * container round-trip through FileStructure and FileRepository.
 */
class BundlerTest {

    // ----- options -------------------------------------------------------------------------------

    @Test
    void testBundlerOptionsParse() {
        var opts = BundlerOptions.parse(new String[] {
                "-v", "-L", "/lib", "-o", "/out/app.xtc", "--main", "app.example.org",
                "--include-system", "extra.example.org"});

        assertTrue(opts.isVerbose());
        assertEquals(1, opts.getModulePath().size());
        assertEquals(new File("/out/app.xtc"), opts.getOutputFile().orElseThrow());
        assertEquals("app.example.org", opts.getMainModule().orElseThrow());
        assertTrue(opts.isIncludeSystem());
        assertEquals(1, opts.getModuleSelection().size());
        assertEquals("extra.example.org", opts.getModuleSelection().getFirst());
    }

    @Test
    void testBundlerOptionsCommandLineRoundTrip() {
        var original = BundlerOptions.builder()
                .enableVerbose()
                .addModulePath(new File("/lib"))
                .setOutputFile(new File("/out/app.xtc"))
                .setMainModule("app.example.org")
                .includeSystem()
                .addModule("extra.example.org")
                .build();

        var restored = BundlerOptions.parse(original.toCommandLine());

        assertEquals(original.isVerbose(), restored.isVerbose());
        assertEquals(original.getModulePath(), restored.getModulePath());
        assertEquals(original.getOutputFile(), restored.getOutputFile());
        assertEquals(original.getMainModule(), restored.getMainModule());
        assertEquals(original.isIncludeSystem(), restored.isIncludeSystem());
        assertEquals(original.getModuleSelection(), restored.getModuleSelection());
    }

    // ----- dispatch ------------------------------------------------------------------------------

    @Test
    void testBundleCommandDispatch() {
        // -h takes the help path through the real Bundler launcher and returns success
        int result = Launcher.launch(Launcher.CMD_BUNDLE, new String[] {"-h"},
                new Console() {});
        assertEquals(0, result);
    }

    @Test
    void testBundleCommandInvalidArgs() {
        int result = Launcher.launch(new String[] {Launcher.CMD_BUNDLE, "--bad-option"});
        assertEquals(1, result);
    }

    @Test
    void testFileRepositoryNormalizesModuleFileNames(@TempDir Path tempDir) {
        var fileXtc = tempDir.resolve("sample.xtc").toFile();

        assertEquals(fileXtc, new FileRepository(fileXtc, true).getFile());
        assertEquals(fileXtc, new FileRepository(tempDir.resolve("sample.x").toFile(), true).getFile());
        assertEquals(fileXtc, new FileRepository(tempDir.resolve("sample").toFile(), true).getFile());
    }

    // ----- multi-module container round-trip -----------------------------------------------------

    @Test
    void testBundleRoundTripThroughFileRepository(@TempDir Path tempDir) throws Exception {
        var fileA = new FileStructure("ModA");
        var fileB = new FileStructure("ModB");
        var verA  = new Version("1.0");
        fileA.getModule().setVersion(verA);

        // merge ModA into ModB's container, per the Bundler recipe
        var bundle = new FileStructure(fileB.getModule(), false);
        bundle.merge(fileA.getModule(), false, false);
        bundle.getChild("ModA").markEmbedded();

        assertTrue(bundle.isBundle());
        assertEquals("ModB", bundle.getModuleId().getName());

        var fileOut = tempDir.resolve("bundle.xtc").toFile();
        bundle.writeTo(fileOut);

        var metadata = FileStructure.readFileInfo(fileOut);
        assertEquals(FileStructure.FileKind.Library, metadata.kind());
        assertEquals(Set.of("ModB", "ModA"), metadata.modules().keySet());
        assertEquals(new VersionTree<Boolean>(verA, true), metadata.modules().get("ModA"));

        // the container round-trips with both modules and their types intact
        var reread = new FileStructure(fileOut);
        assertEquals("ModB", reread.getModuleId().getName());
        assertNotNull(reread.getChild("ModA"));
        assertEquals(ModuleType.Embedded, reread.getChild("ModA").getModuleType());
        assertFalse(reread.getChild("ModA").isFingerprint());

        // the generalized FileRepository exposes and serves both modules by name
        var repo = new FileRepository(fileOut, true);
        assertEquals(2, repo.getModuleNames().size());
        assertTrue(repo.getModuleNames().containsAll(Set.of("ModA", "ModB")));
        assertTrue(repo.getAvailableVersions("ModA").contains(verA));

        var moduleMain = repo.loadModule("ModB");
        assertNotNull(moduleMain);
        assertTrue(moduleMain.isMainModule());

        // non-main members are served as detached copies anchored on their own file structure
        var moduleA = repo.loadModule("ModA");
        assertNotNull(moduleA);
        assertNotSame(moduleMain.getFileStructure(), moduleA.getFileStructure());
        assertEquals("ModA", moduleA.getFileStructure().getModuleId().getName());
        assertTrue(moduleA.isMainModule());
        assertEquals(ModuleType.Primary, moduleA.getModuleType());

        // the detached copy is memoized
        assertSame(moduleA, repo.loadModule("ModA"));

        // version-aware lookup routes around the main-module-only default implementation
        assertSame(moduleA, repo.loadModule("ModA", null, false));
        var moduleAVersioned = repo.loadModule("ModA", verA, true);
        assertNotNull(moduleAVersioned);
        assertTrue(moduleAVersioned.isMainModule());
        assertEquals(verA, moduleAVersioned.getIdentityConstant().getVersion());
        assertEquals(verA, moduleAVersioned.getVersion());

        assertNull(repo.loadModule("NoSuchModule"));
    }

    @Test
    void testSingleModuleFileRepositoryUnchanged(@TempDir Path tempDir) throws Exception {
        var file    = new FileStructure("Solo");
        var version = new Version("2.1");
        file.getModule().setVersion(version);
        var fileOut = tempDir.resolve("solo.xtc").toFile();
        file.writeTo(fileOut);

        var metadata = FileStructure.readFileInfo(fileOut);
        assertEquals(FileStructure.FileKind.Single, metadata.kind());
        assertEquals(Set.of("Solo"), metadata.modules().keySet());
        assertEquals(new VersionTree<Boolean>(version, true), metadata.modules().get("Solo"));

        var repo = new FileRepository(fileOut, true);
        assertEquals(Set.of("Solo"), repo.getModuleNames());
        assertTrue(repo.getAvailableVersions("Solo").contains(version));

        var module = repo.loadModule("Solo");
        assertNotNull(module);
        assertTrue(module.isMainModule());
        assertNull(repo.loadModule("Other"));

        var moduleVersioned = repo.loadModule("Solo", version, true);
        assertNotNull(moduleVersioned);
        assertTrue(moduleVersioned.isMainModule());
        assertEquals(version, moduleVersioned.getIdentityConstant().getVersion());
        assertEquals(version, moduleVersioned.getVersion());
    }

    @Test
    void testDuplicateExplicitModuleSelectionsAreRejected(@TempDir Path tempDir) throws Exception {
        var fileFirst  = tempDir.resolve("first.xtc").toFile();
        var fileSecond = tempDir.resolve("second.xtc").toFile();
        new FileStructure("Dup").writeTo(fileFirst);
        new FileStructure("Dup").writeTo(fileSecond);

        var console = new CaptureConsole();
        int result = Launcher.launch(Launcher.CMD_BUNDLE, new String[] {
                "-o", tempDir.resolve("out.xtc").toString(),
                fileFirst.getPath(),
                fileSecond.getPath()}, console);

        assertEquals(1, result);
        var output = console.getAllOutput();
        assertTrue(output.contains("Duplicate explicit module selection"));
        assertTrue(output.contains("Dup"));
        assertTrue(output.contains(fileSecond.getPath()));
    }

    // ----- reproducibility -----------------------------------------------------------------------

    @Test
    void testBundleOutputBytesIndependentOfInputOrder(@TempDir Path tempDir) throws Exception {
        // with explicit creation timestamps there is no hidden wall-clock input left anywhere in
        // the pipeline: each bundling run generates its own inputs completely from scratch, and
        // the runs both generate and present them in opposite orders - the produced binaries must
        // still be byte-identical
        var timestamp = Instant.parse("2026-01-01T00:00:00Z");

        byte[] abForward  = generateAndBundle(tempDir.resolve("forward"), timestamp,
                List.of("MainMod", "LibA", "LibB"));
        byte[] abReversed = generateAndBundle(tempDir.resolve("reversed"), timestamp,
                List.of("LibB", "LibA", "MainMod"));
        assertArrayEquals(abForward, abReversed);

        // write -> read -> write must also be a fixpoint: re-serializing a read-back bundle
        // reproduces the identical binary
        var reread = new FileStructure(tempDir.resolve("forward").resolve("bundle.xtc").toFile());
        var abRewritten = new ByteArrayOutputStream();
        reread.writeTo(abRewritten);
        assertArrayEquals(abForward, abRewritten.toByteArray());
    }

    /**
     * Generate fresh single-module inputs into the given directory - in the given order, stamped
     * with the given timestamp - bundle them with the real bundle command, and return the bundle
     * file's bytes.
     */
    private static byte[] generateAndBundle(Path dir, Instant timestamp, List<String> moduleNames)
            throws Exception {
        Files.createDirectories(dir);
        var inputs = new ArrayList<File>();
        for (var name : moduleNames) {
            var file = dir.resolve(name + ".xtc").toFile();
            new FileStructure(name, timestamp).writeTo(file);
            inputs.add(file);
        }

        var fileOut = dir.resolve("bundle.xtc");
        assertEquals(0, launchBundle(fileOut, inputs.toArray(new File[0])));
        return Files.readAllBytes(fileOut);
    }

    private static int launchBundle(Path fileOut, File... inputs) {
        var args = new ArrayList<>(List.of("-o", fileOut.toString(), "--main", "MainMod"));
        for (var input : inputs) {
            args.add(input.getPath());
        }
        return Launcher.launch(Launcher.CMD_BUNDLE, args.toArray(new String[0]),
                new CaptureConsole());
    }

    private static final class CaptureConsole implements Console {
        private final List<String> lines = new ArrayList<>();

        @Override
        public String out(Object o) {
            if (o != null) {
                lines.add(o.toString());
            }
            return "";
        }

        String getAllOutput() {
            return String.join("\n", lines);
        }
    }
}
