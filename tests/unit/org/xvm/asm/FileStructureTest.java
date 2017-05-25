package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.byteArrayToHexDump;


/**
 * Tests of XVM FileStructure.
 *
 * @author cp 2016.04.14
 */
public class FileStructureTest
    {
    @Test
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

    @Test
    public void testMinimumModule()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test");
        structfile.getModule()
                .createPackage(Constants.Access.PUBLIC, "x")
                .setImportedModule(structfile.getConstantPool().ensureModuleConstant("ecstasy.xtclang.org"));
        testFileStructure(structfile);
        }

    @Test
    public void testBaseClass()
            throws IOException
        {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        structfile.getModule().createClass(Constants.Access.PUBLIC, Component.Format.CLASS, Constants.CLASS_OBJECT);
        testFileStructure(structfile);
        }

    @Test
    public void testListClass()
            throws IOException
        {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        ClassStructure structobj = structfile.getModule().createClass(Constants.Access.PUBLIC,
                Component.Format.CLASS, Constants.CLASS_OBJECT);
        PackageStructure structpkg =structfile.getModule().createPackage(Constants.Access.PUBLIC,
                "collections");
        ClassStructure structclz = structpkg.createClass(Constants.Access.PUBLIC,
                Component.Format.INTERFACE, "List");
        structclz.addTypeParam("ElementType", structobj.getClassConstant().asTypeConstant());
        testFileStructure(structfile);
        }

    @Test
    public void testMapClass()
            throws IOException
        {
        FileStructure    file    = new FileStructure(Constants.ECSTASY_MODULE);
        ModuleStructure  module  = file.getModule();
        ClassStructure   clzObj  = module.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, Constants.CLASS_OBJECT);
        PackageStructure pkgColl = module.createPackage(Constants.Access.PUBLIC, "collections");
        ClassStructure   clzHash = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.INTERFACE, "Hashable");
        ClassStructure   clzMap  = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.INTERFACE, "Map");
        clzMap.addTypeParam("KeyType", clzObj.getClassConstant().asTypeConstant());
        clzMap.addTypeParam("ValueType", clzObj.getClassConstant().asTypeConstant());
        ClassStructure clzHashMap = pkgColl.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "HashMap");
        clzHashMap.addTypeParam("KeyType", clzHash.getClassConstant().asTypeConstant());
        clzHashMap.addTypeParam("ValueType", clzObj.getClassConstant().asTypeConstant());
        clzHashMap.addContribution(ClassStructure.Composition.Implements, clzMap.getClassConstant());

        testFileStructure(file);
        }

    @Test
    public void testFoo()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test");
        Assert.assertEquals("test", structfile.getModuleName());

        ModuleStructure  structmodule  = structfile.getModule();
        PackageStructure structpackage = structmodule.createPackage(Constants.Access.PUBLIC, "classes");
        ClassStructure   structclass   = structpackage.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Test");
        MethodStructure  structmethod  = structclass.createMethod(false, Constants.Access.PUBLIC,
                new TypeConstant[]{},
                "foo",
                new TypeConstant[]{});

        testFileStructure(structfile);
        }


    // ----- internal -----

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

        Assert.assertTrue(Arrays.equals(ab, ab2));
        }

    static final boolean DEBUG = true;
    }
