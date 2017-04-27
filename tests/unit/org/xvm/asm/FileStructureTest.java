package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.PrintWriter;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

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
        FileStructure structfile = new FileStructure("test");
        Assert.assertEquals("test", structfile.getModuleName());

        testFileStructure(structfile);
        }

    @Test
    public void testMinimumModule()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test");
        structfile.getMainModule().ensurePackage("x").setImportedModule(structfile.getConstantPool().ensureModuleConstant("ecstasy.xtclang.org"));
        testFileStructure(structfile);
        }

    @Test
    public void testBaseClass()
            throws IOException
        {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        structfile.getMainModule().ensureClass(Constants.CLASS_OBJECT);
        testFileStructure(structfile);
        }

    @Test
    public void testListClass()
            throws IOException
        {
        FileStructure structfile = new FileStructure(Constants.ECSTASY_MODULE);
        ClassStructure structobj = structfile.getMainModule().ensureClass(Constants.CLASS_OBJECT);
        PackageStructure structpkg =structfile.getMainModule().ensurePackage("collections");
        ClassStructure structclz = structpkg.ensureClass("List");
        structclz.setCategory(ClassStructure.Category.Interface);
        structclz.addTypeParam("T", structobj.getClassConstant().asTypeConstant());
        testFileStructure(structfile);
        }

    @Test
    public void testMapClass()
            throws IOException
        {
        FileStructure    file    = new FileStructure(Constants.ECSTASY_MODULE);
        ModuleStructure  module  = file.getMainModule();
        ClassStructure   clzObj  = module.ensureClass(Constants.CLASS_OBJECT);
        PackageStructure pkgColl = module.ensurePackage("collections");
        ClassStructure   clzHash = module.ensureClass("Hashable");
        clzHash.setCategory(ClassStructure.Category.Interface);

        ClassStructure clzMap = pkgColl.ensureClass("Map");
        clzMap.setCategory(ClassStructure.Category.Interface);
        clzMap.addTypeParam("K", clzObj.getClassConstant().asTypeConstant());
        clzMap.addTypeParam("V", clzObj.getClassConstant().asTypeConstant());

        ClassStructure clzHashMap = pkgColl.ensureClass("HashMap");
        clzHashMap.addTypeParam("K", clzHash.getClassConstant().asTypeConstant());
        clzHashMap.addTypeParam("V", clzObj.getClassConstant().asTypeConstant());
        clzHashMap.addContribution(ClassStructure.Composition.Implements, clzMap.getClassConstant());

        testFileStructure(file);
        }

    @Test
    public void testFoo()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test");
        Assert.assertEquals("test", structfile.getModuleName());

        ModuleStructure  structmodule  = (ModuleStructure) structfile.getMainModule();
        PackageStructure structpackage = structmodule.ensurePackage("classes");
        ClassStructure   structclass   = structpackage.ensureClass("Test");

        // TODO this fails... why?
//        MethodStructure  structmethod  = structclass.ensureMethod(structclass.methodBuilder("foo").toConstant());

        testFileStructure(structfile);
        }


    // ----- internal -----

    public void testFileStructure(FileStructure structfile)
            throws IOException
        {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        structfile.writeTo(out);

        byte[] ab = out.toByteArray();

        // TODO remove
        System.out.println("file " + structfile + ":");
        System.out.println(byteArrayToHexDump(ab, 16));

        FileStructure structfile2 = new FileStructure(new ByteArrayInputStream(ab));
        Assert.assertEquals(structfile.getModuleName(), structfile2.getModuleName());

        // TODO remove
        System.out.println("structfile:");
        structfile.dump(new PrintWriter(System.out, true));
        System.out.println("structfile2:");
        structfile2.dump(new PrintWriter(System.out, true));

        Assert.assertEquals(structfile, structfile2);

        out = new ByteArrayOutputStream();
        structfile2.writeTo(out);
        byte[] ab2 = out.toByteArray();
        Assert.assertTrue(Arrays.equals(ab, ab2));
        }
    }
