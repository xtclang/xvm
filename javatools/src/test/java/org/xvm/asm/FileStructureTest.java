package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;

import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.asm.constants.ClassConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.byteArrayToHexDump;


/**
 * Tests of XVM FileStructure.
 */
public class FileStructureTest
    {
//    @Test
    public void testEmptyModule()
            throws IOException
        {
        FileStructure structfile = new FileStructure("Test");
        Assert.assertEquals("Test", structfile.getModuleName());
        Assert.assertEquals("Test", structfile.getModule().getName());
        Assert.assertTrue(structfile.getModule().isPackageContainer());
        Assert.assertTrue(structfile.getModule().isClassContainer());
        Assert.assertTrue(structfile.getModule().isMethodContainer());
        Assert.assertEquals(Constants.Access.PUBLIC, structfile.getModule().getAccess());

        testFileStructure(structfile);
        }

//    @Test
    public void testMinimumModule()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test");
        structfile.getModule().createPackage(Constants.Access.PUBLIC, "x", null);
        // TODO .setImportedModule(structfile.getConstantPool().ensureModuleConstant("ecstasy.xtclang.org"));
        testFileStructure(structfile);
        }

//    @Test
    public void testBaseClass()
            throws IOException
        {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        structfile.getModule().createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Object", null);
        testFileStructure(structfile);
        }

//    @Test
    public void testListClass()
            throws IOException
        {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        ClassStructure structobj = structfile.getModule().createClass(Constants.Access.PUBLIC,
                Component.Format.CLASS, "Object", null);
        PackageStructure structpkg =structfile.getModule().createPackage(Constants.Access.PUBLIC,
                "collections", null);
        ClassStructure structclz = structpkg.createClass(Constants.Access.PUBLIC,
                Component.Format.INTERFACE, "List", null);
        structclz.addTypeParam("Element", ((ClassConstant) structobj.getIdentityConstant()).getType());
        testFileStructure(structfile);
        }

//    @Test
    public void testMapClass()
            throws IOException
        {
        FileStructure    file    = new FileStructure(Constants.ECSTASY_MODULE);
        ModuleStructure  module  = file.getModule();
        ClassStructure   clzObj  = module.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Object", null);
        PackageStructure pkgColl = module.createPackage(Constants.Access.PUBLIC, "collections", null);
        ClassStructure   clzHash = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.INTERFACE, "Hashable", null);
        ClassStructure   clzMap  = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.INTERFACE, "Map", null);
        clzMap.addTypeParam("Key", ((ClassConstant) clzObj.getIdentityConstant()).getType());
        clzMap.addTypeParam("Value", ((ClassConstant) clzObj.getIdentityConstant()).getType());
        ClassStructure clzHashMap = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "HashMap", null);
        clzHashMap.addTypeParam("Key", ((ClassConstant) clzHash.getIdentityConstant()).getType());
        clzHashMap.addTypeParam("Value", ((ClassConstant) clzObj.getIdentityConstant()).getType());
        clzHashMap.addContribution(ClassStructure.Composition.Implements, clzMap.getIdentityConstant().getType());

        testFileStructure(file);
        }

    public static FileStructure createFileStructure(String sCode)
        {
        Source                   source   = new Source(sCode);
        ErrorList                errlist  = new ErrorList(10);
        Parser                   parser   = new Parser(source, errlist);
        List<Statement>          stmts    = parser.parseSource().getStatements();
        TypeCompositionStatement module   = (TypeCompositionStatement) stmts.get(stmts.size() - 1);
        Compiler                 compiler = new Compiler(module, errlist);
        Assert.assertEquals(0, errlist.getSeriousErrorCount());
        return compiler.generateInitialFileStructure();
        }

    // ----- internal -----

    public static FileStructure compile(String sSrc, Severity sev, String sCode)
        {
        Source        source  = new Source(sSrc);
        ErrorList     errlist = new ErrorList(10);
        FileStructure struct  = null;

        try
            {
            Parser parser = new Parser(source, errlist);
            List<Statement> stmts = parser.parseSource().getStatements();
            TypeCompositionStatement module =
                    (TypeCompositionStatement) stmts.get(stmts.size() - 1);
            Compiler compiler = new Compiler(module, errlist);

            struct = compiler.generateInitialFileStructure();
            }
        catch (CompilerException e)
            {
            if ((sev != Severity.ERROR && sev != Severity.FATAL))
                {
                throw e;
                }
            }

        if (sev != null)
            {
            Assert.assertEquals(sev, errlist.getSeverity());
            }

        if (sCode != null)
            {
            boolean fFound = false;
            for (ErrorInfo err : errlist.getErrors())
                {
                if (err.getCode().equals(sCode))
                    {
                    fFound = true;
                    break;
                    }
                }
            Assert.assertTrue(fFound);
            }

        return struct;
        }

    public static void testFileStructure(FileStructure structfile)
            throws IOException
        {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        structfile.writeTo(out);

        byte[] ab = out.toByteArray();

        if (DEBUG)
            {
            System.out.println("file " + structfile + ":");
            System.out.println(byteArrayToHexDump(ab, 16));
            }

        FileStructure structfile2 = new FileStructure(new ByteArrayInputStream(ab));
        Assert.assertEquals(structfile.getModuleName(), structfile2.getModuleName());

        if (DEBUG)
            {
            System.out.println("structfile:");
            structfile.dump(new PrintWriter(System.out, true));
            System.out.println("structfile2:");
            structfile2.dump(new PrintWriter(System.out, true));
            }

        Assert.assertEquals(structfile, structfile2);

        out = new ByteArrayOutputStream();
        structfile2.writeTo(out);
        byte[] ab2 = out.toByteArray();

        if (DEBUG)
            {
            if (!Arrays.equals(ab, ab2))
                {
                System.out.println("DIFF! re-assembled " + structfile + ":");
                System.out.println(byteArrayToHexDump(ab2, 16));

                FileStructure structfile3 = new FileStructure(new ByteArrayInputStream(ab2));
                System.out.println("structfile3:");
                structfile3.dump(new PrintWriter(System.out, true));
                Assert.assertEquals(structfile.getModuleName(), structfile3.getModuleName());
                }
            }

        Assert.assertArrayEquals(ab, ab2);
        }

//    @Test
    public void testFoo()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test");
        Assert.assertEquals("test", structfile.getModuleName());

        ModuleStructure  structmodule  = structfile.getModule();
        PackageStructure structpackage = structmodule.createPackage(Constants.Access.PUBLIC, "classes", null);
        ClassStructure   structclass   = structpackage.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        MethodStructure  structmethod  = structclass.createMethod(false, Constants.Access.PUBLIC, null,
            Parameter.NO_PARAMS, "foo", Parameter.NO_PARAMS, true, true);

        testFileStructure(structfile);
        }

    static final boolean DEBUG = true;
    }