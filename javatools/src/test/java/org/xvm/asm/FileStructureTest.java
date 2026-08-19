package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.util.Handy.byteArrayToHexDump;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writeMagnitude;

/**
 * Tests of XVM FileStructure.
 */
public class FileStructureTest {
    @Test
    public void testHasMultipleChildrenIgnoresFingerprints() {
        FileStructure file = new FileStructure("Test");
        file.ensureModule("Dependency").fingerprintRequired();

        assertEquals(2, file.getChildrenCount());
        assertFalse(file.hasMultipleChildren());
    }

    @Test
    public void testSingleFileMetadataRoundTrip()
            throws IOException {
        var file = new FileStructure("Solo");
        var ver  = new Version("2.1");
        file.getModule().setVersion(ver);
        file.ensureModule("Dependency").fingerprintRequired();

        var metadata = file.getFileMetadata();
        assertEquals(FileStructure.FileKind.Single, metadata.kind());
        assertEquals(List.of("Solo"), metadata.moduleNames());
        assertEquals(List.of(ver.toString()), metadata.versionsByModule().get("Solo"));
        assertFalse(metadata.hasMultipleModules());
        assertFalse(metadata.isBundle());

        var out = new ByteArrayOutputStream();
        file.writeTo(out);
        var ab = out.toByteArray();

        assertEquals(metadata, FileStructure.readMetadata(new ByteArrayInputStream(ab)));
        DataInput inMetadata = new DataInputStream(new ByteArrayInputStream(ab));
        assertEquals(metadata, FileStructure.readMetadata(inMetadata));

        var reread = new FileStructure(new ByteArrayInputStream(ab));
        assertEquals(FileStructure.FileKind.Single, reread.getFileKind());
        assertEquals(metadata, reread.getFileMetadata());
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
        bundle.findModule("Lib").markEmbedded();
        bundle.ensureModule("External").fingerprintRequired();

        var metadata = bundle.getFileMetadata();
        assertEquals(FileStructure.FileKind.Library, metadata.kind());
        assertEquals(List.of("App", "Lib"), metadata.moduleNames());
        assertEquals(List.of(verApp.toString()), metadata.versionsByModule().get("App"));
        assertEquals(List.of(verLib.toString()), metadata.versionsByModule().get("Lib"));
        assertFalse(metadata.moduleNames().contains("External"));
        assertTrue(metadata.hasMultipleModules());
        assertTrue(metadata.isBundle());

        var out = new ByteArrayOutputStream();
        bundle.writeTo(out);
        var ab = out.toByteArray();

        assertEquals(metadata, FileStructure.readMetadata(new ByteArrayInputStream(ab)));

        var reread = new FileStructure(new ByteArrayInputStream(ab));
        assertEquals(FileStructure.FileKind.Library, reread.getFileKind());
        assertEquals(metadata, reread.getFileMetadata());
    }

    @Test
    public void testUnknownMetadataFormatSkipsBlockForDisassembly()
            throws IOException {
        var file = new FileStructure("Future");

        var out = new ByteArrayOutputStream();
        file.writeTo(out);
        var ab = withUnknownMetadataFormat(out.toByteArray());

        assertNull(FileStructure.readMetadata(new ByteArrayInputStream(ab)));

        var reread = new FileStructure(new ByteArrayInputStream(ab));
        assertEquals("Future", reread.getModuleId().getName());
        assertEquals(FileStructure.FileKind.Single, reread.getFileKind());
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

    private static byte[] withUnknownMetadataFormat(byte[] ab)
            throws IOException {
        var in = new DataInputStream(new ByteArrayInputStream(ab));
        assertEquals(Constants.FILE_MAGIC, in.readInt());
        assertEquals(Constants.VERSION_MAJOR_CUR, in.readInt());
        assertEquals(Constants.VERSION_MINOR_CUR, in.readInt());

        var abMetadata = new byte[readMagnitude(in)];
        in.readFully(abMetadata);
        var abRemainder = in.readAllBytes();

        var inMetadata = new DataInputStream(new ByteArrayInputStream(abMetadata));
        assertEquals(1, readMagnitude(inMetadata));
        var abMetadataRemainder = inMetadata.readAllBytes();

        var outMetadata = new ByteArrayOutputStream();
        var dataMetadata = new DataOutputStream(outMetadata);
        writeMagnitude(dataMetadata, 999);
        dataMetadata.write(abMetadataRemainder);
        var abNewMetadata = outMetadata.toByteArray();

        var out = new ByteArrayOutputStream();
        var data = new DataOutputStream(out);
        data.writeInt(Constants.FILE_MAGIC);
        data.writeInt(Constants.VERSION_MAJOR_CUR);
        data.writeInt(Constants.VERSION_MINOR_CUR);
        writeMagnitude(data, abNewMetadata.length);
        data.write(abNewMetadata);
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
