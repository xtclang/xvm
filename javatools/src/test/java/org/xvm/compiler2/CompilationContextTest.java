package org.xvm.compiler2;

import org.junit.jupiter.api.Test;

import org.xvm.compiler2.parser.Diagnostic;
import org.xvm.compiler2.parser.DiagnosticSeverity;
import org.xvm.compiler2.parser.GreenLexer;
import org.xvm.compiler2.parser.GreenParser;
import org.xvm.compiler2.parser.SourceText;
import org.xvm.compiler2.parser.TextLocation;
import org.xvm.compiler2.syntax.green.GreenExpression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CompilationContext - unified context for lexing, parsing, and compilation.
 */
class CompilationContextTest {

    @Test
    void sourceText_lineAndColumnTracking() {
        String code = "first line\nsecond line\nthird line";
        SourceText source = new SourceText(code, "test.x");

        assertEquals(3, source.getLineCount());
        assertEquals("test.x", source.getFileName());

        // First line
        TextLocation loc0 = source.getLocation(0);
        assertEquals(0, loc0.line());
        assertEquals(0, loc0.column());
        assertEquals(1, loc0.getDisplayLine());
        assertEquals(1, loc0.getDisplayColumn());

        // Second line, third column
        TextLocation loc15 = source.getLocation(15);
        assertEquals(1, loc15.line()); // second line
        assertEquals(4, loc15.column()); // "nd l" - 4 chars in
    }

    @Test
    void sourceText_getLineText() {
        String code = "line 1\nline 2\nline 3";
        SourceText source = new SourceText(code, "test.x");

        assertEquals("line 1", source.getLineText(0));
        assertEquals("line 2", source.getLineText(1));
        assertEquals("line 3", source.getLineText(2));
    }

    @Test
    void compilationContext_errorReporting() {
        SourceText source = new SourceText("let x = 42;", "test.x");
        CompilationContext ctx = new CompilationContext(source);

        assertFalse(ctx.hasErrors());
        assertEquals(0, ctx.getErrorCount());

        ctx.reportError("XC0001", "Test error", 4, 1);

        assertTrue(ctx.hasErrors());
        assertEquals(1, ctx.getErrorCount());

        Diagnostic diag = ctx.getErrors().get(0);
        assertEquals(DiagnosticSeverity.ERROR, diag.severity());
        assertEquals("XC0001", diag.code());
        assertEquals("Test error", diag.message());
    }

    @Test
    void compilationContext_warningReporting() {
        SourceText source = new SourceText("let x = 42;", "test.x");
        CompilationContext ctx = new CompilationContext(source);

        ctx.reportWarning("XW0001", "Test warning", 4, 1);

        assertFalse(ctx.hasErrors()); // warnings don't set hasErrors
        assertEquals(0, ctx.getErrorCount());
        assertEquals(1, ctx.getWarningCount());
    }

    @Test
    void compilationContext_logging() {
        SourceText source = new SourceText("test", "test.x");
        ConsoleLogger logger = new ConsoleLogger("test", ConsoleLogger.Level.DEBUG);
        CompilationContext ctx = new CompilationContext(source, logger);

        // Should not throw
        ctx.debug("Debug message");
        ctx.info("Info message");
        ctx.warn("Warning message");
        ctx.error("Error message");
    }

    @Test
    void compilationContext_withParser() {
        SourceText source = new SourceText("42 + 3", "test.x");
        CompilationContext ctx = new CompilationContext(source);
        GreenParser parser = new GreenParser(ctx);

        GreenExpression expr = parser.parseExpression();

        assertNotNull(expr);
        assertFalse(ctx.hasErrors());
    }

    @Test
    void compilationContext_parserErrorsReported() {
        SourceText source = new SourceText("42 +", "test.x");
        CompilationContext ctx = new CompilationContext(source);
        GreenParser parser = new GreenParser(ctx);

        try {
            parser.parseExpression();
        } catch (GreenParser.ParseException e) {
            // Expected
        }

        assertTrue(ctx.hasErrors());
        assertEquals(1, ctx.getErrorCount());
    }

    @Test
    void diagnostic_formatting() {
        SourceText source = new SourceText("let x = y;", "test.x");
        Diagnostic diag = Diagnostic.error("XC0001", "Undefined variable 'y'",
                source.getSpan(8, 1));

        String display = diag.toDisplayString();
        assertTrue(display.contains("test.x:1:9")); // 1-based line:column
        assertTrue(display.contains("error"));
        assertTrue(display.contains("XC0001"));
        assertTrue(display.contains("Undefined variable 'y'"));
    }

    @Test
    void lexer_withContext_reportsErrors() {
        SourceText source = new SourceText("\"unterminated", "test.x");
        CompilationContext ctx = new CompilationContext(source);
        GreenLexer lexer = new GreenLexer(ctx);

        // Consume the token (which will report an error for unterminated string)
        GreenLexer.TokenResult result = lexer.nextToken();

        // Error should be reported
        assertTrue(ctx.hasErrors());
    }
}
