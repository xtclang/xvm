package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;

import java.time.Instant;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.constants.NamedCondition;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.util.Handy.byteArrayToHexDump;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writeMagnitude;

/**
 * Tests of XVM FileStructure.
 */
public class FileStructureTest {
    @Test
    public void testRuntimeLinkUsesDefinitionsAlreadyInTheFile() {
        var file = new FileStructure("App");
        var library = createNestedModule("Library");
        library.getModule().setVersion(new Version("1.0"));
        file.merge(library.getModule(), false, false);
        ModuleStructure embedded = file.getChild("Library");

        assertNull(file.linkModules(new BuildRepository(), true));
        assertSame(embedded, file.getChild("Library"));
        assertTrue(file.isLinked());
        assertTrue(file.validateModuleConstants());
    }

    @Test
    public void testRuntimeLinkStillRequiresDependenciesOfEmbeddedModules() {
        var file = new FileStructure("App");
        var library = createNestedModule("Library");
        library.ensureModule("Dependency").fingerprintRequired();
        file.merge(library.getModule(), false, false);

        var repository = new BuildRepository();
        assertEquals("Dependency", file.linkModules(repository, true).getName());
        repository.storeModule(new FileStructure("Dependency").getModule());
        assertNull(file.linkModules(repository, true));
        ModuleStructure dependency = file.getChild("Dependency");
        assertFalse(dependency.isFingerprint());
        assertTrue(file.validateModuleConstants());
    }

    @Test
    public void testHasMultipleChildrenIgnoresFingerprints() {
        FileStructure file = new FileStructure("Test");
        file.ensureModule("Dependency").fingerprintRequired();

        assertEquals(2, file.getChildrenCount());
        assertFalse(file.isBundle());
    }

    @Test
    public void testSingleFileMetadataRoundTrip()
            throws IOException {
        var file = new FileStructure("Solo");
        var ver  = new Version("2.1");
        file.getModule().setVersion(ver);
        file.ensureModule("Dependency").fingerprintRequired();

        var metadata = file.buildFileInfo();
        assertEquals(FileStructure.FileKind.Single, metadata.kind());
        assertEquals(Set.of("Solo"), metadata.modules().keySet());
        assertEquals(new VersionTree(ver, true), metadata.modules().get("Solo"));
        assertFalse(metadata.hasMultipleModules());
        assertFalse(metadata.isBundle());

        var out = new ByteArrayOutputStream();
        file.writeTo(out);
        var ab = out.toByteArray();

        assertEquals(metadata, FileStructure.readFileInfo(new ByteArrayInputStream(ab)));
        DataInput inMetadata = new DataInputStream(new ByteArrayInputStream(ab));
        assertEquals(metadata, FileStructure.readFileInfo(inMetadata));

        var reread = new FileStructure(new ByteArrayInputStream(ab));
        assertEquals(FileStructure.FileKind.Single, reread.getFileKind());
        assertEquals(metadata, reread.buildFileInfo());
    }

    @Test
    public void testLibraryFileMetadataRoundTripExcludesFingerprints()
            throws IOException {
        var fileLib = new FileStructure("Lib");
        var verLib  = new Version("1.0");
        fileLib.getModule().setVersion(verLib);

        var bundle = new FileStructure("App");
        var verApp = new Version("3.2");
        bundle.getModule().setVersion(verApp);
        bundle.merge(fileLib.getModule(), false, false);
        bundle.getChild("Lib").markEmbedded();
        bundle.ensureModule("External").fingerprintRequired();

        var metadata = bundle.buildFileInfo();
        assertEquals(FileStructure.FileKind.Library, metadata.kind());
        // the order is part of the contract: the main module always comes first, and the module
        // listing must stay deterministic (getModuleNames(), reflection's module list)
        assertEquals(List.of("App", "Lib"), List.copyOf(metadata.modules().keySet()));
        assertEquals(new VersionTree(verApp, true), metadata.modules().get("App"));
        assertEquals(new VersionTree(verLib, true), metadata.modules().get("Lib"));
        assertFalse(metadata.modules().containsKey("External"));
        assertTrue(metadata.hasMultipleModules());
        assertTrue(metadata.isBundle());

        var out = new ByteArrayOutputStream();
        bundle.writeTo(out);
        var ab = out.toByteArray();

        assertEquals(metadata, FileStructure.readFileInfo(new ByteArrayInputStream(ab)));

        var reread = new FileStructure(new ByteArrayInputStream(ab));
        assertEquals(FileStructure.FileKind.Library, reread.getFileKind());
        assertEquals(metadata, reread.buildFileInfo());
        // Map.equals is order-insensitive, so assert the deterministic order across the
        // write/read round trip explicitly
        assertEquals(List.of("App", "Lib"),
                List.copyOf(FileStructure.readFileInfo(new ByteArrayInputStream(ab)).modules().keySet()));
    }

    @Test
    public void testExplicitTimestampMakesGenerationReproducible()
            throws IOException {
        // with an explicit creation timestamp, two completely independent generations of the same
        // module are byte-identical; the wall-clock default in FileStructure(String) is the only
        // generation-time input that is not a pure function of the module contents
        var timestamp = Instant.parse("2026-01-01T00:00:00Z");

        var outFirst  = new ByteArrayOutputStream();
        var outSecond = new ByteArrayOutputStream();
        new FileStructure("Repro", timestamp).writeTo(outFirst);
        new FileStructure("Repro", timestamp).writeTo(outSecond);
        assertArrayEquals(outFirst.toByteArray(), outSecond.toByteArray());
    }

    @Test
    public void testUnknownFileKindIsRejected()
            throws IOException {
        var file = new FileStructure("Future");

        var out = new ByteArrayOutputStream();
        file.writeTo(out);
        var ab = withUnknownFileKind(out.toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> FileStructure.readFileInfo(new ByteArrayInputStream(ab)));
        assertThrows(IllegalArgumentException.class,
                () -> new FileStructure(new ByteArrayInputStream(ab)));
    }

    @Test
    public void testMergeAlreadyPresentModuleDoesNotCloneChildrenTwice() {
        FileStructure   source    = createNestedModule("Test");
        ModuleStructure sourceMod = source.getModule();
        Component       sourcePkg = sourceMod.getChildByNameMap().get("util");
        Component       sourceClz = sourcePkg.getChildByNameMap().get("Helper");
        int             sourceKids = sourceMod.getChildrenCount();

        FileStructure dest = new FileStructure(sourceMod, false);
        ModuleStructure destMod = dest.getModule();
        Component       destPkg = destMod.getChildByNameMap().get("util");
        Component       destClz = destPkg.getChildByNameMap().get("Helper");
        assertNotNull(destPkg);
        assertNotNull(destClz);
        assertNotSame(sourcePkg, destPkg);

        assertDoesNotThrow(() -> dest.merge(sourceMod, true, false));

        assertSame(sourcePkg, sourceMod.getChildByNameMap().get("util"));
        assertSame(sourceClz, sourcePkg.getChildByNameMap().get("Helper"));
        assertEquals(sourceKids, sourceMod.getChildrenCount());
        assertEquals(1, siblingCount(sourcePkg));
        assertEquals(1, siblingCount(sourceClz));

        assertSame(destPkg, destMod.getChildByNameMap().get("util"));
        assertSame(destClz, destPkg.getChildByNameMap().get("Helper"));
        assertEquals(1, siblingCount(destPkg));
        assertEquals(1, siblingCount(destClz));
        assertTrue(dest.validateModuleConstants());
        assertSame(dest.getConstantPool(), destMod.getIdentityConstant().getConstantPool());
        assertSame(dest.getConstantPool(), destPkg.getIdentityConstant().getConstantPool());

        assertDoesNotThrow(() -> dest.merge(sourceMod, true, false));
        assertSame(destPkg, dest.getModule().getChildByNameMap().get("util"));
        assertSame(destClz, destPkg.getChildByNameMap().get("Helper"));
        assertEquals(1, siblingCount(destPkg));
        assertEquals(1, siblingCount(destClz));
        assertTrue(siblingCount(dest.getModule()) >= 1);
        assertTrue(dest.validateModuleConstants());
    }

    @Test
    public void testMergeTakeFileSelectsMergedModuleAndMetadata(@TempDir Path tempDir)
            throws IOException {
        File fileLib = tempDir.resolve("lib.xtc").toFile();
        createNestedModule("Lib").writeTo(fileLib);
        FileStructure lib = new FileStructure(fileLib);

        File fileApp = tempDir.resolve("app.xtc").toFile();
        new FileStructure("App").writeTo(fileApp);
        FileStructure dest = new FileStructure(fileApp);
        assertEquals("App", dest.getModuleId().getName());
        assertEquals(fileApp, dest.getOSFile());

        dest.merge(lib.getModule(), false, true);

        assertEquals("Lib", dest.getModuleId().getName());
        assertEquals(fileLib, dest.getOSFile());
        assertNotNull(dest.getModule().getChild("util"));
        assertNotNull(dest.getModule().getChild("util").getChild("Helper"));
        assertTrue(dest.getModule().isMainModule());
        assertTrue(dest.validateModuleConstants());

        dest.merge(lib.getModule(), false, true);
        assertEquals("Lib", dest.getModuleId().getName());
        assertEquals(fileLib, dest.getOSFile());
        assertEquals(1, siblingCount(dest.getModule().getChildByNameMap().get("util")));
        assertTrue(dest.validateModuleConstants());
    }

    @Test
    public void testMergeWithoutTakeFileRetainsPrimary(@TempDir Path tempDir)
            throws IOException {
        File fileLib = tempDir.resolve("lib.xtc").toFile();
        createNestedModule("Lib").writeTo(fileLib);
        FileStructure lib = new FileStructure(fileLib);

        File fileApp = tempDir.resolve("app.xtc").toFile();
        new FileStructure("App").writeTo(fileApp);
        FileStructure dest = new FileStructure(fileApp);

        dest.merge(lib.getModule(), false, false);

        assertEquals("App", dest.getModuleId().getName());
        assertEquals(fileApp, dest.getOSFile());
        assertNotNull(dest.getChild("Lib").getChild("util"));
        assertNotNull(dest.getChild("Lib").getChild("util").getChild("Helper"));
        assertTrue(dest.getModule().isMainModule());
        assertFalse(dest.getChild("Lib").isMainModule());
        assertTrue(dest.validateModuleConstants());

        dest.merge(lib.getModule(), false, false);
        assertEquals("App", dest.getModuleId().getName());
        assertEquals(fileApp, dest.getOSFile());
        assertEquals(1, siblingCount(dest.getChild("Lib").getChildByNameMap().get("util")));
        assertTrue(dest.validateModuleConstants());
    }

    @Test
    public void testMergeAbsentModuleClonesChildrenAndFingerprints() {
        FileStructure lib = createNestedModule("Lib");
        lib.ensureModule("dep.example.org").fingerprintRequired();

        FileStructure dest = new FileStructure("App");
        dest.merge(lib.getModule(), false, false);

        ModuleStructure merged = dest.getChild("Lib");
        assertNotNull(merged);
        assertFalse(merged.isFingerprint());
        assertNotNull(merged.getChild("util"));
        assertNotNull(merged.getChild("util").getChild("Helper"));
        assertEquals(1, siblingCount(merged.getChildByNameMap().get("util")));
        assertEquals(1, siblingCount(merged.getChild("util").getChildByNameMap().get("Helper")));

        ModuleStructure fingerprint = dest.getChild("dep.example.org");
        assertNotNull(fingerprint);
        assertTrue(fingerprint.isFingerprint());
        assertTrue(dest.validateModuleConstants());
    }

    @Test
    public void testMergeReplacesFingerprintWithRealModuleAndPropagatesDependencies() {
        FileStructure dest = new FileStructure("App");
        dest.ensureModule("Lib").fingerprintRequired();
        assertTrue(dest.getChild("Lib").isFingerprint());

        FileStructure lib = createNestedModule("Lib");
        lib.ensureModule("dep.example.org").fingerprintRequired();

        dest.merge(lib.getModule(), false, false);

        ModuleStructure merged = dest.getChild("Lib");
        assertNotNull(merged);
        assertFalse(merged.isFingerprint());
        assertNotNull(merged.getChild("util"));
        assertNotNull(merged.getChild("util").getChild("Helper"));
        assertEquals(1, siblingCount(merged.getChildByNameMap().get("util")));

        ModuleStructure fingerprint = dest.getChild("dep.example.org");
        assertNotNull(fingerprint);
        assertTrue(fingerprint.isFingerprint());
        assertTrue(dest.validateModuleConstants());

        lib.ensureModule("other.example.org").fingerprintRequired();
        dest.merge(lib.getModule(), false, false);
        assertNotNull(dest.getChild("other.example.org"));
        assertTrue(dest.getChild("other.example.org").isFingerprint());
        assertEquals(1, siblingCount(dest.getChild("Lib").getChildByNameMap().get("util")));
        assertTrue(dest.validateModuleConstants());
    }

    @Test
    public void testMergeBundleSynthesizesDependencyFingerprints() {
        FileStructure lib = createNestedModule("Lib");
        FileStructure dep = createNestedModule("Dep");
        FileStructure bundle = new FileStructure(lib.getModule(), false);
        bundle.merge(dep.getModule(), false, false);
        bundle.getChild("Dep").markEmbedded();

        PackageStructure importPkg = bundle.getModule().createPackage(
                Constants.Access.PUBLIC, "dep", null);
        importPkg.setImportedModule(bundle.getChild("Dep"));

        FileStructure dest = new FileStructure("App");
        dest.merge(bundle.getModule(), false, false);

        assertFalse(dest.getChild("Lib").isFingerprint());
        assertNotNull(dest.getChild("Lib").getChild("util"));
        assertNotNull(dest.getChild("Lib").getChild("util").getChild("Helper"));
        assertTrue(dest.getChild("Dep").isFingerprint());
        assertTrue(dest.validateModuleConstants());
    }

    @Test
    public void testMergePreservesVersionSiblingsAndFileCopy()
            throws IOException {
        FileStructure v1 = createNestedModule("Test");
        v1.getModule().setVersion(new Version("1.0"));
        FileStructure v2 = createNestedModule("Test");
        v2.getModule().setVersion(new Version("2.0"));

        FileStructure dest = new FileStructure(v1.getModule(), false);
        dest.merge(v2.getModule(), false, false);

        ModuleStructure first = dest.getModule();
        assertEquals(new Version("1.0"), first.getVersion());
        ModuleStructure second = (ModuleStructure) first.getNextSibling();
        assertNotNull(second);
        assertEquals(new Version("2.0"), second.getVersion());
        assertNull(second.getNextSibling());
        assertEquals(1, siblingCount(first.getChildByNameMap().get("util")));
        assertEquals(1, siblingCount(first.getChild("util").getChildByNameMap().get("Helper")));
        var versions = dest.buildFileInfo().modules().get("Test");
        assertNotNull(versions);
        assertTrue(versions.contains(new Version("1.0")));
        assertTrue(versions.contains(new Version("2.0")));
        assertTrue(dest.validateModuleConstants());

        FileStructure copy = new FileStructure(dest);
        ModuleStructure copyFirst = copy.getModule();
        assertEquals(new Version("1.0"), copyFirst.getVersion());
        ModuleStructure copySecond = (ModuleStructure) copyFirst.getNextSibling();
        assertNotNull(copySecond);
        assertEquals(new Version("2.0"), copySecond.getVersion());
        assertEquals(1, siblingCount(copyFirst.getChildByNameMap().get("util")));
        assertEquals(1, siblingCount(copyFirst.getChild("util").getChildByNameMap().get("Helper")));
        assertTrue(copy.validateModuleConstants());
        assertEquals(FileStructure.FileKind.Library, copy.getFileKind());

        var out = new ByteArrayOutputStream();
        copy.writeTo(out);
        var reread = new FileStructure(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(copy, reread);
        assertEquals(new Version("1.0"), reread.getModule().getVersion());
        ModuleStructure rereadSecond = (ModuleStructure) reread.getModule().getNextSibling();
        assertNotNull(rereadSecond);
        assertEquals(new Version("2.0"), rereadSecond.getVersion());
        assertEquals(1, siblingCount(reread.getModule().getChildByNameMap().get("util")));
    }

    @Test
    public void testMergePreservesConditionalSiblingsAndFileCopy()
            throws IOException {
        FileStructure base = createNestedModule("Test");
        FileStructure debug = createNestedModule("Test");
        debug.getModule().setCondition(debug.getConstantPool().ensureNamedCondition("debug"));

        FileStructure dest = new FileStructure(base.getModule(), false);
        dest.merge(debug.getModule(), false, false);

        ModuleStructure first = dest.getModule();
        assertNull(first.getCondition());
        ModuleStructure second = (ModuleStructure) first.getNextSibling();
        assertNotNull(second);
        assertEquals("debug", ((NamedCondition) second.getCondition()).getName());
        assertEquals(1, siblingCount(first.getChildByNameMap().get("util")));
        assertEquals(1, siblingCount(first.getChild("util").getChildByNameMap().get("Helper")));
        assertTrue(dest.validateModuleConstants());

        FileStructure copy = new FileStructure(dest);
        ModuleStructure copyFirst = copy.getModule();
        assertNull(copyFirst.getCondition());
        ModuleStructure copySecond = (ModuleStructure) copyFirst.getNextSibling();
        assertNotNull(copySecond);
        assertEquals("debug", ((NamedCondition) copySecond.getCondition()).getName());
        assertEquals(1, siblingCount(copyFirst.getChildByNameMap().get("util")));
        assertTrue(copy.validateModuleConstants());

        testFileStructure(copy);
    }

    @Test @Disabled("TODO: Re-enable test")
    public void testEmptyModule()
            throws IOException {
        FileStructure structfile = new FileStructure("Test");
        assertEquals("Test", structfile.getModuleId().getName());
        assertEquals("Test", structfile.getModule().getName());
        assertTrue(structfile.getModule().isPackageContainer());
        assertTrue(structfile.getModule().isClassContainer());
        assertTrue(structfile.getModule().isMethodContainer());
        assertEquals(Constants.Access.PUBLIC, structfile.getModule().getAccess());

        testFileStructure(structfile);
    }

    @Test @Disabled("TODO: Re-enable test")
    public void testMinimumModule()
            throws IOException {
        FileStructure structfile = new FileStructure("test");
        structfile.getModule().createPackage(Constants.Access.PUBLIC, "x", null);
        // TODO .setImportedModule(structfile.getConstantPool().ensureModuleConstant("ecstasy.xtclang.org"));
        testFileStructure(structfile);
    }

    @Test @Disabled("TODO: Re-enable test")
    public void testBaseClass()
            throws IOException {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        structfile.getModule().createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Object", null);
        testFileStructure(structfile);
    }

    @Test @Disabled
    public void testListClass()
            throws IOException {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        ClassStructure structobj = structfile.getModule().createClass(Constants.Access.PUBLIC,
                Component.Format.CLASS, "Object", null);
        PackageStructure structpkg =structfile.getModule().createPackage(Constants.Access.PUBLIC,
                "collections", null);
        ClassStructure structclz = structpkg.createClass(Constants.Access.PUBLIC,
                Component.Format.INTERFACE, "List", null);
        structclz.addTypeParam("Element", structobj.getIdentityConstant().getType());
        testFileStructure(structfile);
    }

    @Test @Disabled("TODO: Re-enable test")
    public void testMapClass()
            throws IOException {
        FileStructure    file    = new FileStructure(Constants.ECSTASY_MODULE);
        ModuleStructure  module  = file.getModule();
        ClassStructure   clzObj  = module.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Object", null);
        PackageStructure pkgColl = module.createPackage(Constants.Access.PUBLIC, "collections", null);
        ClassStructure   clzHash = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.INTERFACE, "Hashable", null);
        ClassStructure   clzMap  = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.INTERFACE, "Map", null);
        clzMap.addTypeParam("Key", clzObj.getIdentityConstant().getType());
        clzMap.addTypeParam("Value", clzObj.getIdentityConstant().getType());
        ClassStructure clzHashMap = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "HashMap", null);
        clzHashMap.addTypeParam("Key", clzHash.getIdentityConstant().getType());
        clzHashMap.addTypeParam("Value", clzObj.getIdentityConstant().getType());
        clzHashMap.addContribution(ClassStructure.Composition.Implements, clzMap.getIdentityConstant().getType());

        testFileStructure(file);
    }

    public static FileStructure createFileStructure(String sCode) {
        Source                   source   = new Source(sCode);
        ErrorList                errlist  = new ErrorList(10);
        Parser                   parser   = new Parser(source, errlist);
        List<Statement>          stmts    = parser.parseSource().getStatements();
        TypeCompositionStatement module   = (TypeCompositionStatement) stmts.getLast();
        Compiler                 compiler = new Compiler(module, errlist);
        assertEquals(0, errlist.getSeriousErrorCount());
        return compiler.generateInitialFileStructure();
    }

    private static FileStructure createNestedModule(String sName) {
        FileStructure    file = new FileStructure(sName);
        PackageStructure pkg  = file.getModule().createPackage(Constants.Access.PUBLIC, "util", null);
        pkg.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Helper", null);
        return file;
    }

    private static int siblingCount(Component component) {
        int count = 0;
        for (Component sibling = component; sibling != null; sibling = sibling.getNextSibling()) {
            ++count;
        }
        return count;
    }

    // ----- internal -----

    public static FileStructure compile(String sSrc, Severity sev, String sCode) {
        Source        source  = new Source(sSrc);
        ErrorList     errlist = new ErrorList(10);
        FileStructure struct  = null;

        try {
            Parser parser = new Parser(source, errlist);
            List<Statement> stmts = parser.parseSource().getStatements();
            TypeCompositionStatement module = (TypeCompositionStatement) stmts.getLast();
            Compiler compiler = new Compiler(module, errlist);

            struct = compiler.generateInitialFileStructure();
        } catch (CompilerException e) {
            if ((sev != Severity.ERROR && sev != Severity.FATAL)) {
                throw e;
            }
        }

        if (sev != null) {
            assertEquals(sev, errlist.getSeverity());
        }

        if (sCode != null) {
            boolean fFound = false;
            for (ErrorInfo err : errlist.getErrors()) {
                if (err.getCode().equals(sCode)) {
                    fFound = true;
                    break;
                }
            }
            assertTrue(fFound);
        }

        return struct;
    }

    public static void testFileStructure(FileStructure structfile)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        structfile.writeTo(out);

        byte[] ab = out.toByteArray();

        if (DEBUG) {
            System.out.println("file " + structfile + ":");
            System.out.println(byteArrayToHexDump(ab, 16));
        }

        FileStructure structfile2 = new FileStructure(new ByteArrayInputStream(ab));
        assertEquals(structfile.getModuleId(), structfile2.getModuleId());

        if (DEBUG) {
            System.out.println("structfile:");
            structfile.dump(new PrintWriter(System.out, true));
            System.out.println("structfile2:");
            structfile2.dump(new PrintWriter(System.out, true));
        }

        assertEquals(structfile, structfile2);

        out = new ByteArrayOutputStream();
        structfile2.writeTo(out);
        byte[] ab2 = out.toByteArray();

        if (DEBUG) {
            if (!Arrays.equals(ab, ab2)) {
                System.out.println("DIFF! re-assembled " + structfile + ":");
                System.out.println(byteArrayToHexDump(ab2, 16));

                FileStructure structfile3 = new FileStructure(new ByteArrayInputStream(ab2));
                System.out.println("structfile3:");
                structfile3.dump(new PrintWriter(System.out, true));
                assertEquals(structfile.getModuleId(), structfile3.getModuleId());
            }
        }

        assertArrayEquals(ab, ab2);
    }

    private static byte[] withUnknownFileKind(byte[] ab)
            throws IOException {
        var in = new DataInputStream(new ByteArrayInputStream(ab));
        assertEquals(Constants.FILE_MAGIC, in.readInt());
        assertEquals(Constants.VERSION_MAJOR_CUR, in.readInt());
        assertEquals(Constants.VERSION_MINOR_CUR, in.readInt());
        assertEquals(FileStructure.FileKind.Single.ordinal(), readMagnitude(in));
        var abRemainder = in.readAllBytes();

        var out = new ByteArrayOutputStream();
        var data = new DataOutputStream(out);
        data.writeInt(Constants.FILE_MAGIC);
        data.writeInt(Constants.VERSION_MAJOR_CUR);
        data.writeInt(Constants.VERSION_MINOR_CUR);
        writeMagnitude(data, 999);
        data.write(abRemainder);
        return out.toByteArray();
    }

    @Test @Disabled("TODO: Re-enable test")
    public void testFoo()
            throws IOException {
        FileStructure structfile = new FileStructure("test");
        assertEquals("test", structfile.getModuleId().getName());

        ModuleStructure  structmodule  = structfile.getModule();
        PackageStructure structpackage = structmodule.createPackage(Constants.Access.PUBLIC, "classes", null);
        ClassStructure   structclass   = structpackage.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        MethodStructure  structmethod  = structclass.createMethod(false, Constants.Access.PUBLIC, null,
            Parameter.NO_PARAMS, "foo", Parameter.NO_PARAMS, true, true);

        testFileStructure(structfile);
    }

    static final boolean DEBUG = true;
}
