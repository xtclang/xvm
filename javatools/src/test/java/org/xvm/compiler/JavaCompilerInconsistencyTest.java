package org.xvm.compiler;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.TypeCompositionStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for open inconsistencies in the Java compiler.
 */
public class JavaCompilerInconsistencyTest {
    @Test
    public void testJI1NestedClassFormalNameResolutionDependsOnMemberTiming() {
        FileStructure file = compileThroughNameResolution("""
                module ecstasy.xtclang.org {
                    interface Object {}

                    interface Iterator<Element> {}

                    interface Appender<Element> {}

                    class Outer<Element> {
                        class Inner(Appender<Element> ctorParam)
                                implements Iterator<Element>
                                implements Appender<Element> {
                            Appender<Element> field;
                        }
                    }
                }
                """);

        ModuleStructure module = file.getModule();
        ClassStructure  outer  = module.getChild("Outer", ClassStructure.class);
        ClassStructure  inner  = outer.getChild("Inner", ClassStructure.class);

        PropertyStructure field = inner.getChild("field", PropertyStructure.class);
        TypeConstant      typeFieldElement =
                getOnlyTypeParameter(field.getType(), "field");

        MethodStructure constructor = getOnlyMethod(inner, "construct");
        Parameter       ctorParam   = constructor.getParam("ctorParam");
        TypeConstant    typeCtorElement =
                getOnlyTypeParameter(ctorParam.getType(), "constructor parameter");

        assertNotEquals(typeFieldElement, typeCtorElement,
                "JI-1 no longer reproduces; update this characterization test");
        assertTrue(typeFieldElement.getValueString().contains("Iterator.Element"),
                () -> "expected field Element to resolve through Iterator; got "
                        + typeFieldElement.getValueString());
        assertTrue(typeCtorElement.getValueString().contains("Outer.Element"),
                () -> "expected constructor Element to resolve through Outer; got "
                        + typeCtorElement.getValueString());
    }

    private static FileStructure compileThroughNameResolution(String sourceText) {
        ErrorList errlist = new ErrorList(100);
        Parser    parser  = new Parser(new Source(sourceText), errlist);

        List<Statement> statements = parser.parseSource().getStatements();
        assertNoSeriousErrors(errlist);

        TypeCompositionStatement moduleStatement =
                (TypeCompositionStatement) statements.getLast();
        Compiler      compiler = new Compiler(moduleStatement, errlist);
        FileStructure file     = compiler.generateInitialFileStructure();
        assertNotNull(file);
        assertNoSeriousErrors(errlist);

        BuildRepository repo = new BuildRepository();
        repo.storeModule(file.getModule());

        ModuleConstant missing = compiler.linkModules(repo);
        assertNull(missing, () -> "missing module: " + missing);
        assertNoSeriousErrors(errlist);

        boolean resolved = false;
        for (int i = 0; i < 0x3F && !resolved; ++i) {
            resolved = compiler.resolveNames(i == 0x3E);
        }
        assertTrue(resolved, () -> "name resolution did not complete: " + errlist.getErrors());
        assertNoSeriousErrors(errlist);

        return file;
    }

    private static MethodStructure getOnlyMethod(ClassStructure clz, String name) {
        MultiMethodStructure methods = clz.getChild(name, MultiMethodStructure.class);
        assertNotNull(methods, () -> "missing method group: " + name);
        assertEquals(1, methods.methods().size(), () -> "method group: " + name);
        return methods.methods().iterator().next();
    }

    private static TypeConstant getOnlyTypeParameter(TypeConstant type, String source) {
        TypeConstant[] params = type.getParamTypesArray();
        assertEquals(1, params.length, () -> source + " type: " + type.getValueString());
        return params[0];
    }

    private static void assertNoSeriousErrors(ErrorList errlist) {
        assertEquals(0, errlist.getSeriousErrorCount(), () -> errlist.getErrors().toString());
    }
}
