package org.xvm.compiler;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.xvm.asm.ErrorList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Source-shape tests for constructor escape patterns reported by {@code javac -Xlint:this-escape}.
 */
public class CompilerThisEscapeConstructionTest {
    /**
     * Lexer construction must not dispatch to subclass whitespace hooks. That virtual call could
     * observe subclass state before construction completes, even in a single-threaded parser setup.
     */
    @Test
    public void lexerConstructorDoesNotDispatchToSubclassWhitespace() {
        var lexer = new HookDetectingLexer(new Source("  module Test {}"));

        assertEquals(0, lexer.whitespaceCalls);
        assertFalse(lexer.eatWhitespace());
        assertEquals(1, lexer.whitespaceCalls);
    }

    /**
     * Parser construction must not call subclass token advancement. The old constructor-time
     * dispatch could publish or inspect partial parser state before subclass fields existed.
     */
    @Test
    public void parserConstructorDoesNotDispatchToSubclassNext() {
        var parser = new HookDetectingParser(new Source("module Test {}"));

        assertEquals(0, parser.nextCalls);
        Token token = parser.peek();
        assertEquals("module", token.getValueText());
        assertEquals(1, parser.nextCalls);
    }

    /**
     * The private lexer priming path must preserve whitespace behavior after removing overridable
     * constructor dispatch.
     */
    @Test
    public void lexerConstructorUsesPrivateWhitespacePriming() throws IOException {
        String source = source("org/xvm/compiler/Lexer.java");
        String ctor   = between(source, "public Lexer(Source source, ErrorListener errorListener)",
                "protected Lexer(Lexer parent)");

        assertTrue(ctor.contains("eatInitialWhitespace(source, errorListener)"));
        assertFalse(ctor.contains("eatWhitespace();"));
    }

    /**
     * Parser construction now avoids eager token priming through overridable paths. This verifies
     * token state is still initialized lazily and equivalently.
     */
    @Test
    public void parserConstructorDoesNotPrimeTokenStream() throws IOException {
        String source = source("org/xvm/compiler/Parser.java");
        // Anchor on the declaration, not its full signature: this test is about what the
        // constructor BODY does, and spelling out every parameter made it fail when the parameters
        // gained annotations. There is exactly one private Parser constructor.
        String ctor   = between(source, "private Parser(", "// ----- parsing");

        assertFalse(ctor.contains("next();"));
        assertTrue(source.contains("private void ensurePrimed()"));
        assertTrue(source.contains("ensurePrimed();"));
    }

    /**
     * Synthetic AST expressions must attach metadata through factories, not partially constructed
     * objects. This protects future incremental compiler reentry.
     */
    @Test
    public void syntheticExpressionsAttachThroughFactories() throws IOException {
        String source = source("org/xvm/compiler/ast/SyntheticExpression.java");
        String ctor   = between(source, "public SyntheticExpression(Expression expr)",
                "protected final void adoptSyntheticExpression()");

        assertFalse(ctor.contains("adopt("));
        assertTrue(source.contains("protected final void adoptSyntheticExpression()"));

        assertFactory("ConvertExpression");
        assertFactory("PackExpression");
        assertFactory("ToIntExpression");
        assertFactory("TraceExpression");
        assertFactory("UnpackExpression");
    }

    /**
     * Compiler AST constructor metadata must use post-construction factories. The old shape mixed
     * construction with owner-visible metadata attachment.
     */
    @Test
    public void compilerAstConstructorMetadataUsesFactories() throws IOException {
        String component = source("org/xvm/compiler/ast/ComponentStatement.java");
        assertTrue(component.contains(
                "protected ComponentStatement(long lStartPos, long lEndPos, Component component)"));

        String method = source("org/xvm/compiler/ast/MethodDeclarationStatement.java");
        assertTrue(method.contains("public static MethodDeclarationStatement forInitializer("));
        assertTrue(method.contains("super(body.getStartPosition(), body.getEndPosition(), struct);"));

        String named = source("org/xvm/compiler/ast/NamedTypeExpression.java");
        assertTrue(named.contains("public static NamedTypeExpression forValidatedType("));

        String type = source("org/xvm/compiler/ast/TypeCompositionStatement.java");
        assertTrue(type.contains("public static TypeCompositionStatement forAnonymousInnerClass("));
        assertTrue(type.contains("public static TypeCompositionStatement forModule("));
    }

    private static void assertFactory(String className) throws IOException {
        String source = source("org/xvm/compiler/ast/" + className + ".java");

        assertTrue(source.contains("private " + className + "("), className);
        assertTrue(source.contains("public static " + className + " create("), className);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(sourceRoot().resolve(relative));
    }

    private static Path sourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path root : List.of(cwd.resolve("src/main/java"),
                cwd.resolve("javatools/src/main/java"))) {
            if (Files.isDirectory(root)) {
                return root;
            }
        }
        throw new IllegalStateException("Cannot locate javatools source root from " + cwd);
    }

    private static String between(String source, String start, String end) {
        int ofStart = source.indexOf(start);
        int ofEnd   = source.indexOf(end, ofStart);

        assertTrue(ofStart >= 0, start);
        assertTrue(ofEnd > ofStart, end);
        return source.substring(ofStart, ofEnd);
    }

    private static final class HookDetectingLexer extends Lexer {
        private boolean constructed = true;
        int             whitespaceCalls;

        HookDetectingLexer(Source source) {
            super(source, new ErrorList(5));
        }

        @Override
        protected boolean eatWhitespace() {
            if (!constructed) {
                throw new IllegalStateException(
                        "lexer constructor called overridable whitespace parser");
            }

            ++whitespaceCalls;
            return super.eatWhitespace();
        }
    }

    private static final class HookDetectingParser extends Parser {
        private boolean constructed = true;
        int             nextCalls;

        HookDetectingParser(Source source) {
            super(source, new ErrorList(5));
        }

        @Override
        protected Token next() {
            if (!constructed) {
                throw new IllegalStateException(
                        "parser constructor called overridable token priming");
            }

            ++nextCalls;
            return super.next();
        }
    }
}
