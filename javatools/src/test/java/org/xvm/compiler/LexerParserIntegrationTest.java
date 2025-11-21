package org.xvm.compiler;

import org.junit.jupiter.api.Test;
import org.xvm.asm.ErrorList;
import org.xvm.compiler.ast.StatementBlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests demonstrating Lexer and Parser APIs for potential language service usage.
 * <p>
 * These tests show what IS currently possible with the Lexer/Parser for standalone use:
 * <ul>
 *   <li>Parsing valid source into AST</li>
 *   <li>Handling syntax errors</li>
 *   <li>Collecting errors via ErrorList</li>
 *   <li>Navigating the AST to find declarations</li>
 * </ul>
 */
class LexerParserIntegrationTest {

    @Test
    void testValidModuleParsing() {
        // Given: Valid Ecstasy module source
        String source = """
            module Test {
                Int add(Int a, Int b) {
                    return a + b;
                }
            }
            """;

        // When: Parsing the source
        ParseResult result = parse(source);

        // Then: Print results to stdout
        System.out.println("=== testValidModuleParsing ===");
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Has errors: " + result.hasErrors());
        System.out.println("Error count: " + result.getErrorCount());
        if (!result.getErrors().isEmpty()) {
            System.out.println("Errors from ErrorList:");
            for (var error : result.getErrors()) {
                System.out.println("  " + error);
            }
        }
        System.out.println();

        // Assertions
        assertTrue(result.isSuccess());
        assertNotNull(result.root());
        assertFalse(result.hasErrors());
    }

    @Test
    void testSyntaxErrorHandling() {
        // Given: Invalid Ecstasy source with syntax error
        String source = """
            module Test {
                Int add(Int a, Int b {
                    return a + b;
                }
            }
            """;

        // When: Parsing the source (will throw CompilerException)
        ParseResult result = parse(source);

        // Then: Print results to stdout
        System.out.println("=== testSyntaxErrorHandling ===");
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Has errors: " + result.hasErrors());
        System.out.println("Error count: " + result.getErrorCount());
        System.out.println("Exception: " + (result.exception() != null ? result.exception().getMessage() : "none"));
        if (!result.getErrors().isEmpty()) {
            System.out.println("Errors from ErrorList:");
            for (var error : result.getErrors()) {
                System.out.println("  " + error);
            }
        }
        System.out.println();

        // Assertions - parser throws on syntax errors, no error recovery
        assertFalse(result.isSuccess());
        assertNull(result.root());
        assertTrue(result.hasErrors() || result.exception() != null);
    }

    @Test
    void testMissingModuleStatement() {
        // Given: Source without module statement
        String source = """
            Int add(Int a, Int b) {
                return a + b;
            }
            """;

        // When: Parsing the source
        ParseResult result = parse(source);

        // Then: Print results to stdout
        System.out.println("=== testMissingModuleStatement ===");
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Has errors: " + result.hasErrors());
        System.out.println("Error count: " + result.getErrorCount());
        System.out.println("Exception: " + (result.exception() != null ? result.exception().getMessage() : "none"));
        if (!result.getErrors().isEmpty()) {
            System.out.println("Errors from ErrorList:");
            result.getErrors().forEach(System.out::println);
        }
        System.out.println();

        // Assertions
        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors() || result.exception() != null);
    }

    @Test
    void testAstNavigation() {
        // Given: Simple module with a method
        String source = """
            module Test {
                Int add(Int a, Int b) {
                    return a + b;
                }
            }
            """;

        // When: Parsing the source
        ParseResult result = parse(source);
        StatementBlock root = result.root();

        // Then: Print results to stdout
        System.out.println("=== testAstNavigation ===");
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Root AST node: " + (root != null ? root.getClass().getSimpleName() : "null"));
        System.out.println("Statement count: " + (root != null ? root.getStatements().size() : 0));
        System.out.println("Error count: " + result.getErrorCount());
        if (!result.getErrors().isEmpty()) {
            System.out.println("Errors from ErrorList:");
            result.getErrors().forEach(System.out::println);
        }
        System.out.println();

        // Assertions
        assertNotNull(root);
        assertFalse(root.getStatements().isEmpty());
    }

    @Test
    void testComplexSyntaxError() {
        // Given: Source with multiple syntax issues
        String source = """
            module Test {
                String name = ;
                Int count
                void doSomething( {}
            }
            """;

        // When: Parsing the source
        ParseResult result = parse(source);

        // Then: Print results to stdout
        System.out.println("=== testComplexSyntaxError ===");
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Has errors: " + result.hasErrors());
        System.out.println("Error count: " + result.getErrorCount());
        System.out.println("Exception: " + (result.exception() != null ? result.exception().getMessage() : "none"));
        if (!result.getErrors().isEmpty()) {
            System.out.println("Errors from ErrorList:");
            result.getErrors().forEach(System.out::println);
        }
        System.out.println();

        // Assertions - parser throws on first error, no error recovery
        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors() || result.exception() != null);
    }

    // ===== Helper Methods =====

    /**
     * Parse source code into an AST using the XVM Parser.
     * This creates a Parser instance with an ErrorList to collect any errors.
     */
    private static ParseResult parse(final String source) {
        Source src = new Source(source);
        ErrorList errors = new ErrorList(100);

        try {
            Parser parser = new Parser(src, errors);
            StatementBlock ast = parser.parseSource();
            return new ParseResult(ast, errors);
        } catch (final CompilerException e) {
            // Parser throws on syntax errors - no error recovery
            return new ParseResult(null, errors, e);
        }
    }

    // ===== Result Container =====

    /**
     * Container for parse results.
     * In a real language service, this would provide more structured access to errors.
     */
    private record ParseResult(StatementBlock root, ErrorList errors, CompilerException exception) {
        ParseResult(final StatementBlock root, final ErrorList errors) {
            this(root, errors, null);
        }

        boolean isSuccess() {
            return exception == null && (errors == null || !errors.hasSeriousErrors());
        }

        boolean hasErrors() {
            return (errors != null && errors.hasErrors()) || exception != null;
        }

        int getErrorCount() {
            return errors != null ? errors.getSeriousErrorCount() : 0;
        }

        java.util.List<org.xvm.asm.ErrorListener.ErrorInfo> getErrors() {
            return errors != null ? errors.getErrors() : java.util.List.of();
        }
    }
}
