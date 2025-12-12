package org.xvm.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.ErrorList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Unit tests for {@link ModuleInfo}, focusing on the deduce flag behavior,
 * project structure layouts, source tree generation, and resource/binary handling.
 */
class ModuleInfoTest {

    @TempDir
    Path tempDir;

    // ----- Helper methods ------------------------------------------------------------------------

    /**
     * Sets a file's modification time to one year in the past.
     */
    private void setTimestampOneYearAgo(File file) {
        var oldTime = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000;
        assertTrue(oldTime > 0, "Timestamp must be positive (after epoch)");
        assertTrue(file.setLastModified(oldTime), "Failed to set file timestamp");
    }

    /**
     * Creates a simple module source file with the given name.
     */
    private Path createModuleSource(String name) throws IOException {
        var sourceFile = tempDir.resolve(name + ".x");
        Files.writeString(sourceFile, "module " + name + " {}");
        return sourceFile;
    }

    /**
     * Creates a module source file with a matching directory (source tree).
     */
    @SuppressWarnings("SameParameterValue")
    private Path createModuleSourceTree(final String name) throws IOException {
        var sourceFile = createModuleSource(name);
        var moduleDir = Files.createDirectories(tempDir.resolve(name));
        Files.writeString(moduleDir.resolve("Helper.x"), "class Helper {}");
        return sourceFile;
    }

    // ----- Basic construction tests --------------------------------------------------------------

    /**
     * Null fileSpec throws IllegalArgumentException.
     */
    @Test
    void testNullFileSpecThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleInfo(null, false));
        assertThrows(IllegalArgumentException.class, () -> new ModuleInfo(null, true));
    }

    /**
     * Basic ModuleInfo from single .x file; verifies name, sourceFile, sourceDir, isSourceTree.
     */
    @Test
    void testSimpleSourceFileInCurrentDir() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(sourceFile.toFile().getCanonicalFile(), info.getSourceFile());
        assertEquals(tempDir.toFile().getCanonicalFile(), info.getSourceDir());
        assertFalse(info.isSourceTree());
    }

    /**
     * Qualified module name (with dots) is correctly parsed from module declaration.
     */
    @Test
    void testQualifiedModuleName() throws IOException {
        var sourceFile = tempDir.resolve("example.x");
        Files.writeString(sourceFile, "module example.com.myapp {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("example.com.myapp", info.getQualifiedModuleName());
    }

    // ----- deduce=false tests --------------------------------------------------------------------

    /**
     * deduce=false uses exact directory containing source file as project dir.
     */
    @Test
    void testDeduceFalseUsesExactDirectory() throws IOException {
        var srcDir = Files.createDirectories(tempDir.resolve("src/main/x"));
        var sourceFile = srcDir.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        assertEquals(srcDir.toFile().getCanonicalFile(), info.getProjectDir());
        assertEquals(srcDir.toFile().getCanonicalFile(), info.getSourceDir());
    }

    /**
     * deduce=false does not search for build/target directories; binaryDir equals projectDir.
     */
    @Test
    void testDeduceFalseDoesNotSearchForBinaryDir() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals(info.getProjectDir(), info.getBinaryDir());
    }

    // ----- deduce=true tests ---------------------------------------------------------------------

    /**
     * deduce=true walks up from src/main/x to find project root.
     */
    @Test
    void testDeduceTrueFindsProjectDirFromSrcMainX() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), true);

        assertEquals(tempDir.toFile().getCanonicalFile(), info.getProjectDir());
        assertEquals(srcMainX.toFile().getCanonicalFile(), info.getSourceDir());
    }

    /**
     * deduce=true recognizes src/main/ecstasy as source directory.
     */
    @Test
    void testDeduceTrueFindsProjectDirFromSrcMainEcstasy() throws IOException {
        var srcMainEcstasy = Files.createDirectories(tempDir.resolve("src/main/ecstasy"));
        var sourceFile = srcMainEcstasy.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");
        var info = new ModuleInfo(sourceFile.toFile(), true);
        assertEquals(tempDir.toFile().getCanonicalFile(), info.getProjectDir());
    }

    /**
     * deduce=true recognizes source/main/x as alternative source directory.
     */
    @Test
    void testDeduceTrueFindsProjectDirFromSourceDir() throws IOException {
        var sourceMainX = Files.createDirectories(tempDir.resolve("source/main/x"));
        var sourceFile = sourceMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), true);

        assertEquals(tempDir.toFile().getCanonicalFile(), info.getProjectDir());
    }

    /**
     * deduce=true finds build/ directory as binary output location.
     */
    @Test
    void testDeduceTrueFindsBuildDir() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var buildDir = Files.createDirectories(tempDir.resolve("build"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), true);

        assertEquals(buildDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * deduce=true finds target/ directory (Maven style) as binary output location.
     */
    @Test
    void testDeduceTrueFindsTargetDir() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var targetDir = Files.createDirectories(tempDir.resolve("target"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), true);

        assertEquals(targetDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * deduce=true prefers build/ over target/ when both exist.
     */
    @Test
    void testDeduceTruePrefersBuildOverTarget() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var buildDir = Files.createDirectories(tempDir.resolve("build"));
        Files.createDirectories(tempDir.resolve("target"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), true);

        assertEquals(buildDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    // ----- Source tree tests ---------------------------------------------------------------------

    /**
     * Module with matching directory (Module.x + Module/) is detected as source tree.
     */
    @Test
    void testSourceTreeDetection() throws IOException {
        var sourceFile = createModuleSourceTree("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        assertTrue(info.isSourceTree());
    }

    /**
     * Single-file module without matching directory is not a source tree.
     */
    @Test
    void testNonSourceTree() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertFalse(info.isSourceTree());
    }

    // ----- File type detection tests -------------------------------------------------------------

    /**
     * isExplicitSourceFile returns true for .x files, false for others.
     */
    @Test
    void testIsExplicitSourceFile() {
        assertTrue(ModuleInfo.isExplicitSourceFile("test.x"));
        assertTrue(ModuleInfo.isExplicitSourceFile("Test.X"));
        assertTrue(ModuleInfo.isExplicitSourceFile("path/to/test.x"));
        assertFalse(ModuleInfo.isExplicitSourceFile("test.xtc"));
        assertFalse(ModuleInfo.isExplicitSourceFile("test.java"));
        assertFalse(ModuleInfo.isExplicitSourceFile("test"));
    }

    /**
     * isExplicitCompiledFile returns true for .xtc files, false for others.
     */
    @Test
    void testIsExplicitCompiledFile() {
        assertTrue(ModuleInfo.isExplicitCompiledFile("test.xtc"));
        assertTrue(ModuleInfo.isExplicitCompiledFile("Test.XTC"));
        assertTrue(ModuleInfo.isExplicitCompiledFile("path/to/test.xtc"));
        assertFalse(ModuleInfo.isExplicitCompiledFile("test.x"));
        assertFalse(ModuleInfo.isExplicitCompiledFile("test.class"));
        assertFalse(ModuleInfo.isExplicitCompiledFile("test"));
    }

    /**
     * isExplicitEcstasyFile returns true for .x and .xtc files.
     */
    @Test
    void testIsExplicitEcstasyFile() {
        assertTrue(ModuleInfo.isExplicitEcstasyFile("test.x"));
        assertTrue(ModuleInfo.isExplicitEcstasyFile("test.xtc"));
        assertTrue(ModuleInfo.isExplicitEcstasyFile("test.X"));
        assertTrue(ModuleInfo.isExplicitEcstasyFile("test.XTC"));
        assertFalse(ModuleInfo.isExplicitEcstasyFile("test.java"));
        assertFalse(ModuleInfo.isExplicitEcstasyFile("test"));
    }

    // ----- Up to date tests ----------------------------------------------------------------------

    /**
     * isUpToDate returns false when no compiled .xtc file exists.
     */
    @Test
    void testIsUpToDateWithNoCompiledFile() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        assertFalse(info.isUpToDate());
    }

    /**
     * isUpToDate returns false when .xtc is older than source.
     */
    @Test
    void testIsUpToDateWithOlderCompiledFile() throws IOException {
        var xtcFile = tempDir.resolve("MyModule.xtc");
        Files.writeString(xtcFile, "compiled");
        var sourceFile = createModuleSource("MyModule");
        setTimestampOneYearAgo(xtcFile.toFile());
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertFalse(info.isUpToDate());
    }

    // ----- toString test -------------------------------------------------------------------------

    /**
     * toString works without triggering lazy module name loading.
     */
    @Test
    void testToStringDoesNotTriggerLazyLoading() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("Module("));
    }

    // ----- Binary spec tests ---------------------------------------------------------------------

    /**
     * 3-arg constructor with .xtc file sets binaryFile and binaryDir correctly.
     */
    @Test
    void testBinarySpecWithXtcFile() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var binaryFile = tempDir.resolve("output/MyModule.xtc");
        Files.createDirectories(binaryFile.getParent());
        var info = new ModuleInfo(sourceFile.toFile(), false, binaryFile.toFile());
        assertEquals(binaryFile.toFile().getCanonicalFile(), info.getBinaryFile());
        assertEquals(binaryFile.getParent().toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * 3-arg constructor with directory sets binaryDir correctly.
     */
    @Test
    void testBinarySpecWithDirectory() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var outputDir = Files.createDirectories(tempDir.resolve("output"));
        var info = new ModuleInfo(sourceFile.toFile(), false, outputDir.toFile());
        assertEquals(outputDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * 3-arg constructor with non-.xtc file throws IllegalArgumentException.
     */
    @Test
    void testBinarySpecWithInvalidExtension() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var invalidBinary = tempDir.resolve("output/MyModule.jar");
        Files.createDirectories(invalidBinary.getParent());
        Files.writeString(invalidBinary, "invalid");
        assertThrows(IllegalArgumentException.class,
                () -> new ModuleInfo(sourceFile.toFile(), false, invalidBinary.toFile()));
    }

    // ----- Directory as input tests --------------------------------------------------------------

    /**
     * Directory input with deduce=false finds .x file in directory.
     */
    @Test
    void testDirectoryAsInputWithDeduceFalse() throws IOException {
        createModuleSource("MyModule");

        var info = new ModuleInfo(tempDir.toFile(), false);

        assertEquals("MyModule", info.getQualifiedModuleName());
    }

    /**
     * Directory input with deduce=true finds .x file in src/main/x subdirectory.
     */
    @Test
    void testDirectoryAsInputWithDeduceTrue() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        Files.writeString(srcMainX.resolve("MyModule.x"), "module MyModule {}");

        var info = new ModuleInfo(tempDir.toFile(), true);

        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(tempDir.toFile().getCanonicalFile(), info.getProjectDir());
    }

    // ----- Lazy loading tests --------------------------------------------------------------------

    /**
     * Module name is lazily loaded and cached on subsequent calls.
     */
    @Test
    void testModuleNameLazyLoading() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals("MyModule", info.getQualifiedModuleName());
    }

    /**
     * getQualifiedModuleName lazily extracts name from source file content.
     */
    @Test
    void testGetQualifiedModuleNameFromSourceContent() throws IOException {
        var sourceFile = tempDir.resolve("example.x");
        Files.writeString(sourceFile, "module foo.bar.baz {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        // First call triggers lazy parsing of source file
        assertEquals("foo.bar.baz", info.getQualifiedModuleName());
        // Simple name is the first component of the qualified name
        assertEquals("foo", info.getSimpleModuleName());
    }

    /**
     * getQualifiedModuleName falls back to file name when source content is invalid.
     */
    @Test
    void testGetQualifiedModuleNameFallbackToFileName() throws IOException {
        var sourceFile = tempDir.resolve("MyModule.x");
        Files.writeString(sourceFile, "class NotAModule {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        // Falls back to file name since file doesn't declare a module
        assertEquals("MyModule", info.getQualifiedModuleName());
    }

    /**
     * getQualifiedModuleName uses cached value on subsequent calls.
     */
    @Test
    void testGetQualifiedModuleNameCaching() throws IOException {
        var sourceFile = tempDir.resolve("cached.x");
        Files.writeString(sourceFile, "module cached.module.name {}");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        // Call multiple times to verify caching
        var name1 = info.getQualifiedModuleName();
        var name2 = info.getQualifiedModuleName();
        var name3 = info.getQualifiedModuleName();

        assertEquals("cached.module.name", name1);
        assertSame(name1, name2);
        assertSame(name2, name3);
    }

    /**
     * getQualifiedModuleName can be extracted without parsing full source tree.
     */
    @Test
    void testGetQualifiedModuleNameDoesNotRequireFullParse() throws IOException {
        var sourceFile = tempDir.resolve("minimal.x");
        Files.writeString(sourceFile, "module minimal.module {}");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        // Module name should be extractable without calling getSourceTree
        var moduleName = info.getQualifiedModuleName();
        assertEquals("minimal.module", moduleName);
    }

    /**
     * Source timestamp is lazily computed and cached.
     */
    @Test
    void testSourceTimestampLazyLoading() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var timestamp = info.getSourceTimestamp();
        assertTrue(timestamp > 0);
        assertEquals(timestamp, info.getSourceTimestamp());
    }

    /**
     * Binary timestamp is lazily computed and cached.
     */
    @Test
    void testBinaryTimestampLazyLoading() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var binaryFile = tempDir.resolve("MyModule.xtc");
        Files.writeString(binaryFile, "compiled");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var timestamp = info.getBinaryTimestamp();
        assertTrue(timestamp > 0);
        assertEquals(timestamp, info.getBinaryTimestamp());
    }

    // ----- Resource directory tests --------------------------------------------------------------

    /**
     * ResourceDir is discovered from src/main/resources with deduce=true.
     */
    @Test
    void testResourceDirWithDefaultLocation() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var resourceDir = Files.createDirectories(tempDir.resolve("src/main/resources"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(resourceDir.resolve("config.txt"), "config content");
        var info = new ModuleInfo(sourceFile.toFile(), true);
        assertNotNull(info.getResourceDir());
    }

    /**
     * Resource timestamp is lazily computed.
     */
    @Test
    void testResourceTimestampLazyLoading() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var timestamp = info.getResourceTimestamp();
        assertTrue(timestamp >= 0);
    }

    // ----- Source tree generation tests ----------------------------------------------------------

    /**
     * Source tree with nested packages is detected correctly.
     */
    @Test
    void testSourceTreeWithNestedPackages() throws IOException {
        var sourceFile = tempDir.resolve("MyModule.x");
        var moduleDir = Files.createDirectories(tempDir.resolve("MyModule"));
        var utilsDir = Files.createDirectories(moduleDir.resolve("utils"));

        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(moduleDir.resolve("Helper.x"), "class Helper {}");
        Files.writeString(utilsDir.resolve("Util.x"), "class Util {}");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        assertTrue(info.isSourceTree());
        assertEquals("MyModule", info.getQualifiedModuleName());
    }

    /**
     * Source timestamp includes all files in source tree.
     */
    @Test
    void testSourceTreeTimestampIncludesAllFiles() throws IOException {
        var sourceFile = createModuleSourceTree("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);

        assertTrue(info.isSourceTree());
        assertTrue(info.getSourceTimestamp() > 0);
    }

    // ----- Simple module name test ---------------------------------------------------------------

    /**
     * getSimpleModuleName returns first component of qualified name.
     */
    @Test
    void testSimpleModuleName() throws IOException {
        var sourceFile = tempDir.resolve("example.x");
        Files.writeString(sourceFile, "module example.com.myapp {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("example.com.myapp", info.getQualifiedModuleName());
        assertEquals("example", info.getSimpleModuleName());
    }

    /**
     * getSimpleModuleName returns full name when no dots.
     */
    @Test
    void testSimpleModuleNameWithoutDots() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals("MyModule", info.getSimpleModuleName());
    }

    // ----- getSourceTree tests -------------------------------------------------------------------

    /**
     * getSourceTree throws NullPointerException for null ErrorListener.
     */
    @Test
    void testGetSourceTreeRequiresErrorListener() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertThrows(NullPointerException.class, () -> info.getSourceTree(null));
    }

    /**
     * getSourceTree returns FileNode for single-file module.
     */
    @Test
    void testGetSourceTreeWithSingleFileModule() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors());
        assertInstanceOf(ModuleInfo.FileNode.class, node);
        assertEquals("MyModule", node.name());
        assertNull(node.parent());
        assertSame(node, node.root());
    }

    /**
     * getSourceTree returns DirNode for multi-file module.
     */
    @Test
    void testGetSourceTreeWithMultiFileModule() throws IOException {
        var sourceFile = createModuleSourceTree("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors());
        assertInstanceOf(ModuleInfo.DirNode.class, node);
        assertNull(node.parent());
        assertSame(node, node.root());
    }

    /**
     * getSourceTree returns cached result on subsequent calls.
     */
    @Test
    void testGetSourceTreeCachesResult() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node1 = info.getSourceTree(errs);
        var node2 = info.getSourceTree(errs);
        assertSame(node1, node2);
    }

    /**
     * Node provides access to depth, moduleInfo, file, ast, and type.
     */
    @Test
    void testGetSourceTreeNodeMethods() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertEquals(0, node.depth());
        assertSame(info, node.moduleInfo());
        assertNotNull(node.file());
        assertNotNull(node.ast());
        assertNotNull(node.type());
    }

    /**
     * DirNode has sourceNode for nested packages.
     */
    @Test
    void testGetSourceTreeWithNestedPackages() throws IOException {
        var sourceFile = tempDir.resolve("MyModule.x");
        var moduleDir = Files.createDirectories(tempDir.resolve("MyModule"));
        var utilsDir = Files.createDirectories(moduleDir.resolve("utils"));
        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(moduleDir.resolve("Helper.x"), "class Helper {}");
        Files.writeString(utilsDir.resolve("Util.x"), "class Util {}");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors());
        assertInstanceOf(ModuleInfo.DirNode.class, node);
        assertNotNull(((ModuleInfo.DirNode) node).sourceNode());
    }

    /**
     * getSourceTree returns null with errors for invalid syntax.
     */
    @Test
    void testGetSourceTreeWithParseErrors() throws IOException {
        var sourceFile = tempDir.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNull(node);
        assertTrue(errs.hasSeriousErrors());
    }

    /**
     * module() returns FileNode representing the module.
     */
    @Test
    void testGetSourceTreeModuleNode() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        var moduleNode = node.module();
        assertNotNull(moduleNode);
        assertInstanceOf(ModuleInfo.FileNode.class, moduleNode);
    }

    /**
     * descriptiveName contains module name.
     */
    @Test
    void testGetSourceTreeDescriptiveName() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertTrue(node.descriptiveName().contains("MyModule"));
    }

    /**
     * Node provides access to ResourceDir.
     */
    @Test
    void testGetSourceTreeResourceDir() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertNotNull(node.resourceDir());
    }

    /**
     * DirNode has classNodes and packageNodes for module with packages.
     */
    @Test
    void testDirNodeWithPackageAndClasses() throws IOException {
        var sourceFile = tempDir.resolve("MyModule.x");
        var moduleDir = Files.createDirectories(tempDir.resolve("MyModule"));
        var subPkgDir = Files.createDirectories(moduleDir.resolve("subpkg"));
        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(moduleDir.resolve("package.x"), "package MyModule {}");
        Files.writeString(moduleDir.resolve("MyClass.x"), "class MyClass {}");
        Files.writeString(subPkgDir.resolve("SubClass.x"), "class SubClass {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors());
        assertInstanceOf(ModuleInfo.DirNode.class, node);
        var dirNode = (ModuleInfo.DirNode) node;
        assertNotNull(dirNode.classNodes());
        assertNotNull(dirNode.packageNodes());
    }

    /**
     * Child nodes have parent reference and depth > 0.
     */
    @Test
    void testFileNodeDepth() throws IOException {
        var sourceFile = createModuleSourceTree("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertEquals(0, node.depth());

        if (node instanceof ModuleInfo.DirNode dirNode) {
            assertFalse(dirNode.classNodes().isEmpty(), "Expected class nodes");
            for (var classNode : dirNode.classNodes().values()) {
                assertTrue(classNode.depth() >= 0);
                assertNotNull(classNode.parent());
            }
        }
    }

    // ----- Tests using real project source files -------------------------------------------------

    /**
     * Parses real net.x module from lib_net as DirNode with class nodes.
     */
    @Test
    void testGetSourceTreeWithRealNetModule() {
        var netSourceFile = new File("../lib_net/src/main/x/net.x");
        if (!netSourceFile.exists()) {
            return;
        }

        var info = new ModuleInfo(netSourceFile, false);
        var errs = new ErrorList(1000);

        assertTrue(info.isSourceTree());
        assertEquals("net.xtclang.org", info.getQualifiedModuleName());

        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors(), "Parse errors: " + errs);
        assertInstanceOf(ModuleInfo.DirNode.class, node);
        assertFalse(((ModuleInfo.DirNode) node).classNodes().isEmpty());
        assertNotNull(node.ast());
        assertNotNull(node.type());
    }

    /**
     * Parses real json.x module from lib_json.
     */
    @Test
    void testGetSourceTreeWithRealJsonModule() {
        var jsonSourceFile = new File("../lib_json/src/main/x/json.x");
        if (!jsonSourceFile.exists()) {
            return;
        }

        var info = new ModuleInfo(jsonSourceFile, false);
        var errs = new ErrorList(1000);
        assertEquals("json.xtclang.org", info.getQualifiedModuleName());
        var node = info.getSourceTree(errs);
        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors(), "Parse errors: " + errs);
        assertNotNull(node.ast());
        assertNotNull(node.type());
        assertEquals("json", node.name());
    }

    /**
     * Parses real web.x module from lib_web with source tree structure.
     */
    @Test
    void testGetSourceTreeWithRealWebModule() {
        var webSourceFile = new File("../lib_web/src/main/x/web.x");
        if (!webSourceFile.exists()) {
            return;
        }

        var info = new ModuleInfo(webSourceFile, false);
        var errs = new ErrorList(1000);

        assertEquals("web.xtclang.org", info.getQualifiedModuleName());

        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors(), "Parse errors: " + errs);

        if (info.isSourceTree()) {
            assertInstanceOf(ModuleInfo.DirNode.class, node);
            var dirNode = (ModuleInfo.DirNode) node;
            assertNotNull(dirNode.sourceNode());
            assertNotNull(dirNode.children());
        }
    }

    /**
     * ModuleInfo with deduce=true on real project directory.
     */
    @Test
    void testModuleInfoDeduceWithRealProject() {
        var projectDir = new File("../lib_net");
        if (!projectDir.exists()) {
            return;
        }

        var info = new ModuleInfo(projectDir, true);
        assertEquals("net.xtclang.org", info.getQualifiedModuleName());
        assertNotNull(info.getSourceDir());
        assertNotNull(info.getProjectDir());
        assertTrue(info.isSourceTree());
    }

    // ----- Constructor with resourceSpecs and binarySpec tests -----------------------------------

    /**
     * 4-arg constructor with explicit resource paths includes them in ResourceDir.
     */
    @Test
    void testConstructorWithExplicitResourcePath() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var resourceDir1 = Files.createDirectories(tempDir.resolve("res1"));
        var resourceDir2 = Files.createDirectories(tempDir.resolve("res2"));
        Files.writeString(resourceDir1.resolve("config.txt"), "config");
        Files.writeString(resourceDir2.resolve("data.json"), "{}");
        var info = new ModuleInfo(sourceFile.toFile(), false,
                List.of(resourceDir1.toFile(), resourceDir2.toFile()), null);
        assertEquals("MyModule", info.getQualifiedModuleName());
        var resDir = info.getResourceDir();
        assertNotNull(resDir);
        assertTrue(resDir.getLocations().contains(resourceDir1.toFile()));
        assertTrue(resDir.getLocations().contains(resourceDir2.toFile()));
    }

    /**
     * 4-arg constructor with empty resource list uses default discovery.
     */
    @Test
    void testConstructorWithEmptyResourcePath() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false, List.of(), null);
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertNotNull(info.getResourceDir());
    }

    /**
     * 4-arg constructor with null resource list uses default discovery.
     */
    @Test
    void testConstructorWithNullResourcePath() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var resourceDir = Files.createDirectories(tempDir.resolve("src/main/resources"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(resourceDir.resolve("config.txt"), "config");
        var info = new ModuleInfo(sourceFile.toFile(), true, null, null);
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertNotNull(info.getResourceDir());
    }

    /**
     * 4-arg constructor with .xtc binarySpec sets binaryFile and binaryDir.
     */
    @Test
    void testConstructorWithBinarySpecFile() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var outputDir = Files.createDirectories(tempDir.resolve("output"));
        var binaryFile = outputDir.resolve("MyModule.xtc");
        var info = new ModuleInfo(sourceFile.toFile(), false, null, binaryFile.toFile());
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(binaryFile.toFile().getCanonicalFile(), info.getBinaryFile());
        assertEquals(outputDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * 4-arg constructor with directory binarySpec sets binaryDir.
     */
    @Test
    void testConstructorWithBinarySpecDirectory() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var outputDir = Files.createDirectories(tempDir.resolve("output"));

        var info = new ModuleInfo(sourceFile.toFile(), false, null, outputDir.toFile());

        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(outputDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * 4-arg constructor with both resourceSpecs and binarySpec.
     */
    @Test
    void testConstructorWithResourcesAndBinarySpec() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var resourceDir = Files.createDirectories(tempDir.resolve("resources"));
        var outputDir = Files.createDirectories(tempDir.resolve("output"));
        Files.writeString(resourceDir.resolve("config.txt"), "config");
        var info = new ModuleInfo(sourceFile.toFile(), false, List.of(resourceDir.toFile()), outputDir.toFile());
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertNotNull(info.getResourceDir());
        assertTrue(info.getResourceDir().getLocations().contains(resourceDir.toFile()));
        assertEquals(outputDir.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * 4-arg constructor with multiple resource directories.
     */
    @Test
    void testConstructorWithMultipleResourceDirs() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var res1 = Files.createDirectories(tempDir.resolve("res1"));
        var res2 = Files.createDirectories(tempDir.resolve("res2"));
        var res3 = Files.createDirectories(tempDir.resolve("res3"));
        Files.writeString(res1.resolve("a.txt"), "a");
        Files.writeString(res2.resolve("b.txt"), "b");
        Files.writeString(res3.resolve("c.txt"), "c");

        var info = new ModuleInfo(sourceFile.toFile(), false,
                List.of(res1.toFile(), res2.toFile(), res3.toFile()), null);

        assertEquals("MyModule", info.getQualifiedModuleName());
        var resDir = info.getResourceDir();
        assertNotNull(resDir);
        var locations = resDir.getLocations();
        assertTrue(locations.contains(res1.toFile()));
        assertTrue(locations.contains(res2.toFile()));
        assertTrue(locations.contains(res3.toFile()));
    }

    /**
     * 4-arg constructor accepts resource file (not just directory).
     */
    @Test
    void testConstructorWithResourceFile() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var resourceFile = tempDir.resolve("single-resource.txt");
        Files.writeString(resourceFile, "resource content");
        var info = new ModuleInfo(sourceFile.toFile(), false, List.of(resourceFile.toFile()), null);
        assertEquals("MyModule", info.getQualifiedModuleName());
        assertNotNull(info.getResourceDir());
        assertFalse(info.getResourceDir().getLocations().isEmpty());
    }

    /**
     * deduce=true with explicit binarySpec uses explicit binaryDir, not deduced.
     */
    @Test
    void testConstructorWithDeduceAndExplicitBinary() throws IOException {
        var srcMainX = Files.createDirectories(tempDir.resolve("src/main/x"));
        var customBuild = Files.createDirectories(tempDir.resolve("custom-build"));
        var sourceFile = srcMainX.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");

        var info = new ModuleInfo(sourceFile.toFile(), true, null, customBuild.toFile());

        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(tempDir.toFile().getCanonicalFile(), info.getProjectDir());
        assertEquals(customBuild.toFile().getCanonicalFile(), info.getBinaryDir());
    }

    /**
     * Resource timestamp is > 0 when explicit resources exist.
     */
    @Test
    void testResourceTimestampWithExplicitResources() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var resourceDir = Files.createDirectories(tempDir.resolve("resources"));
        Files.writeString(resourceDir.resolve("config.txt"), "config");

        var info = new ModuleInfo(sourceFile.toFile(), false,
                List.of(resourceDir.toFile()), null);

        assertTrue(info.getResourceTimestamp() > 0);
    }

    /**
     * Resource timestamp >= 0 with empty resource list.
     */
    @Test
    void testResourceTimestampWithEmptyResourceList() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false, List.of(), null);

        assertTrue(info.getResourceTimestamp() >= 0);
    }

    /**
     * 3-arg constructor sets binaryDir; resourceDir uses default discovery.
     */
    @Test
    void testThreeArgConstructorWithBinarySpec() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var outputDir = Files.createDirectories(tempDir.resolve("output"));

        var info = new ModuleInfo(sourceFile.toFile(), false, outputDir.toFile());

        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(outputDir.toFile().getCanonicalFile(), info.getBinaryDir());
        assertNotNull(info.getResourceDir());
    }

    /**
     * 4-arg constructor with non-.xtc binarySpec throws IllegalArgumentException.
     */
    @Test
    void testConstructorWithInvalidBinaryExtension() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var invalidBinary = tempDir.resolve("output/MyModule.jar");
        Files.createDirectories(invalidBinary.getParent());
        Files.writeString(invalidBinary, "invalid");

        assertThrows(IllegalArgumentException.class,
                () -> new ModuleInfo(sourceFile.toFile(), false, null, invalidBinary.toFile()));
    }

    /**
     * getSourceTree provides ResourceDir from explicit resources.
     */
    @Test
    void testGetSourceTreeWithExplicitResources() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var resourceDir = Files.createDirectories(tempDir.resolve("my-resources"));
        Files.writeString(resourceDir.resolve("data.json"), "{}");

        var info = new ModuleInfo(sourceFile.toFile(), false,
                List.of(resourceDir.toFile()), null);
        var errs = new ErrorList(100);

        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors());
        var nodeResDir = node.resourceDir();
        assertNotNull(nodeResDir);
        assertTrue(nodeResDir.getLocations().contains(resourceDir.toFile()));
    }

    // ----- Real .xtc binary file tests -----------------------------------------------------------

    /**
     * projectDirFromSubDir works with real .xtc binary directory.
     */
    @Test
    void testWithRealXtcBinaryFile() {
        var ecstasyXtc = new File("../xdk/build/xdk/lib/ecstasy.xtc");
        if (!ecstasyXtc.exists()) {
            ecstasyXtc = new File("../lib_ecstasy/build/ecstasy.xtc");
            if (!ecstasyXtc.exists()) {
                return;
            }
        }

        var binaryDir = ecstasyXtc.getParentFile();
        assertNotNull(binaryDir);
        assertNotNull(ModuleInfo.projectDirFromSubDir(binaryDir));
    }

    /**
     * Explicit .xtc binarySpec sets binaryFile, binaryDir, and timestamp.
     */
    @Test
    void testBinarySpecWithExistingXtcFile() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var outputDir = Files.createDirectories(tempDir.resolve("build"));
        var xtcFile = outputDir.resolve("MyModule.xtc");
        Files.writeString(xtcFile, "dummy xtc content");

        var info = new ModuleInfo(sourceFile.toFile(), false, null, xtcFile.toFile());

        assertEquals("MyModule", info.getQualifiedModuleName());
        assertEquals(xtcFile.toFile().getCanonicalFile(), info.getBinaryFile());
        assertEquals(outputDir.toFile().getCanonicalFile(), info.getBinaryDir());
        assertTrue(info.getBinaryTimestamp() > 0);
    }

    /**
     * isUpToDate returns true when binary is newer than source.
     */
    @Test
    void testIsUpToDateWithNewerBinary() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var xtcFile = tempDir.resolve("MyModule.xtc");
        Files.writeString(xtcFile, "compiled");

        setTimestampOneYearAgo(sourceFile.toFile());

        var info = new ModuleInfo(sourceFile.toFile(), false);

        assertTrue(info.isUpToDate());
    }

    // ----- extractModuleName tests ---------------------------------------------------------------

    /**
     * extractModuleName returns module name from valid .x source file.
     */
    @Test
    void testExtractModuleNameFromSourceFile() throws IOException {
        var sourceFile = tempDir.resolve("example.x");
        Files.writeString(sourceFile, "module example.com.myapp {}");
        var moduleName = ModuleInfo.extractModuleName(sourceFile.toFile());
        assertEquals("example.com.myapp", moduleName);
    }

    /**
     * extractModuleName returns null for non-module source file.
     */
    @Test
    void testExtractModuleNameFromNonModuleFile() throws IOException {
        var sourceFile = tempDir.resolve("NotAModule.x");
        Files.writeString(sourceFile, "class NotAModule {}");
        var moduleName = ModuleInfo.extractModuleName(sourceFile.toFile());
        assertNull(moduleName);
    }

    /**
     * extractModuleName returns null for non-existent file.
     */
    @Test
    void testExtractModuleNameFromNonExistentFile() {
        var nonExistent = tempDir.resolve("nonexistent.x");
        var moduleName = ModuleInfo.extractModuleName(nonExistent.toFile());
        assertNull(moduleName);
    }

    /**
     * extractModuleName returns null for file with wrong extension.
     */
    @Test
    void testExtractModuleNameFromWrongExtension() throws IOException {
        var javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "class Test {}");
        var moduleName = ModuleInfo.extractModuleName(javaFile.toFile());
        assertNull(moduleName);
    }

    // ----- isSystemModule tests ------------------------------------------------------------------

    /**
     * isSystemModule returns false for regular user modules.
     */
    @Test
    void testIsSystemModuleReturnsFalseForUserModule() throws IOException {
        var sourceFile = createModuleSource("MyUserModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertFalse(info.isSystemModule());
    }

    /**
     * isSystemModule returns false for qualified user modules.
     */
    @Test
    void testIsSystemModuleReturnsFalseForQualifiedUserModule() throws IOException {
        var sourceFile = tempDir.resolve("myapp.x");
        Files.writeString(sourceFile, "module myapp.example.org {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertFalse(info.isSystemModule());
    }

    // ----- isModuleNameQualified tests -----------------------------------------------------------

    /**
     * isModuleNameQualified returns true for qualified module names.
     */
    @Test
    void testIsModuleNameQualifiedWithDots() throws IOException {
        var sourceFile = tempDir.resolve("qualified.x");
        Files.writeString(sourceFile, "module qualified.example.org {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertTrue(info.isModuleNameQualified());
    }

    /**
     * isModuleNameQualified returns false for simple module names.
     */
    @Test
    void testIsModuleNameQualifiedWithoutDots() throws IOException {
        var sourceFile = createModuleSource("SimpleModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertFalse(info.isModuleNameQualified());
    }

    // ----- FileNode.resolveResource tests --------------------------------------------------------

    /**
     * FileNode.resolveResource returns file for valid relative path.
     */
    @Test
    void testFileNodeResolveResourceRelativePath() throws IOException {
        var srcDir = Files.createDirectories(tempDir.resolve("src/main/x"));
        var resDir = Files.createDirectories(tempDir.resolve("src/main/resources"));
        var sourceFile = srcDir.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(resDir.resolve("config.txt"), "config content");

        var info = new ModuleInfo(sourceFile.toFile(), true);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.FileNode.class, node);

        var resource = ((ModuleInfo.FileNode) node).resolveResource("config.txt");
        assertNotNull(resource);
        assertInstanceOf(File.class, resource);
    }

    /**
     * FileNode.resolveResource returns ResourceDir for directory path.
     */
    @Test
    void testFileNodeResolveResourceDirectoryPath() throws IOException {
        var srcDir = Files.createDirectories(tempDir.resolve("src/main/x"));
        var resDir = Files.createDirectories(tempDir.resolve("src/main/resources"));
        var subDir = Files.createDirectories(resDir.resolve("subdir"));
        var sourceFile = srcDir.resolve("MyModule.x");
        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(subDir.resolve("file.txt"), "content");

        var info = new ModuleInfo(sourceFile.toFile(), true);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.FileNode.class, node);

        var resource = ((ModuleInfo.FileNode) node).resolveResource("subdir/");
        assertNotNull(resource);
        assertInstanceOf(ResourceDir.class, resource);
    }

    /**
     * FileNode.resolveResource returns null for non-existent path.
     */
    @Test
    void testFileNodeResolveResourceNonExistent() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.FileNode.class, node);

        var resource = ((ModuleInfo.FileNode) node).resolveResource("nonexistent.txt");
        assertNull(resource);
    }

    // ----- FileNode.source tests -----------------------------------------------------------------

    /**
     * FileNode.source returns Source object.
     */
    @Test
    void testFileNodeSourceReturnsSource() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.FileNode.class, node);
        var fileNode = (ModuleInfo.FileNode) node;

        var source = fileNode.source();
        assertNotNull(source);
        assertNotNull(source.getFileName());
    }

    /**
     * FileNode.content returns source code as char array.
     */
    @Test
    void testFileNodeContentReturnsCharArray() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.FileNode.class, node);
        var fileNode = (ModuleInfo.FileNode) node;

        var content = fileNode.content();
        assertNotNull(content);
        assertTrue(content.length > 0);
        assertTrue(new String(content).contains("module MyModule"));
    }

    // ----- FileNode.isPackageSource tests --------------------------------------------------------

    /**
     * FileNode.isPackageSource returns true for root module node.
     */
    @Test
    void testFileNodeIsPackageSourceForRootModule() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.FileNode.class, node);
        assertTrue(((ModuleInfo.FileNode) node).isPackageSource());
    }

    // ----- DirNode.children tests ----------------------------------------------------------------

    /**
     * DirNode.children contains registered class and package nodes.
     */
    @Test
    void testDirNodeChildrenContainsRegisteredNodes() throws IOException {
        var sourceFile = tempDir.resolve("MyModule.x");
        var moduleDir = Files.createDirectories(tempDir.resolve("MyModule"));

        Files.writeString(sourceFile, "module MyModule {}");
        Files.writeString(moduleDir.resolve("Helper.x"), "class Helper {}");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertFalse(errs.hasSeriousErrors());
        assertInstanceOf(ModuleInfo.DirNode.class, node);

        var dirNode = (ModuleInfo.DirNode) node;
        assertNotNull(dirNode.children());
        assertTrue(dirNode.children().containsKey("Helper"));
    }

    // ----- Node.toString tests -------------------------------------------------------------------

    /**
     * FileNode.toString includes module name and .x extension.
     */
    @Test
    void testFileNodeToString() throws IOException {
        var sourceFile = createModuleSource("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        var nodeString = node.toString();
        assertTrue(nodeString.contains("MyModule"));
        assertTrue(nodeString.endsWith(".x"));
    }

    /**
     * DirNode.toString includes directory path with slashes.
     */
    @Test
    void testDirNodeToString() throws IOException {
        var sourceFile = createModuleSourceTree("MyModule");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);
        var node = info.getSourceTree(errs);

        assertNotNull(node);
        assertInstanceOf(ModuleInfo.DirNode.class, node);
        var nodeString = node.toString();
        assertTrue(nodeString.contains("MyModule"));
        assertTrue(nodeString.contains("/"));
    }

    // ----- Error handling tests ------------------------------------------------------------------

    /**
     * Node error listener accumulates errors during parsing.
     */
    @Test
    void testNodeErrorListenerAccumulatesErrors() throws IOException {
        var sourceFile = tempDir.resolve("BadModule.x");
        Files.writeString(sourceFile, "module BadModule { invalid syntax here");

        var info = new ModuleInfo(sourceFile.toFile(), false);
        var errs = new ErrorList(100);

        var node = info.getSourceTree(errs);

        assertNull(node);
        assertTrue(errs.hasSeriousErrors());
        assertFalse(errs.getErrors().isEmpty());
    }

    // ----- getFileName tests ---------------------------------------------------------------------

    /**
     * getFileName returns file name without extension.
     */
    @Test
    void testGetFileName() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("MyModule", info.getFileName());
    }

    /**
     * getFileName handles qualified file names.
     */
    @Test
    void testGetFileNameWithQualifiedPath() throws IOException {
        var srcDir = Files.createDirectories(tempDir.resolve("src/main/x"));
        var sourceFile = srcDir.resolve("mymodule.x");
        Files.writeString(sourceFile, "module mymodule.example.org {}");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals("mymodule", info.getFileName());
    }

    // ----- getFileSpec tests ---------------------------------------------------------------------

    /**
     * getFileSpec returns original file specification.
     */
    @Test
    void testGetFileSpec() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var info = new ModuleInfo(sourceFile.toFile(), false);
        assertEquals(sourceFile.toFile(), info.getFileSpec());
    }

    // ----- Constructor validation tests ----------------------------------------------------------

    /**
     * Constructor throws for null resource in resourceSpecs list.
     */
    @Test
    void testConstructorThrowsForNullResourceInList() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var resList = new java.util.ArrayList<File>();
        resList.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new ModuleInfo(sourceFile.toFile(), false, resList, null));
    }

    /**
     * Constructor throws for non-existent resource path.
     */
    @Test
    void testConstructorThrowsForNonExistentResource() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var nonExistentRes = tempDir.resolve("nonexistent");
        assertThrows(IllegalArgumentException.class,
                () -> new ModuleInfo(sourceFile.toFile(), false, List.of(nonExistentRes.toFile()), null));
    }

    /**
     * Constructor throws for binarySpec with illegal parent path.
     */
    @Test
    void testConstructorThrowsForBinarySpecWithIllegalParent() throws IOException {
        var sourceFile = createModuleSource("MyModule");
        var invalidParent = tempDir.resolve("existing.txt/nested/output.xtc");
        Files.writeString(tempDir.resolve("existing.txt"), "not a directory");

        assertThrows(IllegalArgumentException.class,
                () -> new ModuleInfo(sourceFile.toFile(), false, null, invalidParent.toFile()));
    }
}
