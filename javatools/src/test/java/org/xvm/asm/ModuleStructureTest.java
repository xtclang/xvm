package org.xvm.asm;


import java.io.IOException;

import org.junit.Assert;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * Tests for the module structure and module-level rules and functionality.
 */
public class ModuleStructureTest
        extends FileStructureTest
    {
//    @Test
    public void testParseSimple()
            throws IOException
        {
        String src = "module test {}";
        testFileStructure(compile(src, null, null));
        }

//    @Test
    public void testParseIllegalParams()
        {
        // this should fail because a module can't have type params
        String src = "module test<SomeType> {}";
        compile(src, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
        }

//    @Test
    public void testParseIllegalExtends()
        {
        // this should fail because a module can't have type params
        String src = "module Test extends Something extends SomethingElse {}";
        compile(src, Severity.ERROR, Compiler.MULTIPLE_EXTEND_CLAUSES);
        }

//    @Test
    public void testParseIllegalImport()
        {
        // this should fail because a module doesn't "import"
        String src = "module Test import Something {}";
        compile(src, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
        }

//    @Test
    public void testParseIllegalImportEmbedded()
        {
        // this should fail because a module doesn't "import"
        String src = "module Test import:embedded Something {}";
        compile(src, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
        }

//    @Test
    public void testParseIllegalImportDesired()
        {
        // this should fail because a module doesn't "import"
        String src = "module Test import:desired Something {}";
        compile(src, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
        }

//    @Test
    public void testParseIllegalImportRequired()
        {
        // this should fail because a module doesn't "import"
        String src = "module Test import:required Something {}";
        compile(src, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
        }

//    @Test
    public void testParseIllegalImportOptional()
        {
        // this should fail because a module doesn't "import"
        String src = "module Test import:optional Something {}";
        compile(src, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
        }

//    @Test
    public void testParseIllegalInto()
        {
        // this should fail because a module does mix "into" something
        String src = "module Test into Something {}";
        compile(src, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
        }

//    @Test
    public void testModuleDoc()
        {
        String src = "/**\n * This is a module that\n\t* has some doc.\r\n */\nmodule Test {}";
        FileStructure   struct = compile(src, null, null);
        ModuleStructure module = struct.getModule();
        Assert.assertEquals(module.getDocumentation(), "This is a module that\nhas some doc.");
        }

//    @Test
    public void testIllegalConstructorParams()
        {
        // this should fail because a module can't have constructor params (without defaults)
        String src = "module Test(Int x = 1, Int y) {}";
        compile(src, Severity.ERROR, Compiler.CONSTRUCTOR_PARAM_DEFAULT_REQUIRED);
        }

//    @Test
    public void testLegalConstructorParams()
            throws IOException
        {
        // this should work because the constructor params have defaults
        String src = "module Test(Int x = 0, String s = \"hello\") {}";
        testFileStructure(compile(src, null, null));
        }

//    @Test
    public void testLegalPublic()
            throws IOException
        {
        // this should work because a module can be explicitly declared public
        String src = "public module Test {}";
        testFileStructure(compile(src, null, null));
        }

//    @Test
    public void testIllegalPublicPublic()
        {
        // this should fail because a module can't be public x2
        String src = "public public module Test.mycompany.com {}";
        compile(src, Severity.ERROR, Compiler.DUPLICATE_MODIFIER);
        }

//    @Test
    public void testIllegalPrivate()
        {
        // this should fail because a module can't be private
        String src = "private module Test.mycompany.com {}";
        compile(src, Severity.ERROR, Compiler.ILLEGAL_MODIFIER);
        }

//    @Test
    public void testIllegalProtected()
        {
        // this should fail because a module can't be protected
        String src = "protected module Test.mycompany.com {}";
        compile(src, Severity.ERROR, Compiler.ILLEGAL_MODIFIER);
        }

//    @Test
    public void testIllegalStatic()
        {
        // this should fail because a module can't be declared static
        String src = "static module Test.mycompany.com {}";
        compile(src, Severity.ERROR, Compiler.ILLEGAL_MODIFIER);
        }

//    @Test
    public void testConditional()
            throws IOException
        {
        // this should fail because a module can't be declared static
        String src = "static module Test.mycompany.com if (\"debug\".defined) {implements Runnable} {}";
        testFileStructure(compile(src, null, null));
        }
    }