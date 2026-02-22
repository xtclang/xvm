package org.xvm.tool;

import java.io.File;
import java.io.IOException;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.BundledFileRepository;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.ModuleStructure.ModuleType;

import org.xvm.tool.LauncherOptions.BundlerOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for BundlerOptions, BundledFileRepository, and the Bundler tool.
 */
class BundlerTest {

    @TempDir
    Path tempDir;

    // ----- BundlerOptions tests ------------------------------------------------------------------

    @Test
    void testBundlerOptionsParseFromArgs() {
        var args = new String[]{"-o", "/tmp/bundle.xtc", "mod1.xtc", "mod2.xtc"};
        var opts = BundlerOptions.parse(args);

        assertEquals(Optional.of(new File("/tmp/bundle.xtc")), opts.getOutputFile());
        assertEquals(2, opts.getInputFiles().size());
        assertEquals(new File("mod1.xtc"), opts.getInputFiles().get(0));
        assertEquals(new File("mod2.xtc"), opts.getInputFiles().get(1));
        assertTrue(opts.getPrimaryModule().isEmpty());
    }

    @Test
    void testBundlerOptionsWithPrimary() {
        var args = new String[]{"-o", "out.xtc", "--primary", "my.module", "a.xtc", "b.xtc"};
        var opts = BundlerOptions.parse(args);

        assertEquals(Optional.of(new File("out.xtc")), opts.getOutputFile());
        assertEquals(Optional.of("my.module"), opts.getPrimaryModule());
        assertEquals(2, opts.getInputFiles().size());
    }

    @Test
    void testBundlerOptionsBuilder() {
        var opts = BundlerOptions.builder()
                .setOutputFile("/tmp/bundle.xtc")
                .addInputFile("mod1.xtc")
                .addInputFile("mod2.xtc")
                .setPrimaryModule("mod1.xtclang.org")
                .build();

        assertEquals(Optional.of(new File("/tmp/bundle.xtc")), opts.getOutputFile());
        assertEquals(Optional.of("mod1.xtclang.org"), opts.getPrimaryModule());
        assertEquals(2, opts.getInputFiles().size());
    }

    @Test
    void testBundlerOptionsRoundTrip() {
        var original = new String[]{"-o", "/tmp/out.xtc", "--primary", "my.module", "a.xtc", "b.xtc"};
        var opts = BundlerOptions.parse(original);
        var serialized = opts.toCommandLine();
        var reparsed = BundlerOptions.parse(serialized);

        assertEquals(opts.getOutputFile(), reparsed.getOutputFile());
        assertEquals(opts.getPrimaryModule(), reparsed.getPrimaryModule());
        assertEquals(opts.getInputFiles(), reparsed.getInputFiles());
    }

    @Test
    void testBundlerOptionsJsonRoundTrip() {
        var original = BundlerOptions.builder()
                .enableVerbose()
                .setOutputFile("/tmp/bundle.xtc")
                .addInputFile("mod1.xtc")
                .addModulePath("/lib")
                .build();

        var json = original.toJson();
        assertNotNull(json);
        assertTrue(json.contains("verbose"));

        var restored = BundlerOptions.fromJson(json);
        assertEquals(original.isVerbose(), restored.isVerbose());
        assertEquals(original.getOutputFile(), restored.getOutputFile());
        assertEquals(original.getInputFiles(), restored.getInputFiles());
        assertEquals(original.getModulePath(), restored.getModulePath());
    }

    @Test
    void testBundlerOptionsNoInputs() {
        var opts = BundlerOptions.parse(new String[]{"-o", "out.xtc"});
        assertTrue(opts.getInputFiles().isEmpty());
    }

    @Test
    void testBundlerOptionsUsageLine() {
        var opts = BundlerOptions.builder().build();
        var usage = opts.buildUsageLine("bundle");
        assertTrue(usage.contains("bundle"));
        assertTrue(usage.contains("output.xtc"));
    }

    @Test
    void testBundlerOptionsHelpText() {
        var opts = BundlerOptions.builder().build();
        var help = opts.getHelpText();
        assertNotNull(help);
        assertTrue(help.contains("-o"));
        assertTrue(help.contains("--primary"));
    }

    // ----- ModuleStructure.setModuleType tests ---------------------------------------------------

    @Test
    void testSetModuleType() {
        var struct = new FileStructure("test.xtclang.org");
        var module = struct.getModule();

        assertEquals(ModuleType.Primary, module.getModuleType());

        module.setModuleType(ModuleType.Embedded);
        assertEquals(ModuleType.Embedded, module.getModuleType());
        assertTrue(module.isEmbeddedModule());

        module.setModuleType(ModuleType.Primary);
        assertEquals(ModuleType.Primary, module.getModuleType());
        assertFalse(module.isEmbeddedModule());
    }

    // ----- BundledFileRepository tests -----------------------------------------------------------

    @Test
    void testBundledFileRepositorySingleModule() throws IOException {
        // Create a single-module .xtc file
        var struct = new FileStructure("single.xtclang.org");
        var file = tempDir.resolve("single.xtc").toFile();
        struct.writeTo(file);

        var repo = new BundledFileRepository(file);

        var names = repo.getModuleNames();
        assertEquals(1, names.size());
        assertTrue(names.contains("single.xtclang.org"));

        var module = repo.loadModule("single.xtclang.org");
        assertNotNull(module);
        assertEquals("single.xtclang.org", module.getIdentityConstant().getName());

        assertNull(repo.loadModule("nonexistent.xtclang.org"));
    }

    @Test
    void testBundledFileRepositoryMultiModule() throws IOException {
        // Create a multi-module .xtc file by merging modules
        var primaryStruct = new FileStructure("primary.xtclang.org");
        var primaryModule = primaryStruct.getModule();

        var secondaryStruct = new FileStructure("secondary.xtclang.org");
        var secondaryModule = secondaryStruct.getModule();

        // Create bundle: merge secondary into primary's FileStructure
        var bundleStruct = new FileStructure(primaryModule, false);
        bundleStruct.merge(secondaryModule, false, false);

        // Reclassify merged module
        for (ModuleStructure m : bundleStruct.children()) {
            if (!m.isFingerprint() && !m.getIdentityConstant().equals(bundleStruct.getModuleId())) {
                m.setModuleType(ModuleType.Embedded);
            }
        }

        var file = tempDir.resolve("bundle.xtc").toFile();
        bundleStruct.writeTo(file);

        // Now test the repository
        var repo = new BundledFileRepository(file);
        var names = repo.getModuleNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("primary.xtclang.org"));
        assertTrue(names.contains("secondary.xtclang.org"));

        var primary = repo.loadModule("primary.xtclang.org");
        assertNotNull(primary);
        assertEquals("primary.xtclang.org", primary.getIdentityConstant().getName());

        var secondary = repo.loadModule("secondary.xtclang.org");
        assertNotNull(secondary);
        assertEquals("secondary.xtclang.org", secondary.getIdentityConstant().getName());
    }

    @Test
    void testBundledFileRepositoryNonExistentFile() {
        var repo = new BundledFileRepository(new File("/nonexistent/path.xtc"));
        assertTrue(repo.getModuleNames().isEmpty());
        assertNull(repo.loadModule("anything"));
    }

    @Test
    void testBundledFileRepositoryReadOnly() {
        var repo = new BundledFileRepository(new File("dummy.xtc"));
        assertThrows(IOException.class, () -> repo.storeModule(null));
    }

    @Test
    void testBundledFileRepositoryVersions() throws IOException {
        var struct = new FileStructure("versioned.xtclang.org");
        var file = tempDir.resolve("versioned.xtc").toFile();
        struct.writeTo(file);

        var repo = new BundledFileRepository(file);
        var versions = repo.getAvailableVersions("versioned.xtclang.org");
        assertNotNull(versions);

        assertNull(repo.getAvailableVersions("nonexistent"));
    }

    @Test
    void testBundledFileRepositoryEqualsAndHashCode() {
        var repo1 = new BundledFileRepository(new File("test.xtc"));
        var repo2 = new BundledFileRepository(new File("test.xtc"));
        var repo3 = new BundledFileRepository(new File("other.xtc"));

        assertEquals(repo1, repo2);
        assertEquals(repo1.hashCode(), repo2.hashCode());
        assertFalse(repo1.equals(repo3));
    }

    @Test
    void testBundledFileRepositoryToString() {
        var repo = new BundledFileRepository(new File("test.xtc"));
        var str = repo.toString();
        assertTrue(str.contains("BundledFileRepository"));
        assertTrue(str.contains("test.xtc"));
    }

    @Test
    void testBundledFileRepositoryXtcExtensionHandling() throws IOException {
        // Create a file and verify extension handling
        var struct = new FileStructure("ext.xtclang.org");
        var file = tempDir.resolve("ext.xtc").toFile();
        struct.writeTo(file);

        // .xtc extension should work
        var repo1 = new BundledFileRepository(file);
        assertEquals(1, repo1.getModuleNames().size());

        // .x extension should get converted to .xtc
        var repoX = new BundledFileRepository(tempDir.resolve("ext.x").toFile());
        assertEquals(1, repoX.getModuleNames().size());

        // No extension should get .xtc appended
        var repoNoExt = new BundledFileRepository(tempDir.resolve("ext").toFile());
        // This points to a non-existent file (ext.xtc doesn't exist unless ext was the name)
        // But we already have ext.xtc, so:
        var repoFromName = new BundledFileRepository(new File(file.getParent(), "ext"));
        assertEquals(1, repoFromName.getModuleNames().size());
    }

    // ----- Bundler end-to-end tests --------------------------------------------------------------

    @Test
    void testBundlerEndToEnd() throws IOException {
        // Create two separate .xtc files
        var struct1 = new FileStructure("mod1.xtclang.org");
        var file1 = tempDir.resolve("mod1.xtc").toFile();
        struct1.writeTo(file1);

        var struct2 = new FileStructure("mod2.xtclang.org");
        var file2 = tempDir.resolve("mod2.xtc").toFile();
        struct2.writeTo(file2);

        var outputFile = tempDir.resolve("bundle.xtc").toFile();

        // Build bundler options
        var opts = BundlerOptions.builder()
                .setOutputFile(outputFile)
                .addInputFile(file1)
                .addInputFile(file2)
                .build();

        var console = new CaptureConsole();
        var bundler = new Bundler(opts, console, null);
        var result = bundler.run();

        assertEquals(0, result, "Bundler should succeed: " + console.getAllOutput());
        assertTrue(outputFile.exists(), "Output file should exist");

        // Verify the bundle contains both modules
        var repo = new BundledFileRepository(outputFile);
        var names = repo.getModuleNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("mod1.xtclang.org"));
        assertTrue(names.contains("mod2.xtclang.org"));
    }

    @Test
    void testBundlerWithPrimaryOverride() throws IOException {
        // Create two .xtc files
        var struct1 = new FileStructure("first.xtclang.org");
        var file1 = tempDir.resolve("first.xtc").toFile();
        struct1.writeTo(file1);

        var struct2 = new FileStructure("second.xtclang.org");
        var file2 = tempDir.resolve("second.xtc").toFile();
        struct2.writeTo(file2);

        var outputFile = tempDir.resolve("bundle.xtc").toFile();

        // Use second module as primary
        var opts = BundlerOptions.builder()
                .setOutputFile(outputFile)
                .setPrimaryModule("second.xtclang.org")
                .addInputFile(file1)
                .addInputFile(file2)
                .build();

        var console = new CaptureConsole();
        var bundler = new Bundler(opts, console, null);
        var result = bundler.run();

        assertEquals(0, result, "Bundler should succeed: " + console.getAllOutput());

        // Verify the primary module
        var bundleStruct = new FileStructure(outputFile);
        assertEquals("second.xtclang.org", bundleStruct.getModuleId().getName());
    }

    @Test
    void testBundlerThreeModules() throws IOException {
        // Create three .xtc files
        List<File> files = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            var struct = new FileStructure("mod" + i + ".xtclang.org");
            var file = tempDir.resolve("mod" + i + ".xtc").toFile();
            struct.writeTo(file);
            files.add(file);
        }

        var outputFile = tempDir.resolve("triple.xtc").toFile();

        var builder = BundlerOptions.builder().setOutputFile(outputFile);
        for (File f : files) {
            builder.addInputFile(f);
        }
        var opts = builder.build();

        var console = new CaptureConsole();
        var bundler = new Bundler(opts, console, null);
        var result = bundler.run();

        assertEquals(0, result, "Bundler should succeed: " + console.getAllOutput());

        var repo = new BundledFileRepository(outputFile);
        assertEquals(3, repo.getModuleNames().size());
        for (int i = 1; i <= 3; i++) {
            assertNotNull(repo.loadModule("mod" + i + ".xtclang.org"));
        }
    }

    @Test
    void testBundlerSingleModule() throws IOException {
        // Bundling a single module should just copy it
        var struct = new FileStructure("solo.xtclang.org");
        var file = tempDir.resolve("solo.xtc").toFile();
        struct.writeTo(file);

        var outputFile = tempDir.resolve("out.xtc").toFile();

        var opts = BundlerOptions.builder()
                .setOutputFile(outputFile)
                .addInputFile(file)
                .build();

        var console = new CaptureConsole();
        var bundler = new Bundler(opts, console, null);
        var result = bundler.run();

        assertEquals(0, result, "Bundler should succeed");

        var repo = new BundledFileRepository(outputFile);
        assertEquals(Set.of("solo.xtclang.org"), repo.getModuleNames());
    }

    @Test
    void testLauncherBundleCommandRegistered() {
        // Verify the bundle command is registered and parseable
        var console = new CaptureConsole();
        var result = Launcher.launch("bundle", new String[]{"--help"}, console, null);
        assertEquals(0, result);
        var output = console.getAllOutput();
        assertTrue(output.contains("bundle") || output.contains("Bundler") || output.contains("-o"),
                "Help output should contain bundle-related text: " + output);
    }

    // ----- configureLibraryRepo integration tests ------------------------------------------------

    @Test
    void testConfigureLibraryRepoWithBundledXtc() throws IOException {
        // Create three modules and bundle them into a single .xtc file
        var struct1 = new FileStructure("alpha.xtclang.org");
        var file1 = tempDir.resolve("alpha.xtc").toFile();
        struct1.writeTo(file1);

        var struct2 = new FileStructure("beta.xtclang.org");
        var file2 = tempDir.resolve("beta.xtc").toFile();
        struct2.writeTo(file2);

        var struct3 = new FileStructure("gamma.xtclang.org");
        var file3 = tempDir.resolve("gamma.xtc").toFile();
        struct3.writeTo(file3);

        // Bundle all three into one .xtc file
        var bundleFile = tempDir.resolve("libs.xtc").toFile();
        var opts = BundlerOptions.builder()
                .setOutputFile(bundleFile)
                .addInputFile(file1)
                .addInputFile(file2)
                .addInputFile(file3)
                .build();

        var console = new CaptureConsole();
        var bundler = new Bundler(opts, console, null);
        assertEquals(0, bundler.run(), "Bundle should succeed");

        // Use a concrete Launcher subclass to test configureLibraryRepo()
        // with the bundled .xtc file as the sole library path entry
        var testLauncher = new TestLauncher();
        var repo = testLauncher.configureLibraryRepo(List.of(bundleFile));

        // Verify that ALL three modules can be found through the repository
        var moduleNames = repo.getModuleNames();
        assertTrue(moduleNames.contains("alpha.xtclang.org"),
                "Repository should contain alpha module; found: " + moduleNames);
        assertTrue(moduleNames.contains("beta.xtclang.org"),
                "Repository should contain beta module; found: " + moduleNames);
        assertTrue(moduleNames.contains("gamma.xtclang.org"),
                "Repository should contain gamma module; found: " + moduleNames);

        // Verify modules can actually be loaded
        var alpha = repo.loadModule("alpha.xtclang.org");
        assertNotNull(alpha, "Should be able to load alpha module from bundle");
        assertEquals("alpha.xtclang.org", alpha.getIdentityConstant().getName());

        var beta = repo.loadModule("beta.xtclang.org");
        assertNotNull(beta, "Should be able to load beta module from bundle");

        var gamma = repo.loadModule("gamma.xtclang.org");
        assertNotNull(gamma, "Should be able to load gamma module from bundle");

        // Non-existent modules should return null
        assertNull(repo.loadModule("nonexistent.xtclang.org"));
    }

    @Test
    void testConfigureLibraryRepoWithSingleXtcFile() throws IOException {
        // Verify that BundledFileRepository works correctly for single-module files
        // (backwards-compatible with the old FileRepository behavior)
        var struct = new FileStructure("single.xtclang.org");
        var file = tempDir.resolve("single.xtc").toFile();
        struct.writeTo(file);

        var testLauncher = new TestLauncher();
        var repo = testLauncher.configureLibraryRepo(List.of(file));

        var module = repo.loadModule("single.xtclang.org");
        assertNotNull(module, "Should be able to load single module from .xtc file");
        assertEquals("single.xtclang.org", module.getIdentityConstant().getName());
    }

    @Test
    void testConfigureLibraryRepoMixedDirectoryAndBundle() throws IOException {
        // Create a bundled .xtc and a separate .xtc in a directory
        var bundledStruct = new FileStructure("bundled.xtclang.org");
        var bundleFile = tempDir.resolve("bundle.xtc").toFile();
        bundledStruct.writeTo(bundleFile);

        var dirPath = tempDir.resolve("libdir");
        dirPath.toFile().mkdirs();
        var dirStruct = new FileStructure("dirmod.xtclang.org");
        dirStruct.writeTo(dirPath.resolve("dirmod.xtc").toFile());

        var testLauncher = new TestLauncher();
        var repo = testLauncher.configureLibraryRepo(List.of(bundleFile, dirPath.toFile()));

        // Both modules should be accessible
        assertNotNull(repo.loadModule("bundled.xtclang.org"),
                "Should load module from bundled .xtc");
        assertNotNull(repo.loadModule("dirmod.xtclang.org"),
                "Should load module from directory");
    }


    // ----- test helpers --------------------------------------------------------------------------

    /**
     * Minimal Launcher subclass to expose configureLibraryRepo() for testing.
     */
    private static final class TestLauncher extends Launcher<BundlerOptions> {
        TestLauncher() {
            super(BundlerOptions.parse(new String[]{"-o", "dummy.xtc"}), new CaptureConsole(), null);
        }

        @Override
        protected void validateOptions() {
        }

        @Override
        protected int process() {
            return 0;
        }

        @Override
        public ModuleRepository configureLibraryRepo(List<File> path) {
            return super.configureLibraryRepo(path);
        }
    }

    /**
     * Console that captures output for verification.
     */
    private static final class CaptureConsole implements Console {
        private final List<String> lines = new ArrayList<>();

        @Override
        public String out(final Object o) {
            if (o != null) {
                lines.add(o.toString());
            }
            return "";
        }

        public String getAllOutput() {
            return String.join("\n", lines);
        }
    }
}

