package org.xvm.asm;

import org.junit.Test;

import java.io.IOException;

/**
 * TODO
 *
 * @author cp 2017.05.25
 */
public class ModuleStructureTest
        extends FileStructureTest
    {
    @Test
    public void testParseSimple()
    throws IOException
        {
        FileStructure structfile = createFileStructure("module test {}");
        testFileStructure(structfile);
        }

    @Test
    public void testParseIllegalParams()
    throws IOException
        {
        // this should fail because a module can't have type params
        FileStructure structfile = createFileStructure("module test<SomeType> {}");
        testFileStructure(structfile);
        }
    }
