package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.PrintWriter;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import org.xvm.asm.ConstantPool.MethodConstant.Builder;

import static org.xvm.util.Handy.byteArrayToHexDump;


/**
 * TODO
 *
 * @author cp 2016.04.14
 */
public class FileStructureTest
    {
    @Test
    public void testEmptyModule()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test", null, null);
        Assert.assertEquals("test", structfile.getModuleName());
        Assert.assertEquals(null, structfile.getPackageName());
        Assert.assertEquals(null, structfile.getClassName());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        structfile.writeTo(out);

        byte[] ab = out.toByteArray();
        System.out.println("file " + structfile + ":");
        System.out.println(byteArrayToHexDump(ab, 16));

        FileStructure structfile2 = new FileStructure(new ByteArrayInputStream(ab));
        Assert.assertEquals("test", structfile.getModuleName());
        Assert.assertEquals(null, structfile.getPackageName());
        Assert.assertEquals(null, structfile.getClassName());

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

    @Test
    public void testFoo()
            throws IOException
        {
        FileStructure structfile = new FileStructure("test", null, null);
        Assert.assertEquals("test", structfile.getModuleName());
        Assert.assertEquals(null, structfile.getPackageName());
        Assert.assertEquals(null, structfile.getClassName());

        ModuleStructure  structmodule  = (ModuleStructure) structfile.getTopmostStructure();
        PackageStructure structpackage = structmodule.ensurePackage("classes");
        ClassStructure   structclass   = structpackage.ensureClass("Test");
//        ConstantPool.MethodConstant constmethod = structfile.getConstantPool()
//                .ensureMethodConstant(structclass.getClassConstant(), "foo",
//                        null, null, null);
        MethodStructure  structmethod  = structclass.ensureMethod(structclass.methodBuilder("foo").toConstant());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        structfile.writeTo(out);

        byte[] ab = out.toByteArray();
        System.out.println("file " + structfile + ":");
        System.out.println(byteArrayToHexDump(ab, 16));

        FileStructure structfile2 = new FileStructure(new ByteArrayInputStream(ab));
        Assert.assertEquals(structfile, structfile2);

        out = new ByteArrayOutputStream();
        structfile2.writeTo(out);
        byte[] ab2 = out.toByteArray();
        Assert.assertTrue(Arrays.equals(ab, ab2));
        }
    }
